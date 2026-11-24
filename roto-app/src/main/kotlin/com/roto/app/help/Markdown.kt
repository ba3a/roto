package com.roto.app.help

/** Raised for Markdown a help page may not use, or a help page that breaks the page format. */
class HelpFormatException(message: String) : IllegalArgumentException(message)

/** A run of text inside a block. Runs do not nest. */
sealed interface Span {
    /** What the run shows. */
    val text: String

    /** Text with no styling. */
    data class Plain(override val text: String) : Span

    /** Bold text, written `**text**`. */
    data class Bold(override val text: String) : Span

    /** A label, key or value, written `` `text` ``. */
    data class Code(override val text: String) : Span

    /**
     * A link, written `[text](target)`; a [target] that is not a web address is another help page.
     *
     * @property target where the link leads
     */
    data class Link(override val text: String, val target: String) : Span
}

/** The text of [spans] with no styling. */
fun plainText(spans: List<Span>): String = spans.joinToString("") { it.text }

/** One block of a help page. The subset is what the app can show and the site renders the same. */
sealed interface MarkdownBlock {
    /**
     * A heading of [level] 1 to 3.
     *
     * @property level 1 to 3
     * @property spans the heading text
     */
    data class Heading(val level: Int, val spans: List<Span>) : MarkdownBlock {
        /** The heading's anchor: how a link names it. */
        val slug: String get() = slugOf(plainText(spans))
    }

    /**
     * A paragraph.
     *
     * @property spans its text
     */
    data class Paragraph(val spans: List<Span>) : MarkdownBlock

    /**
     * A list whose items have no order.
     *
     * @property items the text of each item
     */
    data class Bullets(val items: List<List<Span>>) : MarkdownBlock

    /**
     * A list whose items are numbered.
     *
     * @property items the text of each item
     */
    data class Numbered(val items: List<List<Span>>) : MarkdownBlock

    /**
     * A table: a [header] row and [rows] of as many cells.
     *
     * @property header the header cells
     * @property rows the body rows, each a list of cells
     */
    data class Table(val header: List<List<Span>>, val rows: List<List<List<Span>>>) : MarkdownBlock
}

/** The anchor GitHub and the site give a heading: lower case, spaces as hyphens, punctuation dropped. */
fun slugOf(heading: String): String =
    heading.lowercase().filter { it.isLetterOrDigit() || it == ' ' || it == '-' }.replace(' ', '-')

private const val MAX_HEADING_LEVEL = 3
private val HEADING = Regex("^(#{1,$MAX_HEADING_LEVEL}) (.+)$")
private val NUMBERED = Regex("^\\d+\\. (.*)$")
private val TABLE_RULE = Regex("^\\|[\\s:|-]+\\|$")
private val UNSUPPORTED = listOf("```", "![", ">", "<", "    ", "\t", "* ", "+ ")
private const val BULLET = "- "
private const val CONTINUATION = "  "
private const val BOLD = "**"
private const val SHOWN = 20

/**
 * The Markdown subset a help page uses: headings 1 to 3, paragraphs, `-` and `1.` lists, pipe tables,
 * and in text `**bold**`, `` `code` `` and `[links](target)`. Anything else, code fences, images, HTML,
 * quotes, nested lists and single-star italics, fails with a [HelpFormatException] naming the line, so a page
 * cannot use what the app cannot show.
 */
object MarkdownParser {
    /** The blocks of [source], in order. */
    fun parse(source: String): List<MarkdownBlock> {
        val lines = source.replace("\r\n", "\n").split("\n")
        val blocks = mutableListOf<MarkdownBlock>()
        var index = 0
        while (index < lines.size) {
            if (lines[index].isBlank()) {
                index++
            } else {
                val (block, next) = block(lines, index)
                blocks += block
                index = next
            }
        }
        return blocks
    }

    private fun block(lines: List<String>, at: Int): Pair<MarkdownBlock, Int> {
        val line = lines[at]
        val number = at + 1
        if (UNSUPPORTED.any { line.startsWith(it) }) fail(number, "unsupported Markdown: ${line.trim().take(SHOWN)}")
        return when {
            line.startsWith("#") -> heading(line, number) to at + 1
            line.startsWith(BULLET) -> bullets(lines, at)
            NUMBERED.matches(line) -> numbered(lines, at)
            line.startsWith("|") -> table(lines, at)
            else -> paragraph(lines, at)
        }
    }

    private fun heading(line: String, number: Int): MarkdownBlock.Heading {
        val match = HEADING.matchEntire(line) ?: fail(number, "a heading is 1 to $MAX_HEADING_LEVEL # then a space")
        return MarkdownBlock.Heading(match.groupValues[1].length, spans(match.groupValues[2], number))
    }

