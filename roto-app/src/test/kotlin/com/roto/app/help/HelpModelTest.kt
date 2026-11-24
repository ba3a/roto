package com.roto.app.help

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HelpModelTest {
    private val library = HelpLibrary { page -> "# ${page.title}\n" }

    @Test
    fun `starts closed on the page it was given`() {
        val model = HelpModel(library, HelpPage.DRAW)

        assertFalse(model.state.open)
        assertEquals(HelpPage.DRAW, model.state.page)
    }

    @Test
    fun `toggle opens and closes the pane`() {
        val model = HelpModel(library)

        model.toggle()
        assertTrue(model.state.open)
        model.toggle()
        assertFalse(model.state.open)
    }

    @Test
    fun `the pane follows the screen and drops the anchor`() {
        val model = HelpModel(library)
        model.toggle()
        model.show(HelpPage.WIZARD, "canvas")

        model.screenChanged(HelpPage.TRIM)

        assertEquals(HelpPage.TRIM, model.state.page)
        assertNull(model.state.anchor)
        assertTrue(model.state.open)
    }

    @Test
    fun `follows a link to a page and an anchor`() {
        val model = HelpModel(library)

        assertTrue(model.follow("clip-setup.md#mask"))

        assertEquals(HelpPage.CLIP_SETUP, model.state.page)
        assertEquals("mask", model.state.anchor)
    }

    @Test
    fun `a link that names no page is left for the browser`() {
        val model = HelpModel(library)

        assertFalse(model.follow("https://example.com/page.md"))
        assertEquals(HelpPage.HOME, model.state.page)
    }
}
