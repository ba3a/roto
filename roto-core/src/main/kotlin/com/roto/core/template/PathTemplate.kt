package com.roto.core.template

import java.nio.file.Path

/**
 * A [Template] that expands to a [RelativePath] inside the consuming project, such as
 * `raw-art/{profile}/{action}_{facing:lower}/{frame:000}.png`.
 *
 * The literal parts are checked on construction. On expansion every substituted value must fit inside
 * one path segment — a value can never add a directory or climb out with `..` — and the expanded
 * path is checked again as a whole. Equality is by [source].
 */
class PathTemplate(val source: String) {

    val template = Template(source)

    /** Every variable a placeholder in this template refers to. */
    val variables: Set<TemplateVariable> get() = template.variables

    init {
        // A stand-in that is a valid segment on its own, so only the literal parts are being judged.
        RelativePath.validate(template.render { STAND_IN }, "Path template '$source'")
    }

    /** The relative path for [values]. */
    fun expand(values: TemplateValues): String {
        val path = template.render { placeholder ->
            placeholder.format(values).also { RelativePath.validateSegmentCharacters(it, "Value") }
        }
        RelativePath.validate(path, "Expanded path")
        return path
    }

    /** The file for [values] under [root]. */
    fun resolve(root: Path, values: TemplateValues): Path = root.resolve(expand(values))

    override fun equals(other: Any?): Boolean = other is PathTemplate && other.source == source

    override fun hashCode(): Int = source.hashCode()

    override fun toString(): String = source

    private companion object {
        const val STAND_IN = "x"
    }
}
