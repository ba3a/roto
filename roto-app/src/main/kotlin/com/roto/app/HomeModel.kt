package com.roto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roto.core.model.ModelRepository
import com.roto.core.preset.PresetBundle
import com.roto.core.preset.PresetLibrary
import com.roto.core.preset.ProjectExporter
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

private const val PRESET_EXTENSION = ".rotopreset"

/**
 * One profile as the list shows it: its [id] and its [profile], or the [problem] that kept it from loading.
 *
 * @property id the profile id
 * @property profile the profile, or null when it could not be loaded
 * @property problem why it could not be loaded
 */
data class ProfileEntry(val id: String, val profile: Profile?, val problem: String?)

/**
 * What the home window shows: the saved [settings], the open [project], its [profiles] in the
 * library, the one [selected], and a [message] about the last thing that went wrong, if anything did.
 *
 * @property settings the saved settings
 * @property project the open project, if any
 * @property profiles the profiles in the library
 * @property selected the id of the selected profile
 * @property models the keys of the selected profile's models
 * @property library the library folder in use: the one chosen, or the default
 * @property profilesFolder the folder the profiles are read from, once a project is open
 * @property profilesFolderFound whether [profilesFolder] exists
 * @property message the last thing that went wrong
 */
data class HomeState(
    val settings: Settings = Settings(),
    val project: Project? = null,
    val profiles: List<ProfileEntry> = emptyList(),
    val selected: String? = null,
    val models: List<String> = emptyList(),
    val library: Path? = null,
    val profilesFolder: Path? = null,
    val profilesFolderFound: Boolean = false,
    val message: String? = null,
) {
    /** The entry of the [selected] profile, if it is still listed. */
    val selectedEntry: ProfileEntry? get() = profiles.firstOrNull { it.id == selected }
}

/** What fills the window: the home screen, or one clip being worked on. */
sealed interface Screen {
    /** The home screen. */
    data object Home : Screen

    /**
     * Shows the trim screen.
     *
     * @property model the state and actions behind it
     */
    class Trimming(val model: TrimModel) : Screen

    /**
     * Shows the still screen.
     *
     * @property model the state and actions behind it
     */
    class AddingStill(val model: StillModel) : Screen

    /**
     * Shows the model import screen.
     *
     * @property model the state and actions behind it
     */
    class ImportingModel(val model: ModelImportModel) : Screen

    /**
     * Shows the clip setup screen.
     *
     * @property model the state and actions behind it
     */
    class SettingUp(val model: ClipSetupModel) : Screen

    /**
     * Shows the draw screen.
     *
     * @property model the state and actions behind it
     */
    class Drawing(val model: DrawModel) : Screen

    /**
     * Shows the handoff panel.
     *
     * @property model the state and actions behind it
     */
    class HandingOff(val model: HandoffModel) : Screen

    /**
     * Shows the new-project wizard.
     *
     * @property model the state and actions behind it
     */
    class Wizard(val model: WizardModel) : Screen
}

/**
 * The home window's state and what can be done to it: open a project, choose the library folder and
 * the editor, create and select profiles. What is chosen is saved through [store], so the next start,
 * and the command line, find it again. Every program runs through [runner]; handoffs open in the
 * editor chosen, or in [systemEditor] until there is one. The new-project wizard starts from [presets].
 *
 * Every rule — what a project is, where its profiles live, what a profile name may be — is
 * `roto-core`'s; this only keeps the state and turns failures into messages.
 */
