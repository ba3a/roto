package com.roto.core.site

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pins the landing-page guidance in `CLAUDE.md` and the pull request template. */
class LandingGuidanceTest {

    private val root = Path.of("..")

    private val section = listOf(
        "## Website landing page (ba3a.dev)",
        "",
        "`site/landing.md` is this project's landing page on ba3a.dev. The site fetches it from `main`",
        "and republishes within about 15 minutes of a push; nothing else has to change. It goes stale",
        "fast, so keep it true.",
        "",
        "- When a change alters what a user sees or types (commands, flags, output, install steps,",
        "  supported platforms, features, limits, license, version or size claims), update the matching",
        "  section of `site/landing.md` in the same commit.",
        "- Every command, number and output sample on the page must match the code. Re-run a sample",
        "  when you touch it.",
        "- Keep the section markers (`<div class=\"section-marker\" id=\"name\"></div>`) and their names",
        "  (hero, problem, audience, how-it-works, timeline, spec-example, features, compare, dogfood,",
        "  quick-start, faq, final-cta). The theme lays the sections out; do not add layout markup.",
        "- Links and images must resolve: a broken one fails the ba3a.dev build.",
        "- Leave `site/brand/` and `site/card.png` alone unless asked.",
    )

    @Test
    fun `CLAUDE md ends with the landing section verbatim`() {
        val text = root.resolve("CLAUDE.md").readText()

        assertTrue(text.endsWith("\n") && !text.endsWith("\n\n"), "single trailing newline")
        val lines = text.lines().dropLast(1)
        assertEquals(section, lines.takeLast(section.size))
        assertEquals("", lines[lines.size - section.size - 1], "one blank line before the section")
        assertTrue(lines[lines.size - section.size - 2].isNotBlank(), "only one blank line before the section")
    }

    @Test
    fun `pull request template asks whether the landing page is still true`() {
        val lines = root.resolve(".github/pull_request_template.md").readText().lines()

        assertContains(lines, "- [ ] Landing page (`site/landing.md`) still true?")
    }
}
