package com.roto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roto.core.process.ProcessRunner
import com.roto.core.process.SystemProcessRunner
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import com.roto.core.profile.ProfileRepository
import com.roto.core.project.Project
import com.roto.core.project.ProjectConfigCodec
import com.roto.core.settings.Settings
import com.roto.core.settings.SettingsStore
import com.roto.core.template.SafeName
import com.roto.core.video.Ffmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

/** One profile as the list shows it: its [id] and its [profile], or the [problem] that kept it from loading. */
data class ProfileEntry(val id: String, val profile: Profile?, val problem: String?)

/**
 * What the home window shows: the saved [settings], the open [project], its [profiles] in the
 * library, the one [selected], and a [message] about the last thing that went wrong, if anything did.
 */
data class HomeState(
    val settings: Settings = Settings(),
    val project: Project? = null,
    val profiles: List<ProfileEntry> = emptyList(),
    val selected: String? = null,
    val message: String? = null,
) {
    /** The entry of the [selected] profile, if it is still listed. */
    val selectedEntry: ProfileEntry? get() = profiles.firstOrNull { it.id == selected }
}

/** What fills the window: the home screen, or one clip being worked on. */
sealed interface Screen {
    data object Home : Screen

    class Trimming(val model: TrimModel) : Screen

    class AddingStill(val model: StillModel) : Screen

    class SettingUp(val model: ClipSetupModel) : Screen

    class Drawing(val model: DrawModel) : Screen

    class HandingOff(val model: HandoffModel) : Screen
}

/**
 * The home window's state and what can be done to it: open a project, choose the library folder and
 * the editor, create and select profiles. What is chosen is saved through [store], so the next start,
 * and the command line, find it again. Every program runs through [runner]; handoffs open in the
 * editor chosen, or in [systemEditor] until there is one.
 *
 * Every rule — what a project is, where its profiles live, what a profile name may be — is
 * `roto-core`'s; this only keeps the state and turns failures into messages.
 */
