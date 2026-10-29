package com.roto.core.template

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PathTemplateTest {

    private val output = PathTemplate("raw-art/{profile}/{action}_{facing:lower}/{frame:000}.png")
    private val values = TemplateValues(profile = "worker_float_tech", action = "walk", facing = "EAST", frame = 7)

    @Test
    fun `expands to a relative path with forward slashes`() {
        assertEquals("raw-art/worker_float_tech/walk_east/007.png", output.expand(values))
    }

    @Test
    fun `resolves under the root`() {
        val root = Path.of("project").toAbsolutePath()

        val file = output.resolve(root, values)

        assertEquals(root.resolve("raw-art").resolve("worker_float_tech").resolve("walk_east").resolve("007.png"), file)
    }

    @Test
    fun `templates that are not relative or climb out are rejected`() {
        listOf(
            "/raw-art/{frame}.png",
            "raw-art/{frame}.png/",
            "raw-art//{frame}.png",
            "../raw-art/{frame}.png",
            "raw-art/./{frame}.png",
            "raw-art/{profile}/../{frame}.png",
            "C:/raw-art/{frame}.png",
            "raw-art\\{frame}.png",
            "raw-art/{frame}?.png",
            "",
        ).forEach { source ->
            assertFailsWith<IllegalArgumentException>(source) { PathTemplate(source) }
        }
    }

    @Test
    fun `a placeholder may stand for a whole segment or part of one`() {
        val template = PathTemplate("{profile}/{action}.{facing:lower}-{frame:00}")

        assertEquals("worker_float_tech/walk.east-07", template.expand(values))
    }

    @Test
    fun `a value cannot add a directory`() {
        val error = assertFailsWith<IllegalArgumentException> { output.expand(values.copy(profile = "worker/other")) }

        assertContains(error.message.orEmpty(), "worker/other")
        assertFailsWith<IllegalArgumentException> { output.expand(values.copy(profile = "worker\\other")) }
        assertFailsWith<IllegalArgumentException> { output.expand(values.copy(action = "walk:1")) }
    }

    @Test
    fun `a value cannot climb out or leave a segment empty`() {
        assertFailsWith<IllegalArgumentException> { output.expand(values.copy(profile = "..")) }
        assertFailsWith<IllegalArgumentException> { output.expand(values.copy(profile = ".")) }
        assertFailsWith<IllegalArgumentException> { output.expand(values.copy(profile = "")) }
    }

    @Test
    fun `variables and equality come from the template`() {
        assertEquals(TemplateVariable.entries.toSet(), output.variables)
        assertEquals(PathTemplate(output.source), output)
        assertEquals(output.source, output.toString())
    }
}
