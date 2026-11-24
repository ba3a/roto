package com.roto.app.help

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class HelpDocumentTest {
    private val source = """
        # Draw a clip

        ## Purpose

        Text.

        ## Controls

        | Control | What it does |
        |---|---|
        | Done | Saves the clip. Goes home. |
        | Mark head | Marks the head. |

        ## Tips and problems

        - A tip.
    """.trimIndent()

    @Test
    fun `reads the title and the section names`() {
        val document = HelpDocument.parse(source)

        assertEquals("Draw a clip", document.title)
        assertEquals(listOf("Purpose", "Controls", "Tips and problems"), document.sectionNames)
    }

    @Test
    fun `reads the controls table and gives the first sentence`() {
        val document = HelpDocument.parse(source)

        assertEquals(listOf("Done", "Mark head"), document.controls.keys.toList())
        assertEquals("Saves the clip.", document.summary("Done"))
        assertEquals("Marks the head.", document.summary("Mark head"))
        assertNull(document.summary("Missing"))
    }

    @Test
    fun `a heading's slug is an anchor`() {
        val anchors = HelpDocument.parse(source).anchors

        assertEquals(setOf("draw-a-clip", "purpose", "controls", "tips-and-problems"), anchors)
    }

    @Test
    fun `a page without a level 1 heading is refused`() {
        assertFailsWith<HelpFormatException> { HelpDocument.parse("## Purpose\n") }
    }

    @Test
    fun `a control listed twice is refused`() {
        val twice = "# T\n\n## Controls\n\n| Control | What |\n|---|---|\n| A | x |\n| A | y |\n"

        assertFailsWith<HelpFormatException> { HelpDocument.parse(twice) }
    }
}
