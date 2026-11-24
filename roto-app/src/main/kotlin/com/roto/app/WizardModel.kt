package com.roto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roto.core.preset.LibraryPreset
import com.roto.core.preset.PresetLibrary
import com.roto.core.project.Canvas
import com.roto.core.project.MirrorPair
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

    /** Edit the facings, mirror pairs and actions. */
    Animation,

    /** Edit the canvas size and ground row. */
    Canvas,
}

/** A group of choices the artist can edit by hand; a preset switch rewrites only groups not edited. */
enum class WizardGroup {
    /** The project id. */
    Id,

    /** The facings and their mirror pairs. */
    Facings,

    /** The actions. */
    Actions,

    /** The canvas size and ground row. */
    Canvas,
}

/**
 * What the wizard shows: the current [step], the [preset] chosen, the project [id] and [folder], the
 * [edited] groups, the [facings], [mirrorPairs], [actions] and canvas size ([width], [height],
 * [groundRow]), the [existingProject] folder when [folder] already holds a project, and a [message].
 *
 * @property step the current step
 * @property preset the preset chosen, if any
 * @property id the project id
 * @property folder the project folder, if chosen
 * @property edited the groups the artist changed by hand
 * @property facings the facings, in order
 * @property mirrorPairs the facings that may be mirrored into each other
 * @property actions the actions, in order
 * @property width the canvas width in pixels
 * @property height the canvas height in pixels
 * @property groundRow the pixel row the feet stand on
 * @property existingProject the folder when it already holds a `roto.json`
 * @property message what to fix to go on, if anything
 */
data class WizardState(
    val step: WizardStep = WizardStep.Preset,
    val preset: LibraryPreset? = null,
    val id: String = "",
    val folder: Path? = null,
    val edited: Set<WizardGroup> = emptySet(),
    val facings: List<String> = emptyList(),
    val mirrorPairs: List<MirrorPair> = emptyList(),
    val actions: List<String> = emptyList(),
    val width: Int = 0,
    val height: Int = 0,
    val groundRow: Int = 0,
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

    /** Chooses [preset]; every group the artist has not edited takes the preset's values. */
    fun choosePreset(preset: LibraryPreset) {
        val p = preset.preset
        val s = state
        val facings = WizardGroup.Facings !in s.edited
        val actions = WizardGroup.Actions !in s.edited
        val canvas = WizardGroup.Canvas !in s.edited
        state = check(
            s.copy(
                preset = preset,
                facings = if (facings) p.facings else s.facings,
                mirrorPairs = if (facings) p.mirrorPairs else s.mirrorPairs,
                actions = if (actions) p.actions else s.actions,
                width = if (canvas) p.canvas.width else s.width,
                height = if (canvas) p.canvas.height else s.height,
                groundRow = if (canvas) p.canvas.groundRow else s.groundRow,
            ),
        )
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

    /** Adds the facing [name] (trimmed) at the end. */
    fun addFacing(name: String) = edit(WizardGroup.Facings) { it.copy(facings = it.facings + name.trim()) }

    /** Removes the facing [name] and the mirror pair that names it. */
    fun removeFacing(name: String) =
        edit(WizardGroup.Facings) { s ->
            s.copy(
                facings = s.facings - name,
                mirrorPairs = s.mirrorPairs.filter { name != it.first && name != it.second },
            )
        }

    /** Renames the facing [from] to [to] (trimmed), in its mirror pair too. */
    fun renameFacing(from: String, to: String) =
        edit(WizardGroup.Facings) { s ->
            val renamed = to.trim()
            fun rename(facing: String) = if (facing == from) renamed else facing
            s.copy(
                facings = s.facings.map(::rename),
                mirrorPairs = s.mirrorPairs.map { it.first.let(::rename) to it.second.let(::rename) }
                    .filter { it.first != it.second }
                    .map { MirrorPair(it.first, it.second) },
            )
        }

    /** Makes [first] and [second] a mirror pair; says why when they are the same or either is already paired. */
    fun pairFacings(first: String, second: String) {
        val paired = state.mirrorPairs.flatMap { listOf(it.first, it.second) }
        val refusal = when {
            first == second -> "Choose two different facings to pair"
            first !in state.facings || second !in state.facings -> "Both facings must be in the list"
            first in paired || second in paired -> "A facing can be in only one mirror pair"
            else -> null
        }
        if (refusal != null) {
            state = state.copy(message = refusal)
        } else {
            edit(WizardGroup.Facings) { it.copy(mirrorPairs = it.mirrorPairs + MirrorPair(first, second)) }
        }
    }

    /** Removes the mirror pair of [first] and [second]. */
    fun unpairFacings(first: String, second: String) =
        edit(WizardGroup.Facings) { it.copy(mirrorPairs = it.mirrorPairs - MirrorPair(first, second)) }

    /** Adds the action [name] (trimmed) at the end. */
    fun addAction(name: String) = edit(WizardGroup.Actions) { it.copy(actions = it.actions + name.trim()) }

    /** Removes the action [name]. */
    fun removeAction(name: String) = edit(WizardGroup.Actions) { it.copy(actions = it.actions - name) }

    /** Renames the action [from] to [to] (trimmed). */
    fun renameAction(from: String, to: String) =
        edit(WizardGroup.Actions) { s -> s.copy(actions = s.actions.map { if (it == from) to.trim() else it }) }

    /** Sets the canvas width to [width]. */
    fun editWidth(width: Int) = edit(WizardGroup.Canvas) { it.copy(width = width) }

    /** Sets the canvas height to [height]. */
    fun editHeight(height: Int) = edit(WizardGroup.Canvas) { it.copy(height = height) }

    /** Sets the ground row to [groundRow]. */
    fun editGroundRow(groundRow: Int) = edit(WizardGroup.Canvas) { it.copy(groundRow = groundRow) }

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

    private fun edit(group: WizardGroup, change: (WizardState) -> WizardState) {
        val changed = change(state)
        state = check(changed.copy(edited = changed.edited + group))
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
            WizardStep.Animation -> namesProblem("facing", s.facings) ?: namesProblem("action", s.actions)
            WizardStep.Canvas -> canvasProblem(s)
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

    private fun namesProblem(what: String, names: List<String>): String? =
        when {
            names.isEmpty() -> "Add at least one $what"
            names.any { !SafeName.isValid(it) } ->
                "Each $what name must start with a letter or digit and use only letters, digits, - and _"
            names.groupBy { it.lowercase() }.values.any { it.size > 1 } ->
                "Each $what name must be different, capitals aside"
            else -> null
        }

    private fun canvasProblem(s: WizardState): String? =
        try {
            Canvas(s.width, s.height, s.groundRow)
            null
        } catch (e: IllegalArgumentException) {
            e.message
        }

    private fun holdsProject(folder: Path): Boolean = Files.exists(folder.resolve(ProjectConfigCodec.fileName))
}

private fun safeName(raw: String): String =
    raw.trim().replace(Regex("[^A-Za-z0-9_-]+"), "-").trimStart('-', '_').trimEnd('-')
