package com.roto.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the landing page under `site/`: the page and its card image must exist, and every section
 * marker (`<div class="section-marker" id="NAME"></div>`) must carry one of the known ids, each at
 * most once. The check takes a repository root, so it is tested against crafted directories as well
 * as the real checkout.
 */
class LandingPageTest {

    private val allowedIds = setOf(
        "hero", "problem", "audience", "how-it-works", "timeline", "spec-example",
        "features", "compare", "dogfood", "quick-start", "faq", "final-cta",
    )

    private val marker = Regex("""<div\s+class="section-marker"\s+id="([^"]*)"\s*>\s*</div>""")

    /** Lists what is wrong with the landing page under [root]; empty when it is fine. */
    private fun problems(root: Path): List<String> {
        val site = root.resolve("site")
        val landing = site.resolve("landing.md")
        val problems = mutableListOf<String>()
        if (!site.resolve("card.png").exists()) problems += "missing site/card.png"
        if (!landing.exists()) return problems + "missing site/landing.md"

        val ids = marker.findAll(landing.readText()).map { it.groupValues[1] }.toList()
        problems += ids.filter { it !in allowedIds }.distinct().map { "unknown section marker id: '$it'" }
        problems += ids.filter { it in allowedIds }.groupingBy { it }.eachCount()
            .filterValues { it > 1 }.keys.sorted().map { "duplicate section marker id: '$it'" }
        return problems
    }

    private fun site(landing: String?, card: Boolean = true, body: (Path) -> Unit) {
        val root = Files.createTempDirectory("roto-landing")
        try {
            root.resolve("site").createDirectories()
            if (landing != null) root.resolve("site/landing.md").writeText(landing)
            if (card) root.resolve("site/card.png").writeText("png")
            body(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun div(id: String) = """<div class="section-marker" id="$id"></div>"""

    @Test
    fun `the repository landing page is fine`() {
        assertEquals(emptyList(), problems(Path.of("..")))
    }

    @Test
    fun `a page with every known marker once is fine`() = site(allowedIds.joinToString("\n") { div(it) }) {
        assertEquals(emptyList(), problems(it))
    }

    @Test
    fun `a page without markers is fine`() = site("placeholder\n") {
        assertEquals(emptyList(), problems(it))
    }

    @Test
    fun `a missing landing page is reported`() = site(landing = null) {
        assertEquals(listOf("missing site/landing.md"), problems(it))
    }

    @Test
    fun `a missing card image is reported`() = site("text\n", card = false) {
        assertEquals(listOf("missing site/card.png"), problems(it))
    }

    @Test
    fun `an unknown marker id is reported`() = site("${div("hero")}\n${div("pricing")}\n") {
        assertEquals(listOf("unknown section marker id: 'pricing'"), problems(it))
    }

    @Test
    fun `a duplicate marker id is reported`() = site("${div("faq")}\n${div("hero")}\n${div("faq")}\n") {
        assertEquals(listOf("duplicate section marker id: 'faq'"), problems(it))
    }
}
