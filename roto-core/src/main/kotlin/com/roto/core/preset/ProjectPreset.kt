package com.roto.core.preset

import com.roto.core.palette.HexColor
import com.roto.core.palette.Palette
import com.roto.core.palette.RampSet
import com.roto.core.project.Canvas
import com.roto.core.project.MirrorPair
import com.roto.core.project.ProjectConfig

/** The id of the probe config [ProjectPreset] validates with; a preset has no identity of its own. */
private const val PROBE_ID = "preset"

/** Where a scaffolded project keeps its palette, relative to the project root. */
internal const val PRESET_PALETTE_PATH = "roto/palette.hex"

/** Where a scaffolded project keeps its ramps, relative to the project root. */
internal const val PRESET_RAMPS_PATH = "roto/ramps.json"

/**
 * Everything in a project setup that is not the project's identity: facings, mirror pairs, actions,
 * canvas, output layout, palette and ramps. It holds no id, no project folder and no `afterPublish`,
 * so sharing one never makes Roto run a command. Validated on construction by building the
 * [ProjectConfig] it would give, and by checking that every ramp colour and the outline are in the
 * palette. Throws [IllegalArgumentException] naming the problem.
 *
 * @property name the preset's name
 * @property description a one-line summary
 * @property facings the facings the project draws
 * @property mirrorPairs the facings that may be mirrored into each other
 * @property actions the actions the project animates
 * @property canvas the canvas every published frame is drawn on
 * @property layout the named layout of published frames
 * @property outputFolder the folder inside the project the layout is built under
 * @property palette the project's palette
 * @property ramps the project's ramps, every colour one of [palette]'s
 */
data class ProjectPreset(
    val name: String,
    val description: String,
    val facings: List<String>,
    val mirrorPairs: List<MirrorPair>,
    val actions: List<String>,
    val canvas: Canvas,
    val layout: OutputLayout,
    val outputFolder: String,
    val palette: Palette,
    val ramps: RampSet,
) {
    init {
        require(name.isNotBlank()) { "A preset needs a name" }
        toConfig(PROBE_ID)
        ramps.ramps.forEach { ramp ->
            ramp.colors.firstOrNull { it !in palette }?.let { color ->
                throw IllegalArgumentException(
                    "Ramp '${ramp.name}' uses ${HexColor.format(color)}, which is not in the palette",
                )
            }
        }
        ramps.outline?.let { outline ->
            require(outline in palette) { "Outline colour ${HexColor.format(outline)} is not in the palette" }
        }
    }

    /** The [ProjectConfig] for a project called [id]: this preset's setup, no `afterPublish`. */
    fun toConfig(id: String): ProjectConfig {
        val templates = layout.templates(outputFolder)
        return ProjectConfig(
            id = id,
            palette = PRESET_PALETTE_PATH,
            ramps = PRESET_RAMPS_PATH,
            facings = facings,
            mirrorPairs = mirrorPairs,
            actions = actions,
            canvas = canvas,
            output = templates.output,
            manifest = templates.manifest,
        )
    }
}
