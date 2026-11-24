package com.roto.app.help

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MarkdownParserTest {
    @Test
    fun `reads headings with their level and slug`() {
        val blocks = MarkdownParser.parse("# Draw a clip\n\n## Tips and problems\n")

        val title = blocks[0] as MarkdownBlock.Heading
        assertEquals(1, title.level)
        assertEquals("draw-a-clip", title.slug)
        assertEquals("tips-and-problems", (blocks[1] as MarkdownBlock.Heading).slug)
    }

    @Test
    fun `joins the lines of a paragraph`() {
        val blocks = MarkdownParser.parse("First line\nsecond line\n\nNext one\n")

        assertEquals(listOf(MarkdownBlock.Paragraph(listOf(Span.Plain("First line second line")))), blocks.take(1))
        assertEquals(2, blocks.size)
    }

    @Test
    fun `reads bullets and numbered lists with continuation lines`() {
        val blocks = MarkdownParser.parse("- one\n- two\n  more\n\n1. first\n2. second\n")

        val bullets = blocks[0] as MarkdownBlock.Bullets
        assertEquals(listOf("one", "two more"), bullets.items.map(::plainText))
        assertEquals(listOf("first", "second"), (blocks[1] as MarkdownBlock.Numbered).items.map(::plainText))
    }

    @Test
    fun `reads a table into header and rows`() {
        val blocks = MarkdownParser.parse("| Control | What it does |\n|---|---|\n| Done | Saves. |\n")
        val block = blocks.single() as MarkdownBlock.Table

        assertEquals(listOf("Control", "What it does"), block.header.map(::plainText))
        assertEquals(listOf("Done", "Saves."), block.rows.single().map(::plainText))
    }

    @Test
    fun `reads bold code and links in text`() {
        val paragraph = MarkdownParser.parse("Press **Done** in `roto.json` or see [draw](draw.md#zones).")
            .single() as MarkdownBlock.Paragraph

        assertEquals(
            listOf(
                Span.Plain("Press "),
                Span.Bold("Done"),
                Span.Plain(" in "),
                Span.Code("roto.json"),
                Span.Plain(" or see "),
                Span.Link("draw", "draw.md#zones"),
                Span.Plain("."),
            ),
            paragraph.spans,
        )
    }

    @Test
    fun `rejects what the app cannot show and names the line`() {
        val unsupported = listOf(
            "```\ncode\n```",
            "![picture](a.png)",
            "> quote",
            "<b>html</b>",
            "* star list",
            "A *slanted* word",
            "A **bold word",
            "A [link](broken",
            "#### too deep",
            "| a |\n| b |",
        )
        unsupported.forEach { source ->
            val failure = assertFailsWith<HelpFormatException>(source) { MarkdownParser.parse("Fine.\n\n$source") }
            assertTrue(failure.message.orEmpty().startsWith("line "), failure.message)
        }
    }

    @Test
    fun `rejects a table row of the wrong width`() {
        assertFailsWith<HelpFormatException> { MarkdownParser.parse("| a | b |\n|---|---|\n| only |\n") }
    }
}
