package com.roto.core.handoff

import com.roto.core.ase.AseCel
import com.roto.core.ase.AseLayer
import com.roto.core.ase.AseSprite
import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import com.roto.core.palette.Palette
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HandoffMergeTest {

    private val black = Argb.of(0, 0, 0)
    private val red = Argb.of(200, 30, 30)
    private val white = Argb.of(255, 255, 255)
    private val palette = Palette(listOf(black, red, white))

    private fun render(frames: Int = 2, palette: Palette = this.palette, draftLabel: Int = 2) = HandoffRender(
        palette,
        width = 4,
        height = 3,
        frames = List(frames) { index ->
            HandoffFrame(
                durationMs = 100 + index,
                reference = IndexedRaster.of(4, 3) { _, _ -> 1 },
                draft = IndexedRaster.of(4, 3) { x, _ -> if (x == 1) draftLabel else 0 },
            )
        },
    )

    /** What the artist painted: palette label 2, red, on the art layer of [frame]. */
    private fun painted(sprite: AseSprite, frame: Int, x: Int = 2): AseSprite {
        val art = sprite.layers.indexOfFirst { it.name == HandoffMerge.ART }
        val frames = sprite.frames.toMutableList()
        val stroke = IndexedRaster.of(2, 2) { px, _ -> if (px == 0) 2 else 0 }
        frames[frame] = frames[frame].copy(cels = frames[frame].cels + AseCel(art, stroke, x, y = 1))
        return sprite.copy(frames = frames)
    }

    @Test
    fun `a first handoff has reference, draft and an empty art layer over a transparent entry 0`() {
        val sprite = HandoffMerge.merge(null, render())

        assertEquals(listOf(Argb.TRANSPARENT, black, red, white), sprite.palette)
        assertEquals(0, sprite.transparentIndex)
        assertEquals(listOf("reference", "draft", "art"), sprite.layers.map { it.name })
        assertEquals(false, sprite.layers.first().editable)
        assertEquals(listOf(100, 101), sprite.frames.map { it.durationMs })
        assertEquals(listOf(0 to 102, 1 to 255), sprite.frames.first().cels.map { it.layerIndex to it.opacity })
    }

    @Test
    fun `re-rendering rewrites reference and draft and keeps the art exactly`() {
        val saved = painted(HandoffMerge.merge(null, render()), frame = 1)

        val rerendered = HandoffMerge.merge(saved, render(draftLabel = 3))

        assertEquals(saved.frames[1].cels.last(), rerendered.frames[1].cels.last())
        assertEquals(3, rerendered.frames[1].cels[1].image[1, 0])
        assertEquals(2, rerendered.frames[0].cels.size)
    }

    @Test
    fun `layers the artist added come through, and hidden roto layers stay hidden`() {
        val first = HandoffMerge.merge(null, render())
        val saved = first.copy(
            layers = listOf(first.layers[0].copy(visible = false)) + first.layers.drop(1) +
                AseLayer("shadow", opacity = 128),
        )

        val rerendered = HandoffMerge.merge(saved, render())

        assertEquals(listOf("reference", "draft", "art", "shadow"), rerendered.layers.map { it.name })
        assertEquals(false, rerendered.layers[0].visible)
        assertEquals(128, rerendered.layers[3].opacity)
    }

    @Test
    fun `art colours follow a reordered palette`() {
        val saved = painted(HandoffMerge.merge(null, render()), frame = 0)

        val rerendered = HandoffMerge.merge(saved, render(palette = Palette(listOf(red, white, black))))

        // Red was entry 2 and is now entry 1.
        assertEquals(1, rerendered.frames[0].cels.last().image[0, 0])
        assertEquals(red, rerendered.palette[rerendered.frames[0].cels.last().image[0, 0]])
        assertEquals(0, rerendered.frames[0].cels.last().image[1, 0])
    }

    @Test
    fun `a palette that lost an art colour is refused`() {
        val saved = painted(HandoffMerge.merge(null, render()), frame = 0)

        val error = assertFailsWith<IllegalArgumentException> {
            HandoffMerge.merge(saved, render(palette = Palette(listOf(black, white)), draftLabel = 1))
        }

        assertContains(error.message.orEmpty(), "#c81e1e")
    }

    @Test
    fun `fewer key frames may drop empty art but not painted art`() {
        val painted = painted(HandoffMerge.merge(null, render(frames = 3)), frame = 2)

        val error = assertFailsWith<IllegalArgumentException> { HandoffMerge.merge(painted, render(frames = 2)) }

        assertContains(error.message.orEmpty(), "Frame 3 holds art")
        val unpainted = painted(HandoffMerge.merge(null, render(frames = 3)), frame = 1)
        assertEquals(2, HandoffMerge.merge(unpainted, render(frames = 2)).frames.size)
    }

    @Test
    fun `a changed canvas size is refused`() {
        val saved = HandoffMerge.merge(null, render())

        val bigger = HandoffRender(palette, 5, 3, listOf(HandoffFrame(100, IndexedRaster(5, 3), IndexedRaster(5, 3))))

        assertFailsWith<IllegalArgumentException> { HandoffMerge.merge(saved, bigger) }
    }

    @Test
    fun `a handoff without an art layer gets one`() {
        val first = HandoffMerge.merge(null, render())
        val stripped = first.copy(layers = first.layers.take(2))

        assertEquals(listOf("reference", "draft", "art"), HandoffMerge.merge(stripped, render()).layers.map { it.name })
    }
}
