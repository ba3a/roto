package com.roto.core.site

import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Pins the landing page's key content and the card image under `site/`, which the project website publishes. */
class LandingPageTest {

    private val site = Path.of("..", "site")

    @Test
    fun `landing page describes the wizard and bundles and is no longer a placeholder`() {
        val text = site.resolve("landing.md").readText()

        assertFalse("TODO" in text)
        assertContains(text, "id=\"quick-start\"")
        assertContains(text, "project wizard")
        assertContains(text, "roto init my-game --preset side-view --id my-game")
        assertContains(text, "roto export my-setup.rotopreset --project my-game")
    }

    @Test
    fun `card image is the current card`() {
        val card = site.resolve("card.png").readBytes()

        assertEquals(12683, card.size)
        assertEquals(
            "bff35f7afa9229cfd1cb6be045f7f7c78c68aeb423927f16330b7ae8dd1d57ad",
            MessageDigest.getInstance("SHA-256").digest(card).joinToString("") { "%02x".format(it) },
        )
    }
}
