package com.roto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roto.core.palette.HexColor
import com.roto.core.palette.Palette
import com.roto.core.palette.PaletteCodec
import com.roto.core.palette.PaletteFormatException
import com.roto.core.palette.Ramp
import com.roto.core.palette.RampEditor
import com.roto.core.palette.RampSuggester
import com.roto.core.preset.LibraryPreset
import com.roto.core.preset.OutputLayout
import com.roto.core.preset.PresetLibrary
import com.roto.core.project.Canvas
import com.roto.core.project.MirrorPair
import com.roto.core.project.ProjectConfig
import com.roto.core.project.ProjectConfigCodec
import com.roto.core.template.PathTemplate
import com.roto.core.template.SafeName
import com.roto.core.template.Template
import java.io.IOException
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

    /** Choose the palette, ramps and outline colour. */
    Colours,

    /** Choose where published frames go. */
    Output,
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

    /** The palette, the ramps and the outline colour. */
    Colours,

    /** The output folder, layout, templates and `afterPublish` command. */
    Output,
}

/**
 * What the wizard shows: the current [step], the [preset] chosen, the project [id] and [folder], the
 * [edited] groups, the [facings], [mirrorPairs], [actions] and canvas size ([width], [height],
 * [groundRow]), the [palette], [ramps] and [outline], the output [outputFolder], [layout], raw
 * [output] and [manifest] templates and [afterPublish] command, the [existingProject] folder when [folder] already holds a project, and a [message].
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
 * @property palette the palette, once a preset or a palette file gave one
 * @property ramps the ramps, each dark to light
 * @property outline the outline colour, or null to outline each ramp in its darkest
 * @property outputFolder the folder inside the project published frames go under
 * @property layout the named layout of published frames
 * @property output the `output` template, as the layout built it or the artist edited it
 * @property manifest the `manifest` template, as the layout built it or the artist edited it
 * @property afterPublish the command run after a publish, one argument per word; empty for none
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
    val palette: Palette? = null,
    val ramps: List<Ramp> = emptyList(),
    val outline: Int? = null,
    val outputFolder: String = "",
    val layout: OutputLayout = OutputLayout.ONE_FOLDER_PER_CLIP,
    val output: String = "",
    val manifest: String = "",
    val afterPublish: String = "",
    val existingProject: Path? = null,
    val message: String? = null,
)

/**
 * The project wizard's state and what can be done to it. It keeps the choices and says, in plain
 * words, what stops the next step; every rule is `roto-core`'s.
 */