@Suppress("TooManyFunctions") // one short method per thing the artist can do from the home window
class HomeModel(
    private val store: SettingsStore,
    private val runner: ProcessRunner = SystemProcessRunner,
    private val presets: PresetLibrary = PresetLibrary.standard(store.configFolder),
    private val systemEditor: Editor = SystemEditor,
) : PickerMemory {
    private val ffmpeg = Ffmpeg(runner)

    /** What the home window shows. */
    var state by mutableStateOf(HomeState())
        private set

    /** What fills the window. */
    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    init {
        try {
            val settings = store.load()
            state = state.copy(settings = settings, library = store.library(settings))
            state.settings.projectFolder?.let { load(it) }
        } catch (e: IOException) {
            state = state.copy(message = "Settings could not be read, so Roto starts afresh: ${e.message}")
        }
    }

    /** Opens the project in [folder] and remembers it. */
    fun openProject(folder: Path) {
        if (load(folder)) save(state.settings.withProject(folder))
    }

    /** Opens the new-project wizard. */
    fun startWizard() {
        screen = Screen.Wizard(WizardModel(presets))
    }

    /**
     * Adds the preset bundle [file] to My presets and opens the wizard on it. A bad bundle gives a
     * message and changes nothing.
     */
    fun importBundle(file: Path) {
        try {
            val id = presets.add(file)
            val wizard = WizardModel(presets)
            wizard.presets.firstOrNull { it.id == id }?.let(wizard::choosePreset)
            screen = Screen.Wizard(wizard)
            state = state.copy(message = null)
        } catch (e: IOException) {
            state = state.copy(message = "The preset could not be imported: ${e.message}")
        }
    }

    /**
     * Writes the open project's setup to [file] as a preset bundle, named after the file. A failure,
     * including output paths that match no named layout, gives a message and writes nothing.
     */
    suspend fun exportPreset(file: Path) {
        val project = state.project ?: return
        val name = file.fileName.toString().removeSuffix(PRESET_EXTENSION)
        val report = { problem: String? -> state = state.copy(message = "The preset could not be exported: $problem") }
        val written = offUiThread(report) { PresetBundle.write(ProjectExporter.export(project, name, ""), file) }
        if (written != null) state = state.copy(message = null)
    }

    /** Opens the [project] the wizard made, remembers it, and goes back to the home screen. */
    fun finishWizard(project: Project) {
        screen = Screen.Home
        openProject(project.root)
    }

    override fun last(category: PickerCategory): Path? = state.settings.folder(category.name)

    override fun remember(category: PickerCategory, path: Path) = save(state.settings.withFolder(category.name, path))

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

    /** Opens the import screen for the selected profile's models. */
    fun importModel() {
        val repository = repository() ?: return
        val project = state.project ?: return
        val id = state.selectedEntry?.takeIf { it.profile != null }?.id ?: return
        screen = Screen.ImportingModel(ModelImportModel(project.config, repository.folder(id)))
    }

    /** Removes the model [key] from the selected profile; the app has asked the artist first. */
    fun removeModel(key: String) {
        val repository = repository() ?: return
        val id = state.selected ?: return
        ModelRepository(repository.folder(id)).remove(key)
        refresh()
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

    /** Selects profile [id]. */
    fun select(id: String) {
        state = state.copy(selected = id)
        refresh()
    }

    /** Clears the message. */
    fun dismissMessage() {
        state = state.copy(message = null)
    }

    /** Reads the profile list again from the library. */
    fun refresh() {
        val repository = repository()
        val folder = repository?.projectFolder
        state = state.copy(
            profilesFolder = folder,
            profilesFolderFound = folder?.let(Files::isDirectory) == true,
        )
        val profiles = try {
            repository?.ids()?.map { entry(repository, it) } ?: emptyList()
        } catch (e: IOException) {
            state = state.copy(message = "The library could not be read: ${e.message}")
            emptyList()
        }
        val selected = state.selected?.takeIf { id -> profiles.any { it.id == id } } ?: profiles.firstOrNull()?.id
        val models = try {
            val folder = if (repository == null || selected == null) null else repository.folder(selected)
            folder?.let { ModelRepository(it).keys() } ?: emptyList()
        } catch (e: IOException) {
            state = state.copy(message = "The models could not be read: ${e.message}")
            emptyList()
        }
        state = state.copy(profiles = profiles, selected = selected, models = models)
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
        state = state.copy(settings = settings, library = store.library(settings))
        try {
            store.save(settings)
        } catch (e: IOException) {
            state = state.copy(message = "Settings could not be saved: ${e.message}")
        }
    }

    private fun repository(): ProfileRepository? {
        val project = state.project ?: return null
        return ProfileRepository(store.library(state.settings), project.config.id)
    }
}

private fun entry(repository: ProfileRepository, id: String): ProfileEntry =
    try {
        ProfileEntry(id, repository.load(id), problem = null)
    } catch (e: IOException) {
        ProfileEntry(id, profile = null, problem = e.message)
    }
