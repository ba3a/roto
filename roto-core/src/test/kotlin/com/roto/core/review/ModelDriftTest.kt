package com.roto.core.review

import com.roto.core.framebox.DepthSettings
import com.roto.core.image.Argb
import com.roto.core.image.Raster
import com.roto.core.model.ModelMeasure
import com.roto.core.palette.Palette
import com.roto.core.palette.RampSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelDriftTest {
    private val ink = Argb.of(200, 50, 50)

    /** A 20 x 30 frame holding a 3 wide figure from row [top] to row [bottom], its left edge at [left]. */
    private fun figure(top: Int = 4, bottom: Int = 19, left: Int = 4): Raster {
        val raster = Raster(20, 30)
        for (y in top..bottom) for (x in left until left + 3) raster[x, y] = ink
        return raster
    }

    private fun context(
        model: List<Raster>? = listOf(figure(), figure()),
        modelDurations: List<Int> = listOf(100, 100),
        clipDurations: List<Int> = listOf(100, 100),
    ) = LintContext(
        Palette(listOf(ink)),
        RampSet(ramps = emptyList()),
        false,
        DepthSettings(),
        model = model?.let { ModelReference(ModelMeasure.measure(it), modelDurations) },
        clipDurationsMs = clipDurations,
    )

    private fun found(clip: List<Raster> = listOf(figure(), figure()), context: LintContext = context()) =
        ModelDrift.check(clip, context).map { it.message }

    @Test
    fun `a clip equal to its model is clean`() {
        assertTrue(found().isEmpty())
    }

    @Test
    fun `body height differs and names both numbers`() {
        assertEquals(listOf("Body height is 14, the model's is 16"), found(listOf(figure(top = 6), figure(top = 6))))
    }

    @Test
    fun `feet row differs and names both numbers`() {
        val clip = listOf(figure(top = 6, bottom = 21), figure(top = 6, bottom = 21))
        assertEquals(listOf("Feet row is 21, the model's is 19"), found(clip))
    }

    @Test
    fun `root x differs and names both numbers`() {
        val message = found(listOf(figure(left = 8), figure(left = 8))).single()
        assertEquals("Root x is 9, the model's is 5", message)
    }

    @Test
    fun `frame count differs and names both numbers`() {
        val message = found(context = context(clipDurations = listOf(100, 100, 100)))
        assertEquals(listOf("Frame count is 3, the model's is 2", "Duration total is 300, the model's is 200"), message)
    }

    @Test
    fun `duration total differs with the same frame count`() {
        assertEquals(
            listOf("Duration total is 160, the model's is 200"),
            found(context = context(clipDurations = listOf(80, 80))),
        )
    }

    @Test
    fun `each quantity alone is reported and the others stay quiet`() {
        val same = listOf(100, 100)
        assertEquals(
            listOf("Body height is 14, the model's is 16"),
            found(listOf(figure(top = 6), figure(top = 6)), context(clipDurations = same)),
        )
        assertEquals(
            listOf("Feet row is 21, the model's is 19"),
            found(listOf(figure(top = 6, bottom = 21), figure(top = 6, bottom = 21))),
        )
        assertEquals(listOf("Root x is 9, the model's is 5"), found(listOf(figure(left = 8), figure(left = 8))))
        assertEquals(
            listOf("Duration total is 160, the model's is 200"),
            found(context = context(clipDurations = listOf(60, 100))),
        )
        assertEquals(
            listOf("Frame count is 3, the model's is 2"),
            found(context = context(modelDurations = listOf(100, 200), clipDurations = listOf(100, 100, 100))),
        )
    }

    @Test
    fun `no durations given leaves the frame count and duration total unchecked`() {
        assertTrue(found(context = context(clipDurations = emptyList())).isEmpty())
    }

    @Test
    fun `a clip with no model has no finding`() {
        assertTrue(found(listOf(figure(top = 9)), context(model = null)).isEmpty())
    }

    @Test
    fun `the finding is on the first frame and has no pixel`() {
        val finding = ModelDrift.check(listOf(figure(left = 8)), context()).single()
        assertEquals(LintFinding("ModelDrift", 0, null, finding.message), finding)
    }
}