@Suppress("TooManyFunctions") // one short method per edit the artist can make
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
        val colours = WizardGroup.Colours !in s.edited
        val outputs = WizardGroup.Output !in s.edited
        val templates = p.layout.templates(p.outputFolder)
        state = check(
            s.copy(
                preset = preset,
                facings = if (facings) p.facings else s.facings,
                mirrorPairs = if (facings) p.mirrorPairs else s.mirrorPairs,
                actions = if (actions) p.actions else s.actions,
                width = if (canvas) p.canvas.width else s.width,
                height = if (canvas) p.canvas.height else s.height,
                groundRow = if (canvas) p.canvas.groundRow else s.groundRow,
                palette = if (colours) p.palette else s.palette,
                ramps = if (colours) p.ramps.ramps else s.ramps,
                outline = if (colours) p.ramps.outline else s.outline,
                outputFolder = if (outputs) p.outputFolder else s.outputFolder,
                layout = if (outputs) p.layout else s.layout,
                output = if (outputs) templates.output.source else s.output,
                manifest = if (outputs) templates.manifest.source else s.manifest,
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

    /**
     * Reads the palette in [file], a `.gpl` or `.hex`. A file that cannot be read keeps the previous
     * palette and shows the reason, with its line. Ramps and the outline that name a colour the new
     * palette lacks are dropped, and the message says so.
     */
    fun importPalette(file: Path) {
        val palette = try {
            PaletteCodec.read(file)
        } catch (e: PaletteFormatException) {
            state = state.copy(message = e.message)
            return
        } catch (e: IOException) {
            state = state.copy(message = "Cannot read $file: ${e.message}")
            return
        }
        val kept = state.ramps.filter { ramp -> ramp.colors.all { it in palette } }
        val dropped = state.ramps.size - kept.size
        val outline = state.outline?.takeIf { it in palette }
        val notice = listOfNotNull(
            "Dropped $dropped ramp(s) that use colours missing from the new palette".takeIf { dropped > 0 },
            "Dropped the outline colour, which is not in the new palette"
                .takeIf { state.outline != null && outline == null },
        ).joinToString(". ").ifEmpty { null }
        edit(WizardGroup.Colours, notice) { it.copy(palette = palette, ramps = kept, outline = outline) }
    }

    /** Adds the ramp [name] of the [picked] palette colours, sorted dark to light; says why when it cannot. */
    fun addRamp(name: String, picked: List<Int>) {
        val trimmed = name.trim()
        buildRamp(trimmed, picked, null)?.let { ramp ->
            edit(WizardGroup.Colours) { it.copy(ramps = it.ramps + ramp) }
        }
    }

    /** Replaces the ramp [from] with one called [name] of the [picked] colours; says why when it cannot. */
    fun editRamp(from: String, name: String, picked: List<Int>) {
        buildRamp(name.trim(), picked, from)?.let { ramp ->
            edit(WizardGroup.Colours) { s -> s.copy(ramps = s.ramps.map { if (it.name == from) ramp else it }) }
        }
    }

    /** Removes the ramp [name]. */
    fun removeRamp(name: String) =
        edit(WizardGroup.Colours) { s -> s.copy(ramps = s.ramps.filter { it.name != name }) }

    /** Replaces the ramps with [RampSuggester]'s grouping of the palette. */
    fun suggestRamps() {
        val palette = state.palette ?: return
        edit(WizardGroup.Colours) { it.copy(ramps = RampSuggester.suggest(palette)) }
    }

    /** Outlines in [colour], one of the palette, or in each ramp's darkest when [colour] is null. */
    fun chooseOutline(colour: Int?) {
        if (colour != null && state.palette?.contains(colour) != true) {
            state = state.copy(message = "${HexColor.format(colour)} is not in the palette")
        } else {
            edit(WizardGroup.Colours) { it.copy(outline = colour) }
        }
    }

    /** Publishes under [folder], inside the project; both templates are rebuilt from it and the layout. */
    fun editOutputFolder(folder: String) = editOutput { it.copy(outputFolder = folder.trim()) }

    /** Publishes in [layout]; both templates are rebuilt from the folder. */
    fun chooseLayout(layout: OutputLayout) = editOutput { it.copy(layout = layout) }

    /** Sets the raw `output` template to [template]; the message shows what `roto.json` would refuse. */
    fun editOutputTemplate(template: String) = edit(WizardGroup.Output) { it.copy(output = template.trim()) }

    /** Sets the raw `manifest` template to [template]; the message shows what `roto.json` would refuse. */
    fun editManifestTemplate(template: String) = edit(WizardGroup.Output) { it.copy(manifest = template.trim()) }

    /** Sets the command run after a publish to [command], one argument per word; empty for none. */
    fun editAfterPublish(command: String) = edit(WizardGroup.Output) { it.copy(afterPublish = command.trim()) }

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

    private fun edit(group: WizardGroup, notice: String? = null, change: (WizardState) -> WizardState) {
        val changed = change(state)
        state = check(changed.copy(edited = changed.edited + group), notice)
    }

    private fun editOutput(change: (WizardState) -> WizardState) =
        edit(WizardGroup.Output) { s ->
            val changed = change(s)
            val templates = runCatching { changed.layout.templates(changed.outputFolder) }.getOrNull()
            if (templates == null) {
                changed
            } else {
                changed.copy(output = templates.output.source, manifest = templates.manifest.source)
            }
        }

    private fun buildRamp(name: String, picked: List<Int>, replacing: String?): Ramp? {
        val palette = state.palette
        return try {
            requireNotNull(palette) { "Choose a palette first" }
            require(state.ramps.none { it.name == name && it.name != replacing }) { "Ramp '$name' already exists" }
            RampEditor.build(name, picked, palette)
        } catch (e: IllegalArgumentException) {
            state = state.copy(message = e.message)
            null
        }
    }

    private fun check(next: WizardState, notice: String? = null): WizardState =
        next.copy(
            existingProject = next.folder?.takeIf { next.step == WizardStep.Name && holdsProject(it) },
            message = notice ?: problem(next),
        )

    private fun problem(s: WizardState): String? =
        when (s.step) {
            WizardStep.Preset -> if (s.preset == null) "Choose a preset to start from" else null
            WizardStep.Name -> nameProblem(s)
            WizardStep.Animation -> namesProblem("facing", s.facings) ?: namesProblem("action", s.actions)
            WizardStep.Canvas -> canvasProblem(s)
            WizardStep.Colours -> coloursProblem(s)
            WizardStep.Output -> outputProblem(s)
        }

    private fun coloursProblem(s: WizardState): String? =
        when {
            s.palette == null -> "Import a palette"
            s.ramps.isEmpty() -> "Add at least one ramp, or suggest ramps"
            else -> null
        }

    private fun outputProblem(s: WizardState): String? =
        try {
            s.layout.templates(s.outputFolder)
            config(s)
            null
        } catch (e: IllegalArgumentException) {
            e.message
        }

    /** The config the state would give, which is what validates the raw templates and the command. */
    private fun config(s: WizardState): ProjectConfig =
        ProjectConfig(
            id = s.id,
            palette = PALETTE_PATH,
            ramps = RAMPS_PATH,
            facings = s.facings,
            mirrorPairs = s.mirrorPairs,
            actions = s.actions,
            canvas = Canvas(s.width, s.height, s.groundRow),
            output = PathTemplate(s.output),
            manifest = PathTemplate(s.manifest),
            afterPublish = s.afterPublish.split(WHITESPACE).filter { it.isNotEmpty() }.map(::Template),
        )

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

private const val PALETTE_PATH = "roto/palette.hex"
private const val RAMPS_PATH = "roto/ramps.json"
private val WHITESPACE = Regex("\\s+")