class HomeModel(
    private val store: SettingsStore,
    private val runner: ProcessRunner = SystemProcessRunner,
    private val systemEditor: Editor = SystemEditor,
) {
    private val ffmpeg = Ffmpeg(runner)

    var state by mutableStateOf(HomeState())
        private set

    /** What fills the window. */
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    init {
        try {
            state = state.copy(settings = store.load())
            state.settings.projectFolder?.let { load(it) }
        } catch (e: IOException) {
            state = state.copy(message = "Settings could not be read, so Roto starts afresh: ${e.message}")
        }
    }

    /** Opens the project in [folder] and remembers it. */
    fun openProject(folder: Path) {
        if (load(folder)) save(state.settings.withProject(folder))
    }

    /** Makes [folder] the library and remembers it. */
    fun chooseLibrary(folder: Path) {
        save(state.settings.withLibrary(folder))
        refresh()
    }

    /** Makes [program] the editor handoffs open in, and remembers it. */
    fun chooseEditor(program: Path) = save(state.settings.withEditor(program))

    /** Creates a profile called [name] and selects it; false, with a message, when it cannot be made. */
    fun createProfile(name: String): Boolean {
        val repository = repository()
        val trimmed = name.trim()
        val problem = when {
            repository == null -> "Open a project and choose a library first"
            !SafeName.isValid(trimmed) -> "'$trimmed' cannot be a profile name: use letters, digits, '-' and '_'"
            else -> try {
                repository.create(trimmed)
                null
            } catch (e: FileAlreadyExistsException) {
                "A profile called ${Path.of(e.file).fileName} already exists"
            } catch (e: IOException) {
                "The profile could not be created: ${e.message}"
            }
        }
        if (problem != null) {
            state = state.copy(message = problem)
            return false
        }
        state = state.copy(selected = trimmed, message = null)
        refresh()
        return true
    }

    /** Opens [file], a video not yet in the selected profile, for trimming; nothing is copied until it is saved. */
    suspend fun importVideo(file: Path) = openTrim(file, editing = null)

    /** Opens [file], a photo not yet in the selected profile, to file as a still; nothing is copied until it is saved. */
    fun importPhoto(file: Path) {
        val repository = repository() ?: return
        val project = state.project ?: return
        val entry = state.selectedEntry ?: return
        val profile = entry.profile ?: return
        val target = ClipTarget(ffmpeg, repository, entry.id)
        screen = Screen.AddingStill(StillModel(file, target, project.config, profile))
    }

    /** Opens [clip] of the selected profile for trimming again. */
    suspend fun retrim(clip: Clip) {
        val repository = repository() ?: return
        val folder = repository.folder(state.selected ?: return)
        openTrim(folder.sourceFile(clip), editing = clip)
    }

    /** Opens [clip] of the selected profile for setup. */
    fun setUp(clip: Clip) {
        val repository = repository() ?: return
        val id = state.selected ?: return
        screen = Screen.SettingUp(ClipSetupModel(clip, ClipTarget(ffmpeg, repository, id)))
    }

    /** Opens [clip] of the selected profile for its scale, zones and draft. */
    fun draw(clip: Clip) {
        val repository = repository() ?: return
        val project = state.project ?: return
        val entry = state.selectedEntry ?: return
        val target = ClipTarget(ffmpeg, repository, entry.id)
        screen = Screen.Drawing(DrawModel(clip, entry.profile?.scale, target, project))
    }

    /** Opens the selected profile's handoff and publish panel. */
    fun handOff() {
        val repository = repository() ?: return
        val project = state.project ?: return
        val id = state.selected ?: return
        val editor = state.settings.editorProgram?.let { ChosenEditor(it, runner) } ?: systemEditor
        screen = Screen.HandingOff(HandoffModel(ClipTarget(ffmpeg, repository, id), project, editor, runner))
    }

    /** Goes back to the home screen, and reads the profiles again. */
    fun closeClip() {
        screen = Screen.Home
        refresh()
    }

    fun select(id: String) {
        state = state.copy(selected = id)
    }

    fun dismissMessage() {
        state = state.copy(message = null)
    }

    /** Reads the profile list again from the library. */
    fun refresh() {
        val repository = repository()
        val profiles = try {
            repository?.ids()?.map { entry(repository, it) } ?: emptyList()
        } catch (e: IOException) {
            state = state.copy(message = "The library could not be read: ${e.message}")
            emptyList()
        }
        val selected = state.selected?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.firstOrNull()?.id
        state = state.copy(profiles = profiles, selected = selected)
    }

    private suspend fun openTrim(file: Path, editing: Clip?) {
        val repository = repository() ?: return
        val project = state.project ?: return
        val entry = state.selectedEntry ?: return
        val profile = entry.profile ?: return
        try {
            val info = withContext(Dispatchers.IO) { ffmpeg.probe(file) }
            val target = ClipTarget(ffmpeg, repository, entry.id)
            screen = Screen.Trimming(TrimModel(TrimVideo(file, info), target, project.config, profile, editing))
        } catch (e: IOException) {
            state = state.copy(message = "${file.fileName} could not be opened: ${e.message}")
        }
    }

    private fun load(folder: Path): Boolean {
        val project = try {
            if (!Files.isRegularFile(folder.resolve(ProjectConfigCodec.fileName))) {
                throw IOException("$folder holds no ${ProjectConfigCodec.fileName}")
            }
            Project.open(folder)
        } catch (e: IOException) {
            state = state.copy(message = "The project could not be opened: ${e.message}")
            return false
        }
        state = state.copy(project = project, selected = null, message = null)
        refresh()
        return true
    }

    private fun save(settings: Settings) {
        state = state.copy(settings = settings)
        try {
            store.save(settings)
        } catch (e: IOException) {
            state = state.copy(message = "Settings could not be saved: ${e.message}")
        }
    }

    private fun repository(): ProfileRepository? {
        val project = state.project ?: return null
        val library = state.settings.libraryFolder ?: return null
        return ProfileRepository(library, project.config.id)
    }

    private fun entry(repository: ProfileRepository, id: String): ProfileEntry =
        try {
            ProfileEntry(id, repository.load(id), problem = null)
        } catch (e: IOException) {
            ProfileEntry(id, profile = null, problem = e.message)
        }
}
