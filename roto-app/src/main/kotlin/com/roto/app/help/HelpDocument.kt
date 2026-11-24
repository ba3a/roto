package com.roto.app.help

/** The sections a screen's help page has, in this order. [GESTURES_SECTION] may be left out. */
val PAGE_SECTIONS = listOf("Purpose", "Steps", "Controls", "Gestures", "Tips and problems")

/** The section that lists every control by its label. */
const val CONTROLS_SECTION = "Controls"

/** The one section a page may leave out. */
const val GESTURES_SECTION = "Gestures"

/**
 * One parsed help page: a level 1 title, then level 2 sections. The Controls section holds a table
 * whose first column names a control as the screen labels it and whose second says what it does;
 * that is where tooltips come from.
 *
 * @property blocks the page's blocks, in order.
 */
class HelpDocument(val blocks: List<MarkdownBlock>) {
    /** The page's title: its level 1 heading. */
    val title: String = (blocks.firstOrNull() as? MarkdownBlock.Heading)
        ?.takeIf { it.level == 1 }
        ?.let { plainText(it.spans) }
        ?: throw HelpFormatException("a help page starts with a level 1 heading")

    /** The names of the level 2 sections, in order. */
    val sectionNames: List<String> = blocks.filterIsInstance<MarkdownBlock.Heading>()
        .filter { it.level == 2 }
        .map { plainText(it.spans) }

    /** The anchors a link may name: every heading's slug. */
    val anchors: Set<String> = blocks.filterIsInstance<MarkdownBlock.Heading>().map { it.slug }.toSet()

    /** Control label to what it does, from the Controls section; empty when the page has none. */
    val controls: Map<String, String> = controlTable()

    /** The first sentence of what the control labelled [label] does, or null when the page lacks it. */
    fun summary(label: String): String? = controls[label]?.let(::firstSentence)

    private fun controlTable(): Map<String, String> {
        val table = section(CONTROLS_SECTION).filterIsInstance<MarkdownBlock.Table>().firstOrNull()
            ?: return emptyMap()
        val result = LinkedHashMap<String, String>()
        table.rows.forEach { row ->
            val label = plainText(row[0])
            if (result.put(label, plainText(row[1])) != null) throw HelpFormatException("control twice: $label")
        }
        return result
    }

    private fun section(name: String): List<MarkdownBlock> {
        val start = blocks.indexOfFirst { it is MarkdownBlock.Heading && it.level == 2 && plainText(it.spans) == name }
        if (start < 0) return emptyList()
        val rest = blocks.drop(start + 1)
        val end = rest.indexOfFirst { it is MarkdownBlock.Heading && it.level <= 2 }
        return if (end < 0) rest else rest.take(end)
    }

    /** Parsing a page. */
    companion object {
        /** The document [source] holds. */
        fun parse(source: String): HelpDocument = HelpDocument(MarkdownParser.parse(source))
    }
}

/** [text] up to and including its first full stop that ends a sentence, or all of it. */
internal fun firstSentence(text: String): String {
    val end = text.indexOf(". ")
    return if (end < 0) text else text.substring(0, end + 1)
}
