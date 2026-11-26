package com.roto.core.model

import com.roto.core.image.Argb
import com.roto.core.image.Raster
import com.roto.core.project.Canvas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ModelCanvasTest {

    private val canvas = Canvas(5, 4, 3)
    private val ink = 0xFF112233.toInt()

    @Test
    fun `a smaller frame is centred across and bottom-aligned`() {
        val placed = ModelCanvas.place("f", Raster.of(2, 2) { _, _ -> ink }, canvas)

        assertEquals(5, placed.width)
        assertEquals(4, placed.height)
        val inked = (0 until 4)
            .flatMap { y -> (0 until 5).map { x -> x to y } }
            .filter { (x, y) -> placed[x, y] == ink }
        assertEquals(listOf(1 to 2, 2 to 2, 1 to 3, 2 to 3), inked)
        assertEquals(Argb.TRANSPARENT, placed[0, 0])
    }

    @Test
    fun `a frame of canvas size comes back as it is`() {
        val frame = Raster.of(5, 4) { _, _ -> ink }
        assertSame(frame, ModelCanvas.place("f", frame, canvas))
    }

    @Test
    fun `a frame larger either way is refused naming both sizes`() {
        val failure = assertFailsWith<ModelException> { ModelCanvas.place("f.png", Raster(4, 5), canvas) }
        assertEquals("f.png is 4x5, larger than the 5x4 canvas", failure.message)
    }
}
