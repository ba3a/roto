package com.roto.core.preset

import com.roto.core.json.SourceStringSerializer
import com.roto.core.project.Canvas
import com.roto.core.project.MirrorPair
import com.roto.core.project.ProjectConfig
import kotlinx.serialization.Serializable

/** The id of the probe config [PresetInfo] validates with; a preset has no identity of its own. */
private const val PROBE_ID = "preset"

/**
 * What `preset.json` holds: a [ProjectPreset] without its palette and ramps, which a bundle keeps in
 * their own files. It has no `afterPublish`, so a shared file never makes Roto run a command, and
 * reading rejects any key it does not know. Validated on construction by building the [ProjectConfig]
 * it would give, with a probe id. Throws [IllegalArgumentException] naming the problem.
 *
 * @property name the preset's name
 * @property description a one-line summary
 * @property facings the facings the project draws
 * @property mirrorPairs the facings that may be mirrored into each other
 * @property actions the actions the project animates
 * @property canvas the canvas every published frame is drawn on
 * @property layout the named layout of published frames, written by its [OutputLayout.key]
 * @property outputFolder the folder inside the project the layout is built under
 */
@Serializable
data class PresetInfo(
    val name: String,
    val description: String,
    val facings: List<String>,
    val mirrorPairs: List<MirrorPair>,
    val actions: List<String>,
    val canvas: Canvas,
    val layout: @Serializable(with = OutputLayoutSerializer::class) OutputLayout,
    val outputFolder: String,
) {
    init {
        require(name.isNotBlank()) { "A preset needs a name" }
        toConfig(PROBE_ID)
    }

    /** The [ProjectConfig] for a project called [id]: this setup, no `afterPublish`. */
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

/** [OutputLayout] as its [OutputLayout.key]. */
internal object OutputLayoutSerializer : SourceStringSerializer<OutputLayout>(
    "OutputLayout",
    OutputLayout::fromKey,
    OutputLayout::key,
)
