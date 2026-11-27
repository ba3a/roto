package com.roto.cli

import com.roto.core.review.ArtLint
import com.roto.core.review.LintReport

/** What linting one clip came to: a [LintReport], or the reason its handoff could not be linted. */
internal sealed interface ClipCheck {

    /** The clip's id. */
    val clip: String

    /** [clip] was linted; [report] holds what the rules found. */
    data class Checked(override val clip: String, val report: LintReport) : ClipCheck

    /** [clip]'s handoff or its model cannot be read, for this [reason]. */
    data class Unreadable(override val clip: String, val reason: String) : ClipCheck
}

/**
 * Prints the lint of a profile's clips as text: one line per finding, one per unreadable clip, one per
 * clip with unpainted frames, then a per-rule summary. The order is the clips' order, then each report's
 * own; frames are counted from 1, as the editor shows them. It decides nothing: the findings come from
 * `roto-core`.
 */
internal object LintFormatter {

    /** The last line when nothing was found. */
    const val CLEAN = "No findings"

    /** Whether any of [checks] is a finding for the exit code: a rule's finding or an unreadable clip. */
    fun hasFindings(checks: List<ClipCheck>): Boolean = checks.any {
        when (it) {
            is ClipCheck.Checked -> !it.report.clean
            is ClipCheck.Unreadable -> true
        }
    }

    /** The checks as lines of text, without a trailing newline. */
    fun format(checks: List<ClipCheck>): String = (checks.flatMap(::lines) + summary(checks)).joinToString("\n")

    private fun lines(check: ClipCheck): List<String> = when (check) {
        is ClipCheck.Unreadable -> listOf("${check.clip}: unreadable: ${check.reason}")
        is ClipCheck.Checked -> {
            val findings = check.report.findings.map {
                val pixel = it.pixel?.let { p -> " pixel ${p.x},${p.y}" }.orEmpty()
                "${it.rule} ${check.clip} frame ${it.frame + 1}$pixel: ${it.message}"
            }
            val skipped = check.report.skipped.joinToString { (it + 1).toString() }
            val note = "${check.clip}: skipped unpainted frames $skipped"
            if (skipped.isEmpty()) findings else findings + note
        }
    }

    private fun summary(checks: List<ClipCheck>): List<String> {
        val reports = checks.filterIsInstance<ClipCheck.Checked>().map { it.report }
        val unreadable = checks.count { it is ClipCheck.Unreadable }
        if (!hasFindings(checks)) return listOf(CLEAN)
        val rules = ArtLint.rules.map { it.name }.map { name -> name to reports.sumOf { it.counts[name] ?: 0 } }
        return rules.filter { it.second > 0 }.map { "${it.first}: ${it.second}" } +
            if (unreadable > 0) listOf("Unreadable: $unreadable") else emptyList()
    }
}
