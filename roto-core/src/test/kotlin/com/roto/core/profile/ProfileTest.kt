package com.roto.core.profile

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ProfileTest {

    private val walkEast = sampleProfile().clips.first()

    @Test
    fun `a clip id joins action and facing`() {
        assertEquals("walk_EAST", walkEast.id)
    }

    @Test
    fun `clips are found by action and facing`() {
        val profile = sampleProfile()

        assertEquals(walkEast, profile.clip("walk", "EAST"))
        assertNull(profile.clip("walk", "WEST"))
        assertNull(profile.clip("carry", "EAST"))
    }

    @Test
    fun `an empty profile is valid`() {
        assertEquals(Profile(emptyList(), emptyList()), Profile())
    }

    @Test
    fun `two clips for the same action and facing are rejected, even differing only in case`() {
        val again = walkEast.copy(source = "IMG_0500.MOV")
        val shouted = walkEast.copy(action = "WALK")

        val error = assertFailsWith<IllegalArgumentException> { Profile(clips = listOf(walkEast, again)) }
        assertContains(error.message.orEmpty(), "walk_EAST")
        assertFailsWith<IllegalArgumentException> { Profile(clips = listOf(walkEast, shouted)) }
    }

    @Test
    fun `ramps must be distinct and not blank`() {
        assertFailsWith<IllegalArgumentException> { Profile(ramps = listOf("skin", "skin")) }
        assertFailsWith<IllegalArgumentException> { Profile(ramps = listOf("skin", " ")) }
    }

    @Test
    fun `action and facing must be safe names`() {
        assertFailsWith<IllegalArgumentException> { walkEast.copy(action = "walk/run") }
        assertFailsWith<IllegalArgumentException> { walkEast.copy(facing = "") }
        assertFailsWith<IllegalArgumentException> { walkEast.copy(facing = "NORTH EAST") }
    }

    @Test
    fun `the source must stay inside the sources folder`() {
        assertFailsWith<IllegalArgumentException> { walkEast.copy(source = "../IMG_0412.MOV") }
        assertFailsWith<IllegalArgumentException> { walkEast.copy(source = "/videos/IMG_0412.MOV") }
        assertFailsWith<IllegalArgumentException> { walkEast.copy(source = "C:/videos/IMG_0412.MOV") }
        assertFailsWith<IllegalArgumentException> { walkEast.copy(source = "") }
    }

    @Test
    fun `a time range starts at zero or later and ends after it starts`() {
        assertEquals(3_600, TimeRange(1_200, 4_800).durationMs)
        assertFailsWith<IllegalArgumentException> { TimeRange(-1, 100) }
        assertFailsWith<IllegalArgumentException> { TimeRange(100, 100) }
        assertFailsWith<IllegalArgumentException> { TimeRange(200, 100) }
    }
}
