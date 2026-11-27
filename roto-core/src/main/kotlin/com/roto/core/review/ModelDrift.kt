package com.roto.core.review

import com.roto.core.image.Raster
import com.roto.core.model.ModelMeasure

/**
 * Where a clip's painted figure differs from its model: body height, feet row, root x, frame count and
 * duration total. The clip is measured as the model is, so the numbers compare. One finding per
 * differing quantity, on the first frame, naming the clip's number and then the model's. The root x
 * is the lower median of the frames' roots. Nothing is found for a clip with no model, and a
 * quantity a clip cannot measure (no figure, or no durations given) is left out.
 */
object ModelDrift : LintRule {
    override val name = "ModelDrift"

    override fun check(frames: List<Raster>, context: LintContext): List<LintFinding> {
        val model = context.model ?: return emptyList()
        val clip = ModelMeasure.measure(frames)
        val quantities = listOf(
            Quantity("Body height", clip.bodyHeight, model.measurement.bodyHeight),
            Quantity("Feet row", clip.feetRow, model.measurement.feetRow),
            Quantity("Root x", rootX(clip.frames.map { it.rootX }), rootX(model.measurement.frames.map { it.rootX })),
        ) + durations(context.clipDurationsMs, model.durationsMs)
        return quantities.mapNotNull { q ->
            if (q.clip != null && q.model != null && q.clip != q.model) {
                LintFinding(name, 0, null, "${q.label} is ${q.clip}, the model's is ${q.model}")
            } else {
                null
            }
        }
    }

    /** The frame count and duration total, left out when the context gives no durations for the clip. */
    private fun durations(clip: List<Int>, model: List<Int>): List<Quantity> =
        if (clip.isEmpty()) {
            emptyList()
        } else {
            listOf(
                Quantity("Frame count", clip.size, model.size),
                Quantity("Duration total", clip.sum(), model.sum()),
            )
        }

    private class Quantity(val label: String, val clip: Int?, val model: Int?)

    private fun rootX(roots: List<Int?>): Int? = roots.filterNotNull().sorted().let { it.getOrNull((it.size - 1) / 2) }
}
