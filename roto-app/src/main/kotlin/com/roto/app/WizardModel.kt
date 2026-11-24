package com.roto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roto.core.preset.LibraryPreset
import com.roto.core.preset.PresetLibrary
import com.roto.core.project.ProjectConfigCodec
import com.roto.core.template.SafeName
import java.nio.file.Files
import java.nio.file.Path

/** The steps of the project wizard, in order. */
enum class WizardStep {
    /** Choose the preset to start from. */
    Preset,

    /** Choose the project id and folder. */
    Name,
}

/** A group of choices the artist can edit by hand; a preset switch rewrites only groups not edited. */
enum class WizardGroup {
    /** The project id. */
    Id,
}

/**
 * What the wizard shows: the current [step], the [preset] chosen, the project [id] and [folder], the
 * [edited] groups, the [existingProject] folder when [folder] already holds a project, and a [message].
 *
 * @property step the current step
 * @property preset the preset chosen, if any
 * @property id the project id
 * @property folder the project folder, if chosen
 * @property edited the groups the artist changed by hand
 * @property existingProject the folder when it already holds a `roto.json`
 * @property message what to fix to go on, if anything
 */
data class WizardState(
    val step: WizardStep = WizardStep.Preset,
    val preset: LibraryPreset? = null,
    val id: String = "",
    val folder: Path? = null,
    val edited: Set<WizardGroup> = emptySet(),
    val existingProject: Path? = null,
    val message: String? = null,
)

/**
 * The project wizard's state and what can be done to it. It keeps the choices and says, in plain
 * words, what stops the next step; every rule is `roto-core`'s.
 */
class WizardModel(private val library: PresetLibrary) {

    /** What the wizard shows. */
    var state by mutableStateOf(WizardState())
        private set

    /** The presets that loaded, built-in first. */
    val presets: List<LibraryPreset> get() = library.list().filterIsInstance<LibraryPreset>()

    /** Chooses [preset]. */
    fun choosePreset(preset: LibraryPreset) {
        state = check(state.copy(preset = preset))
    }

    /** Chooses [folder]; the id follows its name until the artist edits the id. */
    fun chooseFolder(folder: Path) {
        val id = if (WizardGroup.Id in state.edited) state.id else safeName(folder.fileName?.toString().orEmpty())
        state = check(state.copy(folder = folder, id = id))
    }

    /** Sets the project id to [id], and keeps it from now on. */
    fun editId(id: String) {
        state = check(state.copy(id = id, edited = state.edited + WizardGroup.Id))
    }

    /** Whether the current step is valid, so the next may open. */
    val canNext: Boolean get() = problem(state) == null

    /** Goes to the next step when the current one is valid; otherwise says what to fix. */
    fun next() {
        val problem = problem(state)
        val following = WizardStep.entries.getOrNull(state.step.ordinal + 1)
        state = when {
            problem != null -> state.copy(message = problem)
            following != null -> check(state.copy(step = following))
            else -> state
        }
    }

    /** Goes to the step before, if there is one. */
    fun back() {
        val previous = WizardStep.entries.getOrNull(state.step.ordinal - 1) ?: return
        state = check(state.copy(step = previous))
    }

    private fun check(next: WizardState): WizardState =
        next.copy(
            existingProject = next.folder?.takeIf { next.step == WizardStep.Name && holdsProject(it) },
            message = problem(next),
        )

    private fun problem(s: WizardState): String? =
        when (s.step) {
            WizardStep.Preset -> if (s.preset == null) "Choose a preset to start from" else null
            WizardStep.Name -> nameProblem(s)
        }

    private fun nameProblem(s: WizardState): String? {
        val folder = s.folder
        return when {
            folder == null -> "Choose the project folder"
            holdsProject(folder) -> "This folder already holds a project. Open it instead, or choose another folder"
            Files.exists(folder) && !Files.isDirectory(folder) -> "$folder is a file, not a folder"
            Files.isDirectory(folder) && !Files.isWritable(folder) -> "Roto cannot write to $folder"
            !SafeName.isValid(s.id) ->
                "The project id must start with a letter or digit and use only letters, digits, - and _"
            else -> null
        }
    }

    private fun holdsProject(folder: Path): Boolean = Files.exists(folder.resolve(ProjectConfigCodec.fileName))
}

private fun safeName(raw: String): String =
    raw.trim().replace(Regex("[^A-Za-z0-9_-]+"), "-").trimStart('-', '_').trimEnd('-')
