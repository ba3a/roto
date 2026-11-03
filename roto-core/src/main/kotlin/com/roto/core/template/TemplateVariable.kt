package com.roto.core.template

/**
 * A placeholder a [Template] may use, written `{key}` or `{key:format}`.
 *
 * Text variables accept the `lower` and `upper` formats; [FRAME] is numeric and accepts a run of
 * zeros (`000`) giving the minimum digit count.
 *
 * @property key the name used inside the braces
 * @property numeric whether the variable is a number, which takes zero padding rather than a case format
 */
enum class TemplateVariable(val key: String, val numeric: Boolean) {
    PROFILE("profile", numeric = false),
    ACTION("action", numeric = false),
    FACING("facing", numeric = false),
    FRAME("frame", numeric = true),
    ;

    /** Lookup by key. */
    companion object {
        /** The variable written `{key}`, or null when there is none. */
        fun byKey(key: String): TemplateVariable? = entries.firstOrNull { it.key == key }
    }
}

/**
 * The values a [Template] is expanded with; a variable the template uses must not be null.
 *
 * @property profile the profile id
 * @property action the action name
 * @property facing the facing name
 * @property frame the zero-based frame number
 */
data class TemplateValues(
    val profile: String? = null,
    val action: String? = null,
    val facing: String? = null,
    val frame: Int? = null,
) {
    init {
        require(frame == null || frame >= 0) { "Frame number must not be negative, was $frame" }
    }

    internal fun valueOf(variable: TemplateVariable): Any? = when (variable) {
        TemplateVariable.PROFILE -> profile
        TemplateVariable.ACTION -> action
        TemplateVariable.FACING -> facing
        TemplateVariable.FRAME -> frame
    }
}
