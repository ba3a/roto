package com.roto.cli

import com.roto.core.coverage.CoverageCell
import com.roto.core.coverage.CoverageMatrix
import com.roto.core.coverage.CoverageState

/**
 * Prints a [CoverageMatrix] as text: one row per action, one column per facing, a state label in each cell
 * (with [MODEL_MARK] after it when a model exists), then a `Next:` line or [DONE]. It decides nothing:
 * the states and the next cell come from `roto-core`.
 */
internal object StatusFormatter {

    /** Shown after a cell's label when a model clip exists for it. */
    const val MODEL_MARK = "[model]"

    /** The last line when nothing is left to do. */
    const val DONE = "Everything is published"

    private const val GAP = 2

    /** The matrix as lines of text, without a trailing newline. */
    fun format(matrix: CoverageMatrix): String {
        val header = listOf("") + matrix.facings
        val rows = matrix.actions.map { action ->
            listOf(action) + matrix.facings.map { cellText(matrix.cell(action, it)) }
        }
        val table = listOf(header) + rows
        val widths = header.indices.map { column -> table.maxOf { it[column].length } }
        val lines = table.map { row ->
            row.indices.joinToString(" ".repeat(GAP)) { row[it].padEnd(widths[it]) }.trimEnd()
        }
        val next = matrix.next
        val last = if (next == null) DONE else "Next: ${next.action} ${next.facing} (${label(next.state)})"
        return (lines + "" + last).joinToString("\n")
    }

    private fun cellText(cell: CoverageCell): String =
        if (cell.hasModel) "${label(cell.state)} $MODEL_MARK" else label(cell.state)

    private fun label(state: CoverageState): String = when (state) {
        CoverageState.NotFilmed -> "not filmed"
        CoverageState.Mirrored -> "mirrored"
        CoverageState.Model -> "model only"
        CoverageState.Setup -> "setup"
        is CoverageState.Painting -> "painting ${state.painted}/${state.total}"
        CoverageState.Finished -> "finished"
        CoverageState.Published -> "published"
        is CoverageState.OutOfDate -> "out of date: ${state.reason}"
        is CoverageState.Unreadable -> "unreadable: ${state.reason}"
    }
}
