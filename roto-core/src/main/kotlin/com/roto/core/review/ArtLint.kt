package com.roto.core.review

import com.roto.core.image.Raster

/** Runs the lint rules over a clip's art. */
object ArtLint {
    /** Every rule, in the order reports list them. */
    val rules: List<LintRule> = listOf(OffPalette, StrayPixel, OutlineGap, GroundDrift, Flicker, ModelDrift, SeamRule)

    /**
     * Lints [frames], one per handoff frame, null where the frame has no art yet. Only painted frames
     * are checked; the others are listed as skipped.
     */
    fun run(frames: List<Raster?>, context: LintContext): LintReport {
        val painted = frames.indices.filter { frames[it] != null }
        val rasters = painted.map { requireNotNull(frames[it]) }
        if (rasters.isEmpty()) return LintReport(emptyList(), rules.associate { it.name to 0 }, frames.indices.toList())
        val order = rules.withIndex().associate { it.value.name to it.index }
        val findings = rules
            .flatMap { rule -> rule.check(rasters, context) }
            .map { it.copy(frame = painted[it.frame]) }
            .sortedWith(
                compareBy<LintFinding>(
                    { it.frame },
                    { order.getValue(it.rule) },
                    { it.pixel?.y ?: -1 },
                    { it.pixel?.x ?: -1 },
                ),
            )
        val counts = rules.associate { rule -> rule.name to findings.count { it.rule == rule.name } }
        return LintReport(findings, counts, frames.indices.filter { frames[it] == null })
    }
}