    private fun paragraph(lines: List<String>, at: Int): Pair<MarkdownBlock, Int> {
        var end = at
        while (end < lines.size && continuesParagraph(lines[end])) end++
        val text = lines.subList(at, end).joinToString(" ") { it.trim() }
        return MarkdownBlock.Paragraph(spans(text, at + 1)) to end
    }

    private fun continuesParagraph(line: String): Boolean =
        line.isNotBlank() && !line.startsWith("#") && !line.startsWith(BULLET) && !line.startsWith("|") &&
            !NUMBERED.matches(line)

    private fun bullets(lines: List<String>, at: Int): Pair<MarkdownBlock, Int> {
        val (items, end) = items(lines, at) { line -> if (line.startsWith(BULLET)) line.removePrefix(BULLET) else null }
        return MarkdownBlock.Bullets(items) to end
    }

    private fun numbered(lines: List<String>, at: Int): Pair<MarkdownBlock, Int> {
        val (items, end) = items(lines, at) { NUMBERED.matchEntire(it)?.groupValues?.get(1) }
        return MarkdownBlock.Numbered(items) to end
    }

    /** The items of a list from [at]: [start] gives an item's text from the line that opens it, or null. */
    private fun items(lines: List<String>, at: Int, start: (String) -> String?): Pair<List<List<Span>>, Int> {
        val texts = mutableListOf<StringBuilder>()
        var end = at
        while (end < lines.size && lines[end].isNotBlank()) {
            val line = lines[end]
            val opening = start(line)
            when {
                opening != null -> texts += StringBuilder(opening.trim())
                line.startsWith(CONTINUATION) && texts.isNotEmpty() -> texts.last().append(' ').append(line.trim())
                else -> break
            }
            end++
        }
        return texts.mapIndexed { i, text -> spans(text.toString(), at + 1 + i) } to end
    }

    private fun table(lines: List<String>, at: Int): Pair<MarkdownBlock, Int> {
        var end = at
        while (end < lines.size && lines[end].startsWith("|")) end++
        val rows = lines.subList(at, end)
        if (rows.size < 2 || !TABLE_RULE.matches(rows[1].trim())) fail(at + 1, "a table needs a header and a rule line")
        val cells = rows.mapIndexed { i, row -> cells(row, at + 1 + i) }
        val width = cells.first().size
        cells.forEachIndexed { i, row -> if (row.size != width) fail(at + 1 + i, "a table row has $width cells") }
        return MarkdownBlock.Table(cells.first(), cells.drop(2)) to end
    }

    private fun cells(row: String, number: Int): List<List<Span>> =
        row.trim().removePrefix("|").removeSuffix("|").split("|").map { spans(it.trim(), number) }

    /** The runs of [text]; [number] is its line, for the error. */
    private fun spans(text: String, number: Int): List<Span> {
        val spans = mutableListOf<Span>()
        val plain = StringBuilder()
        var index = 0
        while (index < text.length) {
            val (span, next) = styled(text, index, number) ?: (null to index + 1)
            if (span == null) {
                if (text[index] == '*') fail(number, "unsupported emphasis: use ** for bold")
                plain.append(text[index])
            } else {
                if (plain.isNotEmpty()) spans += Span.Plain(plain.toString())
                plain.clear()
                spans += span
            }
            index = next
        }
        if (plain.isNotEmpty()) spans += Span.Plain(plain.toString())
        return spans
    }

    /** The bold, code or link that opens at [at], and where it ends; null when none opens there. */
    private fun styled(text: String, at: Int, number: Int): Pair<Span, Int>? =
        when {
            text.startsWith(BOLD, at) -> closed(text, at, BOLD, number) { Span.Bold(it) }
            text[at] == '`' -> closed(text, at, "`", number) { Span.Code(it) }
            text[at] == '[' -> link(text, at, number)
            else -> null
        }

    private fun closed(text: String, at: Int, mark: String, number: Int, make: (String) -> Span): Pair<Span, Int> {
        val end = text.indexOf(mark, at + mark.length)
        if (end < 0) fail(number, "unclosed $mark")
        return make(text.substring(at + mark.length, end)) to end + mark.length
    }

    private fun link(text: String, at: Int, number: Int): Pair<Span, Int> {
        val middle = text.indexOf("](", at)
        val end = text.indexOf(')', middle + 1)
        if (middle < 0 || end < 0) fail(number, "unclosed link")
        return Span.Link(text.substring(at + 1, middle), text.substring(middle + 2, end)) to end + 1
    }

    private fun fail(number: Int, message: String): Nothing = throw HelpFormatException("line $number: $message")
}
