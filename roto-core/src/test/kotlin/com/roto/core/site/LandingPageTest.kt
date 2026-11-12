package com.roto.core.site

import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/** Pins the landing page content under `site/`, which the project website publishes. */
class LandingPageTest {

    private val site = Path.of("..", "site")

    @Test
    fun `landing page is the placeholder text, still marked TODO`() {
        val expected = listOf(
            "<!-- TODO: rewrite -->",
            "",
            "- Reference footage in, a layered `.ase` draft out for polishing in LibreSprite or Aseprite, " +
                "then publishes the finished frames into your game.",
            "- Every step is a deterministic algorithm or the artist's own hand: no machine learning, " +
                "no generative models; the same inputs always give byte-identical output.",
            "- Desktop app and CLI.",
            "- Early development.",
        )

        val text = site.resolve("landing.md").readText()

        assertContains(text, "<!-- TODO: rewrite -->")
        assertEquals(expected, text.lines().dropLastWhile { it.isEmpty() })
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
