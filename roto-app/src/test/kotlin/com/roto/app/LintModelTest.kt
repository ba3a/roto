package com.roto.app

import com.roto.core.image.Raster
import com.roto.core.review.Flipbook
import com.roto.core.review.FlipbookFrame
import com.roto.core.review.FlipbookSource
import com.roto.core.review.LintFinding
import com.roto.core.review.LintReport
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LintModelTest {

    private val finding = LintFinding("StrayPixel", 2, LintFinding.Pixel(1, 3), "stray")
    private val report = LintReport(listOf(finding), mapOf("StrayPixel" to 1, "OffPalette" to 0), listOf(1))

    private fun player(): FlipbookModel = FlipbookModel().also { model ->
        val frames = List(3) { FlipbookFrame(Raster.of(8, 8) { _, _ -> 0 }, 100, FlipbookSource.Art) }
        runBlocking { model.open { Flipbook(frames, true) } }
    }

    private fun linted(player: FlipbookModel, mirrored: Boolean = false) =
        LintModel(player, mirrored).also { runBlocking { it.run { report } } }

    @Test
    fun `selecting a finding shows its frame and marks its pixel`() {
        val player = player()
        val model = linted(player)
        model.select(finding)
        assertEquals(finding, model.selected)
        assertEquals(2, player.index)
        assertEquals(LintFinding.Pixel(1, 3), player.marked)
    }

    @Test
    fun `a mirrored player marks the flipped column`() {
        val player = player()
        linted(player, mirrored = true).select(finding)
        assertEquals(LintFinding.Pixel(6, 3), player.marked)
    }

    @Test
    fun `stepping the player clears the mark`() {
        val player = player()
        linted(player).select(finding)
        player.step(1)
        assertNull(player.marked)
    }

    @Test
    fun `the summary and skipped frames come from the report`() {
        val model = linted(player())
        assertEquals(listOf("StrayPixel" to 1, "OffPalette" to 0), model.summary)
        assertEquals(listOf(1), model.skipped)
    }

    @Test
    fun `an unreadable handoff disables the lint with the reason`() {
        val player = player()
        val model = LintModel(player)
        runBlocking { model.run { throw IOException("handoff is damaged") } }
        assertEquals(LintLoad.Disabled("handoff is damaged"), model.load)
        model.select(finding)
        assertNull(model.selected)
        assertNull(player.marked)
        assertIs<LintLoad.Disabled>(model.load)
    }
}
