package com.roto.core.template

/**
 * A string with `{variable}` and `{variable:format}` placeholders, parsed and validated on
 * construction: an unknown variable, a format that does not fit the variable, or an unbalanced brace
 * fails here rather than at publish time.
 *
 * Formats are `lower` and `upper` for text variables and a run of zeros for numbers (`{frame:000}`
 * pads to at least three digits, never truncates). Case mapping is locale-independent, so expansion
 * gives the same string on every machine. Equality is by [source].
 */
class Template(val source: String) {

    private val parts: List<Part> = parse(source)

    /** Every variable a placeholder in this template refers to. */
    val variables: Set<TemplateVariable> = parts.filterIsInstance<Placeholder>().map { it.variable }.toSet()

    /** This template with each placeholder replaced from [values]; throws if a used value is missing. */
    fun expand(values: TemplateValues): String = render { placeholder -> placeholder.format(values) }

    /** Concatenates the literal parts with [valueFor] of each placeholder. */
    internal fun render(valueFor: (Placeholder) -> String): String = buildString {
        for (part in parts) {
            when (part) {
                is Literal -> append(part.text)
                is Placeholder -> append(valueFor(part))
            }
        }
    }

    override fun equals(other: Any?): Boolean = other is Template && other.source == source

    override fun hashCode(): Int = source.hashCode()

    override fun toString(): String = source

    internal sealed interface Part

    internal class Literal(val text: String) : Part

    internal class Placeholder(val variable: TemplateVariable, private val format: Format) : Part {

        fun format(values: TemplateValues): String {
            val value = requireNotNull(values.valueOf(variable)) { "No value for {${variable.key}}" }
            return format.apply(value.toString())
        }
    }

    internal sealed interface Format {
        fun apply(value: String): String

        data object AsIs : Format {
            override fun apply(value: String) = value
        }

        data object Lower : Format {
            override fun apply(value: String) = value.lowercase()
        }

        data object Upper : Format {
            override fun apply(value: String) = value.uppercase()
        }

        data class ZeroPad(val width: Int) : Format {
            override fun apply(value: String) = value.padStart(width, '0')
        }
    }

    private companion object {
        val ZEROS = Regex("0+")

        fun parse(source: String): List<Part> {
            val parts = mutableListOf<Part>()
            var index = 0
            while (index < source.length) {
                val open = source.indexOf('{', index)
                val literalEnd = if (open < 0) source.length else open
                val stray = source.indexOf('}', index)
                require(stray < 0 || stray >= literalEnd) { "Unmatched '}' at $stray in template '$source'" }
                if (literalEnd > index) parts += Literal(source.substring(index, literalEnd))
                if (open < 0) break
                val close = source.indexOf('}', open)
                require(close >= 0) { "Unclosed '{' at $open in template '$source'" }
                parts += placeholder(source.substring(open + 1, close), source)
                index = close + 1
            }
            return parts
        }

        fun placeholder(body: String, source: String): Placeholder {
            require('{' !in body) { "Nested '{' in template '$source'" }
            val key = body.substringBefore(':')
            val variable = requireNotNull(TemplateVariable.byKey(key)) {
                "Unknown variable {$key} in template '$source'; known: " +
                    TemplateVariable.entries.joinToString { "{${it.key}}" }
            }
            val spec = if (':' in body) body.substringAfter(':') else null
            return Placeholder(variable, format(variable, spec, source))
        }

        fun format(variable: TemplateVariable, spec: String?, source: String): Format = when {
            spec == null -> Format.AsIs
            variable.numeric && ZEROS.matches(spec) -> Format.ZeroPad(spec.length)
            !variable.numeric && spec == "lower" -> Format.Lower
            !variable.numeric && spec == "upper" -> Format.Upper
            else -> throw IllegalArgumentException(
                "Format '$spec' does not apply to {${variable.key}} in template '$source'; " +
                    if (variable.numeric) "numbers take a run of zeros such as 000" else "text takes lower or upper",
            )
        }
    }
}
