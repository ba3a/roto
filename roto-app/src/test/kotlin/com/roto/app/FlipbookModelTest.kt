package com.roto.app

import com.roto.core.image.Raster
import com.roto.core.model.ModelClip
import com.roto.core.model.ModelFrame
import com.roto.core.review.Flipbook
import com.roto.core.review.FlipbookFrame
import com.roto.core.review.FlipbookSource
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlipbookModelTest {

    private fun book(loop: Boolean, vararg durations: Int) = Flipbook(
        durations.mapIndexed { i, ms ->
            FlipbookFrame(Raster.of(2, 2) { _, _ -> 0xFF000000.toInt() or i }, ms, FlipbookSource.Art)
        },
        loop,
    )

    private fun loaded(loop: Boolean, vararg durations: Int) = FlipbookModel().also { model ->
        runBlocking { model.open { book(loop, *durations) } }
    }

    @Test
    fun `stepping wraps for a looping clip`() {
        val model = loaded(true, 100, 100, 100)
        model.step(-1)
        assertEquals(2, model.index)
        model.step(1)
        assertEquals(0, model.index)
    }

    @Test
    fun `stepping stops at the ends of a clip that does not loop`() {
        val model = loaded(false, 100, 100, 100)
        model.step(-1)
        assertEquals(0, model.index)
        model.step(5)
        assertEquals(2, model.index)
        model.step(1)
        assertEquals(2, model.index)
    }

    @Test
    fun `speed scales the elapsed time the clock sees`() {
        val model = loaded(true, 100, 100)
        model.chooseSpeed(50)
        model.tick(0)
        model.play()
        model.tick(100)
        assertEquals(0, model.index)
        model.tick(200)
        assertEquals(1, model.index)
        model.tick(400)
        assertEquals(0, model.index)
    }

    @Test
    fun `a clip that does not loop stops playing at its end`() {
        val model = loaded(false, 100, 100)
        model.tick(0)
        model.play()
        model.tick(150)
        assertEquals(1, model.index)
        assertTrue(model.playing)
        model.tick(500)
        assertEquals(1, model.index)
        assertTrue(!model.playing)
    }

    @Test
    fun `a paused model ignores ticks and a step pauses`() {
        val model = loaded(true, 100, 100)
        model.tick(0)
        model.play()
        model.step(1)
        model.tick(1000)
        assertEquals(1, model.index)
        assertTrue(!model.playing)
    }

    @Test
    fun `an unreadable handoff disables the player with the reason`() {
        val model = FlipbookModel()
        runBlocking { model.open { throw IOException("handoff is damaged") } }
        assertEquals(FlipbookLoad.Disabled("handoff is damaged"), model.load)
        assertNull(model.frame)
        model.play()
        assertTrue(!model.playing)
    }

    @Test
    fun `a flipbook that loads is ready with its seam and onion`() {
        val model = loaded(true, 100, 100)
        assertIs<FlipbookLoad.Ready>(model.load)
        assertNull(model.onion)
        assertNull(model.seam)
        model.toggleOnion()
        model.toggleSeam()
        assertTrue(model.onion != null)
        assertEquals(4, model.seam?.count)
    }

    @Test
    fun `the ghost stays off without a model and zoom stays in range`() {
        val model = loaded(true, 100)
        model.toggleGhost()
        assertTrue(!model.ghostOn)
        model.chooseZoom(99)
        assertEquals(16, model.zoom)
        model.chooseZoom(0)
        assertEquals(1, model.zoom)
    }

    @Test
    fun `the ghost shows the model frame the index map picks`() {
        val model = ModelClip(
            "walk", "south",
            (0 until 3).map { ModelFrame(Raster.of(2, 2) { _, _ -> 0xFF000000.toInt() or (0x10 + it) }, 100) },
            true, "test",
        )
        val player = FlipbookModel(model)
        runBlocking { player.open { book(true, 100, 100, 100, 100, 100) } }
        assertNull(player.ghost)
        player.toggleGhost()
        assertTrue(player.ghostOn)
        assertEquals(model.frames[model.frameIndexFor(0, 5)].raster, player.ghost)
        player.step(3)
        assertEquals(3, player.index)
        assertEquals(model.frames[model.frameIndexFor(3, 5)].raster, player.ghost)
        assertTrue(model.frameIndexFor(0, 5) != model.frameIndexFor(3, 5))
        player.toggleGhost()
        assertNull(player.ghost)
    }
}
