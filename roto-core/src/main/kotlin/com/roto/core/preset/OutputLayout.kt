package com.roto.core.preset

import com.roto.core.template.PathTemplate
import com.roto.core.template.RelativePath

/**
 * The `output` and `manifest` templates a [OutputLayout] gives for one folder.
 *
 * @property output where each published frame goes
 * @property manifest where each clip's manifest goes
 */
data class OutputTemplates(val output: PathTemplate, val manifest: PathTemplate)

/**
 * A layout [OutputLayout.recognise] found in a pair of templates.
 *
 * @property layout the named layout
 * @property folder the folder inside the project the layout was built with
 */
data class RecognisedLayout(val layout: OutputLayout, val folder: String)

/**
 * The named ways published frames are laid out under a folder of the consuming project. The artist
 * picks a folder and a layout and never sees a template. Every layout uses the variables
 * `ProjectConfig` requires: all four in `output`, all but `{frame}` in `manifest`.
 *
 * @property outputSuffix the `output` template after the folder
 * @property manifestSuffix the `manifest` template after the folder
 */
enum class OutputLayout(private val outputSuffix: String, private val manifestSuffix: String) {
    /** `<folder>/{profile}/{action}_{facing}/{frame}.png` with `clip.json` beside the frames. */
    ONE_FOLDER_PER_CLIP("/{profile}/{action}_{facing}/{frame}.png", "/{profile}/{action}_{facing}/clip.json"),

    /** `<folder>/{profile}/{action}_{facing}_{frame}.png` with `{action}_{facing}.json` beside them. */
    ONE_FOLDER_PER_PROFILE("/{profile}/{action}_{facing}_{frame}.png", "/{profile}/{action}_{facing}.json"),
    ;

    /**
     * The templates for [folder]. Throws [IllegalArgumentException] unless [folder] is a valid relative
     * path (no `..`, no backslash, no leading or trailing `/`).
     */
    fun templates(folder: String): OutputTemplates {
        RelativePath.validate(folder, "Output folder")
        return OutputTemplates(PathTemplate(folder + outputSuffix), PathTemplate(folder + manifestSuffix))
    }

    /** Layouts found in templates. */
    companion object {
        /**
         * The layout and folder that give exactly [output] and [manifest], or null when the pair is
         * custom or mixes two layouts.
         */
        fun recognise(output: PathTemplate, manifest: PathTemplate): RecognisedLayout? =
            entries.firstNotNullOfOrNull { layout ->
                val folder = output.source.removeSuffix(layout.outputSuffix)
                if (folder == output.source) {
                    null
                } else {
                    runCatching { layout.templates(folder) }.getOrNull()
                        ?.takeIf { it.output == output && it.manifest == manifest }
                        ?.let { RecognisedLayout(layout, folder) }
                }
            }
    }
}
