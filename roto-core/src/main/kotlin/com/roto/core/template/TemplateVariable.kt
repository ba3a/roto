package com.roto.core.template

/**
 * A placeholder a [Template] may use, written `{key}` or `{key:format}`.
 *
 * Text variables accept the `lower` and `upper` formats; [FRAME] is numeric and accepts a run of
 * zeros (`000`) giving the minimum digit count.
 */
enum class TemplateVariable(val key: String, val numeric: Boolean) {
    PROFILE("profile", numeric = false),
    ACTION("action", numeric = false),
    FACING("facing", numeric = false),
    FRAME("frame", numeric = true),
    ;

    companion object {
        fun byKey(key: String): TemplateVariable? = entries.firstOrNull { it.key == key }
    }
}

/** The values a [Template] is expanded with; a variable the template uses must not be null. */
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
