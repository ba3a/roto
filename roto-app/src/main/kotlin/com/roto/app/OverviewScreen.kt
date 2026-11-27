package com.roto.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roto.app.help.HelpControl
import com.roto.app.help.Helped
import com.roto.core.coverage.CoverageState

private val LABEL_WIDTH = 100.dp
private val CELL_WIDTH = 150.dp

/** How a cell's [state] reads: its name, with the count of frames painted for a handoff in progress. */
internal fun stateLabel(state: CoverageState): String =
    when (state) {
        CoverageState.NotFilmed -> "Not filmed"
        CoverageState.Mirrored -> "Mirrored"
        CoverageState.Model -> "Model"
        CoverageState.Setup -> "Setup"
        is CoverageState.Painting -> "Painting ${state.painted}/${state.total}"
        CoverageState.Finished -> "Finished"
        CoverageState.Published -> "Published"
        is CoverageState.OutOfDate -> "Out of date"
        is CoverageState.Unreadable -> "Unreadable"
    }

/** The line above the grid: what to do [next], or why there is nothing to name yet. */
internal fun nextLabel(state: OverviewState): String {
    val next = state.next
    return when {
        state.matrix == null -> "Next: reading the profile…"
        next == null -> "Everything is published"
        else -> "Next: ${next.action} · ${next.facing} (${stateLabel(next.state)})"
    }
}

/** One line for each cell that cannot be read: its action and facing, and why. */
internal fun problems(state: OverviewState): List<String> =
    state.actions.flatMap { action -> state.facings.map { facing -> action to facing } }
        .mapNotNull { (action, facing) ->
            val cell = state.cell(action, facing) as? OverviewCell.Ready
            (cell?.cell?.state as? CoverageState.Unreadable)?.let { "$action · $facing: ${it.reason}" }
        }

/** The profile's action by facing grid and the Next line above it; the cells read as [model] loads them. */
@Composable
fun OverviewScreen(model: OverviewModel) {
    LaunchedEffect(model) { model.load() }
    val state = model.state
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Overview", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(nextLabel(state), style = MaterialTheme.typography.bodyLarge)
            Helped(HelpControl.OVERVIEW_NEXT) {
                Button(onClick = model::openNext, enabled = state.next != null) { Text("Open Next") }
            }
        }
        Row {
            Text("", Modifier.width(LABEL_WIDTH))
            state.facings.forEach { Text(it, Modifier.width(CELL_WIDTH), style = MaterialTheme.typography.labelLarge) }
        }
        state.actions.forEach { action ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(action, Modifier.width(LABEL_WIDTH), style = MaterialTheme.typography.labelLarge)
                state.facings.forEach { facing -> Cell(model, action, facing) }
            }
        }
        problems(state).forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Cell(model: OverviewModel, action: String, facing: String) {
    val ready = model.state.cell(action, facing) as? OverviewCell.Ready
    Helped(HelpControl.OVERVIEW_CELL) {
        OutlinedButton(onClick = { model.open(action, facing) }, Modifier.width(CELL_WIDTH), enabled = ready != null) {
            Text(
                when (ready) {
                    null -> "…"
                    else -> stateLabel(ready.cell.state) + if (ready.cell.hasModel) " · M" else ""
                },
                maxLines = 1,
            )
        }
    }
}
