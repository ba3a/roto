package com.roto.core.preset

import com.roto.core.palette.Palette
import com.roto.core.palette.Ramp
import com.roto.core.palette.RampSet
import com.roto.core.project.Canvas
import com.roto.core.project.MirrorPair
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PresetCodecTest {
    private val dark = 0xFF202020.toInt()
    private val light = 0xFFE0E0E0.toInt()
    private val palette = Palette(listOf(dark, light))
    private val ramps = RampSet(ramps = listOf(Ramp("skin", listOf(dark, light))))
    private val preset = ProjectPreset(
        "Side", "A side view", listOf("LEFT", "RIGHT"), listOf(MirrorPair("LEFT", "RIGHT")), listOf("idle"),
        Canvas(32, 32, 30), OutputLayout.ONE_FOLDER_PER_PROFILE, "art", palette, ramps,
    )

    private fun encode(info: PresetInfo) = ByteArrayOutputStream().also { PresetCodec.write(info, it) }.toByteArray()

    private fun read(text: String) = PresetCodec.read(ByteArrayInputStream(text.toByteArray()))

    @Test
    fun `write then read round trips and bytes are identical`() {
        val bytes = encode(preset.info)

        assertEquals(preset.info, PresetCodec.read(ByteArrayInputStream(bytes)))
        assertEquals(bytes.toList(), encode(preset.info).toList())
    }

    @Test
    fun `the layout is written by its lowercase key`() {
        assertContains(encode(preset.info).toString(Charsets.UTF_8), "\"layout\": \"one-folder-per-profile\"")
    }

    @Test
    fun `an afterPublish key is refused`() {
        val text = encode(preset.info).toString(Charsets.UTF_8).replaceFirst("{", "{\"afterPublish\": [\"x\"],")

        assertFailsWith<PresetException> { read(text) }
    }

    @Test
    fun `an unknown layout or key is refused`() {
        val text = encode(preset.info).toString(Charsets.UTF_8)

        assertFailsWith<PresetException> { read(text.replace("one-folder-per-profile", "nope")) }
        assertFailsWith<PresetException> { read(text.replaceFirst("{", "{\"extra\": 1,")) }
    }

    @Test
    fun `an invalid preset is refused on read`() {
        val text = encode(preset.info).toString(Charsets.UTF_8).replace("\"idle\"", "")

        assertFailsWith<PresetException> { read(text) }
    }

    @Test
    fun `info and of are inverse`() {
        assertEquals(preset, ProjectPreset.of(preset.info, palette, ramps))
        assertEquals(preset.info, ProjectPreset.of(preset.info, palette, ramps).info)
    }
}
