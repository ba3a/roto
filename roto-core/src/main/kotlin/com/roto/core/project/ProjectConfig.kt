package com.roto.core.project

import com.roto.core.ase.AseLimits
import com.roto.core.template.PathTemplate
import com.roto.core.template.RelativePath
import com.roto.core.template.SafeName
import com.roto.core.template.Template
import com.roto.core.template.TemplateVariable
import kotlinx.serialization.Serializable

/**
 * A consuming project's `roto.json`: everything Roto is told about the project it publishes into.
 * Roto has no built-in notion of facings, actions, canvas size or folder layout; they all come from
 * here. Validated on construction, so a loaded config is always usable.
 *
 * [id] names the project in the library folder (`<library>/<id>/<profile>/`), so it stays the same
 * across clones and renames of the consuming repository. [palette] and [ramps] are [RelativePath]s
 * from the project root. [output] names each published frame and must use every [TemplateVariable],
 * so no two frames of any clip land on the same file; [manifest] names each clip's manifest and uses
 * all of them but `{frame}`. [afterPublish] is a command run in the project root after a publish; its
 * arguments may use `{profile}`.
 *
 * @property id the project's name in the library folder
 * @property palette the palette file, relative to the project root
 * @property ramps the ramps file, relative to the project root
 * @property facings the facings the project draws, such as the directions a figure can face
 * @property mirrorPairs the facings that may be mirrored into each other
 * @property actions the actions the project animates
 * @property canvas the canvas every published frame is drawn on
 * @property output where each published frame goes
 * @property manifest where each clip's manifest goes
 * @property afterPublish the command run in the project root after a publish, one template per argument
 */
@Serializable
data class ProjectConfig(
    val id: String,
    val palette: String,
    val ramps: String,
    val facings: List<String>,
    val mirrorPairs: List<MirrorPair> = emptyList(),
    val actions: List<String>,
    val canvas: Canvas,
    val output: @Serializable(with = PathTemplateSerializer::class) PathTemplate,
    val manifest: @Serializable(with = PathTemplateSerializer::class) PathTemplate,
    val afterPublish: List<@Serializable(with = TemplateSerializer::class) Template> = emptyList(),
) {
    init {
        SafeName.validate(id, "id")
        RelativePath.validate(palette, "palette")
        RelativePath.validate(ramps, "ramps")
        SafeName.validateList(facings, "facings")
        SafeName.validateList(actions, "actions")
        validateMirrorPairs()
        validateTemplateVariables()
    }

    /** The facing [facing] may be mirrored into, or null when no mirror pair names it. */
    fun mirrorFacing(facing: String): String? = mirrorPairs.firstNotNullOfOrNull { pair ->
        when (facing) {
            pair.first -> pair.second
            pair.second -> pair.first
            else -> null
        }
    }

    private fun validateMirrorPairs() {
        val paired = mutableSetOf<String>()
        for (pair in mirrorPairs) {
            for (facing in listOf(pair.first, pair.second)) {
                require(facing in facings) { "Mirror pair $pair names '$facing', which is not in facings" }
                require(paired.add(facing)) { "Facing '$facing' is in more than one mirror pair" }
            }
        }
    }

    private fun validateTemplateVariables() {
        require(output.variables == TemplateVariable.entries.toSet()) {
            "output '$output' must use every one of ${placeholders(TemplateVariable.entries)}, " +
                "so that no two published frames share a file"
        }
        val clipVariables = TemplateVariable.entries - TemplateVariable.FRAME
        require(manifest.variables == clipVariables.toSet()) {
            "manifest '$manifest' must use ${placeholders(clipVariables)} and not {frame}, one manifest per clip"
        }
        afterPublish.forEach { argument ->
            require(argument.variables.all { it == TemplateVariable.PROFILE }) {
                "afterPublish argument '$argument' may only use {profile}"
            }
        }
    }
}

private fun placeholders(variables: List<TemplateVariable>) = variables.joinToString { "{${it.key}}" }

/**
 * The canvas every published frame is drawn on; [groundRow] is the pixel row the feet stand on.
 *
 * @property width the canvas width in pixels
 * @property height the canvas height in pixels
 * @property groundRow the pixel row the feet stand on
 */
@Serializable
data class Canvas(val width: Int, val height: Int, val groundRow: Int) {
    init {
        require(width in 1..AseLimits.MAX_DIMENSION && height in 1..AseLimits.MAX_DIMENSION) {
            "canvas must be 1..${AseLimits.MAX_DIMENSION} on each side, was ${width}x$height"
        }
        require(groundRow in 0 until height) { "canvas groundRow $groundRow is outside 0..${height - 1}" }
    }
}

/**
 * Two facings that may be mirrored into each other. Only permission: nothing is mirrored unless a
 * clip opts in. Written in `roto.json` as a two-element array, `["EAST", "WEST"]`.
 *
 * @property first one of the two facings
 * @property second the other
 */
@Serializable(with = MirrorPairSerializer::class)
data class MirrorPair(val first: String, val second: String) {
    init {
        require(first != second) { "A facing cannot mirror into itself: $this" }
    }

    /** The pair as it is written in `roto.json`. */
    override fun toString(): String = "[$first, $second]"
}
