package com.roto.core.publish

import com.roto.core.ase.AseCel
import com.roto.core.ase.AseFrame
import com.roto.core.ase.AseLayer
import com.roto.core.ase.AseSprite
import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import com.roto.core.image.Raster
import com.roto.core.project.sampleConfig
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PublishPartsTest {

    private val red = Argb.of(200, 30, 30)

    @Test
    fun `art is the art layer only, placed by its cel, its opacity applied`() {
        val sprite = AseSprite(
            width = 4,
            height = 2,
            palette = listOf(Argb.TRANSPARENT, red),
            layers = listOf(AseLayer("reference"), AseLayer("draft"), AseLayer("art")),
            frames = listOf(
                AseFrame(
                    90,
                    listOf(
                        AseCel(1, IndexedRaster.of(4, 2) { _, _ -> 1 }),
                        AseCel(2, IndexedRaster.of(2, 1) { x, _ -> x }, x = 2, y = 1, opacity = 128),
                    ),
                ),
            ),
        )

        val frame = ArtFrames.read(sprite, "walk_EAST").single()

        assertEquals(90, frame.durationMs)
        assertEquals(Argb.TRANSPARENT, frame.image[0, 0])
        assertEquals(Argb.TRANSPARENT, frame.image[2, 1])
        assertEquals(Argb.of(200, 30, 30, 128), frame.image[3, 1])
        val withoutArt = sprite.copy(
            layers = sprite.layers.take(2),
            frames = listOf(AseFrame(90, listOf(AseCel(1, IndexedRaster(4, 2))))),
        )
        assertFailsWith<IllegalArgumentException> { ArtFrames.read(withoutArt, "walk_EAST") }
    }

    @Test
    fun `mirroring flips across the canvas`() {
        val ramp = Raster.of(3, 1) { x, _ -> Argb.of(x * 100, 0, 0) }

        assertEquals(Raster.of(3, 1) { x, _ -> Argb.of((2 - x) * 100, 0, 0) }, Mirror.flip(ramp))
    }

    @Test
    fun `a manifest round-trips with its field names`() {
        val frames = listOf(ManifestFrame("a/000.png", 120))
        val manifest = ClipManifest("walk", "WEST", "walk_EAST", mirrored = true, loop = true, frames)
        val text = ByteArrayOutputStream().also { ClipManifestCodec.write(manifest, it) }.toString(Charsets.UTF_8)

        assertEquals(manifest, ClipManifestCodec.read(ByteArrayInputStream(text.toByteArray())))
        listOf("action", "facing", "source", "mirrored", "loop", "frames", "file", "durationMs").forEach {
            assertContains(text, "\"$it\"")
        }
        assertFailsWith<ClipManifestException> {
            ClipManifestCodec.read(ByteArrayInputStream(text.replace("a/000.png", "../000.png").toByteArray()))
        }
    }

    @Test
    fun `a facing's mirror comes from roto json's pairs, either way round`() {
        val config = sampleConfig()

        assertEquals("WEST", config.mirrorFacing("EAST"))
        assertEquals("EAST", config.mirrorFacing("WEST"))
        assertNull(config.mirrorFacing("NORTH"))
    }
}
