package com.roto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roto.core.review.LintFinding
import com.roto.core.review.LintReport

/** Whether the lint has a report to list. */
sealed interface LintLoad {
    /** The art is being checked. */
    data object Loading : LintLoad

    /**
     * The art is checked.
     *
     * @property report what the rules found
     */
    class Ready(val report: LintReport) : LintLoad

    /**
     * The handoff cannot be read, so the lint is off.
     *
     * @property reason why, as the artist reads it
     */
    data class Disabled(val reason: String) : LintLoad
}

/**
 * The lint panel's state: a clip's [LintReport], the finding the artist picked, and the player it points
 * at. Picking a finding moves [flipbook] to its frame and marks its pixel. The lint only reads: it never
 * changes the art. [mirrored] says the player shows the clip flipped, so a pixel's column is flipped too.
 */
class LintModel(private val flipbook: FlipbookModel, private val mirrored: Boolean = false) {
    /** Whether the lint is running, ready or off. */
    var load: LintLoad by mutableStateOf(LintLoad.Loading)
        private set

    /** The finding the artist picked, or null. */
    var selected: LintFinding? by mutableStateOf(null)
        private set

    /** How many findings each rule made, in rule order; empty until the report is ready. */
    val summary: List<Pair<String, Int>>
        get() = (load as? LintLoad.Ready)?.report?.counts?.entries?.map { it.key to it.value }.orEmpty()

    /** The frames that have no art yet and were not checked, zero-based. */
    val skipped: List<Int> get() = (load as? LintLoad.Ready)?.report?.skipped.orEmpty()

    /**
     * Lints through [check] off the UI thread. A handoff that cannot be read turns the lint off with the
     * reason.
     */
    suspend fun run(check: () -> LintReport) {
        load = LintLoad.Loading
        selected = null
        var problem: String? = null
        val report = offUiThread({ problem = it }) { check() }
        load = if (report == null) {
            LintLoad.Disabled(problem ?: "The handoff cannot be read")
        } else {
            LintLoad.Ready(report)
        }
    }

    /** Picks [finding]: the player shows its frame and marks its pixel. */
    fun select(finding: LintFinding) {
        val ready = load as? LintLoad.Ready ?: return
        if (finding !in ready.report.findings) return
        selected = finding
        val pixel = finding.pixel?.let { p ->
            val width = flipbook.frame?.image?.width ?: return@let p
            if (mirrored) LintFinding.Pixel(width - 1 - p.x, p.y) else p
        }
        flipbook.show(finding.frame, pixel)
    }
}
