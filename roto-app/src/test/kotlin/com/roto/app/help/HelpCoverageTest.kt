package com.roto.app.help

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Locks the help to the screens: every page is bundled, well formed and in the page format; every
 * control marked in code has its row and every row has its control; every link lands.
 */
class HelpCoverageTest {
    private val library = HelpLibrary.bundled()

    @Test
    fun `every page is bundled and its title is the one the app knows`() {
        HelpPage.entries.forEach { page ->
            assertEquals(page.title, library.document(page).title, "title of ${page.file}")
        }
    }

    @Test
    fun `a screen page has its sections in order`() {
        HelpPage.entries.filter { it.isScreenPage }.forEach { page ->
            val names = library.document(page).sectionNames
            val allowed = listOf(PAGE_SECTIONS, PAGE_SECTIONS - GESTURES_SECTION)
            assertTrue(names in allowed, "sections of ${page.file}: $names")
        }
    }

    @Test
    fun `every control in code has a row and every row has a control`() {
        HelpPage.entries.filter { it.isScreenPage }.forEach { page ->
            val inCode = HelpControl.entries.filter { it.page == page }.map { it.label }
            val inPage = library.document(page).controls.keys
            assertEquals(inCode.toSet(), inPage, "controls of ${page.file}")
            assertEquals(inCode.size, inCode.toSet().size, "a label is used twice on ${page.file}")
        }
    }

    @Test
    fun `no page but a screen page lists controls`() {
        assertEquals(emptyMap(), library.document(HelpPage.INDEX).controls)
        assertTrue(HelpControl.entries.all { it.page.isScreenPage })
    }

    @Test
    fun `every control has a tooltip that is one sentence`() {
        HelpControl.entries.forEach { control ->
            val tip = library.tooltip(control)
            assertNotNull(tip, "${control.page.file}: ${control.label}")
            assertTrue(tip.isNotBlank() && tip.endsWith("."), "${control.label}: $tip")
        }
    }

    @Test
    fun `every link lands on a page and an anchor that exist`() {
        HelpPage.entries.forEach { page ->
            links(library.document(page)).filter { !it.startsWith("http") }.forEach { target ->
                val resolved = assertNotNull(HelpPage.resolve(target), "${page.file}: $target names no page")
                val anchor = resolved.anchor
                if (anchor != null) {
                    assertTrue(anchor in library.document(resolved.page).anchors, "${page.file}: $target")
                }
            }
        }
    }

    @Test
    fun `every page can be reached from the index`() {
        val linked = links(library.document(HelpPage.INDEX)).mapNotNull { HelpPage.resolve(it)?.page }.toSet()

        assertEquals(HelpPage.entries.filter { it != HelpPage.INDEX }.toSet(), linked)
    }

    private fun links(document: HelpDocument): List<String> =
        document.blocks.flatMap { block ->
            when (block) {
                is MarkdownBlock.Heading -> block.spans
                is MarkdownBlock.Paragraph -> block.spans
                is MarkdownBlock.Bullets -> block.items.flatten()
                is MarkdownBlock.Numbered -> block.items.flatten()
                is MarkdownBlock.Table -> (block.header + block.rows.flatten()).flatten()
            }
        }.filterIsInstance<Span.Link>().map { it.target }
}
