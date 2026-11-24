package com.roto.core.preset

import com.roto.core.palette.Palette
import com.roto.core.palette.Ramp
import com.roto.core.palette.RampSet
import com.roto.core.project.Canvas
import com.roto.core.project.MirrorPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProjectPresetTest {
    private val dark = 0xFF202020.toInt()
    private val mid = 0xFF808080.toInt()
    private val light = 0xFFE0E0E0.toInt()
    private val palette = Palette(listOf(dark, mid, light))
    private val ramps = RampSet(ramps = listOf(Ramp("skin", listOf(dark, mid))))

    private fun preset(
        facings: List<String> = listOf("LEFT", "RIGHT"),
        mirrorPairs: List<MirrorPair> = listOf(MirrorPair("LEFT", "RIGHT")),
        actions: List<String> = listOf("idle"),
        canvas: Canvas = Canvas(32, 32, 30),
        ramps: RampSet = this.ramps,
    ) = ProjectPreset(
        "Side", "A side view", facings, mirrorPairs, actions, canvas,
        OutputLayout.ONE_FOLDER_PER_CLIP, "art", palette, ramps,
    )

    @Test
    fun `toConfig gives a valid config with the fixed palette and ramps paths and no hook`() {
        val config = preset().toConfig("game")

        assertEquals("game", config.id)
        assertEquals("roto/palette.hex", config.palette)
        assertEquals("roto/ramps.json", config.ramps)
        assertTrue(config.afterPublish.isEmpty())
        assertEquals(OutputLayout.ONE_FOLDER_PER_CLIP.templates("art").output, config.output)
    }

    @Test
    fun `an unknown facing in a mirror pair is refused`() {
        val error = assertFailsWith<IllegalArgumentException> {
            preset(mirrorPairs = listOf(MirrorPair("LEFT", "UP")))
        }
        assertTrue("UP" in error.message.orEmpty())
    }

    @Test
    fun `empty actions are refused`() {
        assertFailsWith<IllegalArgumentException> { preset(actions = emptyList()) }
    }

    @Test
    fun `a canvas whose ground row is off the canvas is refused`() {
        assertFailsWith<IllegalArgumentException> { preset(canvas = Canvas(32, 32, 40)) }
    }

    @Test
    fun `a ramp colour outside the palette is refused`() {
        val outside = RampSet(ramps = listOf(Ramp("skin", listOf(dark, 0xFF123456.toInt()))))
        val error = assertFailsWith<IllegalArgumentException> { preset(ramps = outside) }
        assertTrue("#123456" in error.message.orEmpty())
    }

    @Test
    fun `an outline colour outside the palette is refused`() {
        assertFailsWith<IllegalArgumentException> { preset(ramps = RampSet(0xFF123456.toInt(), ramps.ramps)) }
    }
}
