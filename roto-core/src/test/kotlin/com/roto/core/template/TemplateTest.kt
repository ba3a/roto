package com.roto.core.template

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class TemplateTest {

    private val values = TemplateValues(profile = "worker_float_tech", action = "walk", facing = "EAST", frame = 7)

    @Test
    fun `placeholders expand from the values and literals stay as they are`() {
        val template = Template("{profile}/{action}_{facing}/{frame}.png")

        assertEquals("worker_float_tech/walk_EAST/7.png", template.expand(values))
    }

    @Test
    fun `text formats change case`() {
        val template = Template("{facing:lower}-{facing:upper}-{action}-{action:upper}")

        assertEquals("east-EAST-walk-WALK", template.expand(values))
    }

    @Test
    fun `case mapping ignores the default locale`() {
        val previous = Locale.getDefault()
        // Turkish maps 'i' to a dotted capital and 'I' to a dotless small letter.
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        try {
            val template = Template("{action:upper} {facing:lower}")

            assertEquals("IDLE inside", template.expand(TemplateValues(action = "idle", facing = "INSIDE")))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `zero formats pad to a minimum width and never truncate`() {
        assertEquals("007", Template("{frame:000}").expand(values))
        assertEquals("0", Template("{frame:0}").expand(TemplateValues(frame = 0)))
        assertEquals("1234", Template("{frame:000}").expand(TemplateValues(frame = 1234)))
        assertEquals("00012", Template("{frame:00000}").expand(TemplateValues(frame = 12)))
    }

    @Test
    fun `a template without placeholders expands to itself`() {
        assertEquals("./gradlew.bat", Template("./gradlew.bat").expand(TemplateValues()))
        assertEquals("", Template("").expand(TemplateValues()))
    }

    @Test
    fun `variables lists what the placeholders use`() {
        val template = Template("--args={profile} {profile:upper} {frame:00}")

        assertEquals(setOf(TemplateVariable.PROFILE, TemplateVariable.FRAME), template.variables)
        assertEquals(emptySet(), Template("plain").variables)
    }

    @Test
    fun `a missing value fails on expansion and names the variable`() {
        val template = Template("{action}/{facing}")

        val error = assertFailsWith<IllegalArgumentException> { template.expand(values.copy(facing = null)) }

        assertContains(error.message.orEmpty(), "{facing}")
    }

    @Test
    fun `unknown variables are rejected on construction`() {
        val error = assertFailsWith<IllegalArgumentException> { Template("{skin}/{frame}") }

        assertContains(error.message.orEmpty(), "{skin}")
    }

    @Test
    fun `formats that do not fit the variable are rejected`() {
        assertFailsWith<IllegalArgumentException> { Template("{frame:lower}") }
        assertFailsWith<IllegalArgumentException> { Template("{facing:000}") }
        assertFailsWith<IllegalArgumentException> { Template("{facing:title}") }
        assertFailsWith<IllegalArgumentException> { Template("{frame:00x}") }
        assertFailsWith<IllegalArgumentException> { Template("{frame:}") }
    }

    @Test
    fun `unbalanced braces are rejected`() {
        assertFailsWith<IllegalArgumentException> { Template("{action") }
        assertFailsWith<IllegalArgumentException> { Template("action}") }
        assertFailsWith<IllegalArgumentException> { Template("{action}}") }
        assertFailsWith<IllegalArgumentException> { Template("{{action}") }
        assertFailsWith<IllegalArgumentException> { Template("{}") }
    }

    @Test
    fun `negative frame numbers are rejected`() {
        assertFailsWith<IllegalArgumentException> { TemplateValues(frame = -1) }
    }

    @Test
    fun `equality is by source`() {
        assertEquals(Template("{frame:000}"), Template("{frame:000}"))
        assertEquals(Template("{frame:000}").hashCode(), Template("{frame:000}").hashCode())
        assertNotEquals(Template("{frame:000}"), Template("{frame:00}"))
        assertEquals("{frame:000}", Template("{frame:000}").toString())
    }
}
