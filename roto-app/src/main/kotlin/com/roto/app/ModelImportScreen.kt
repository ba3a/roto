package com.roto.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roto.app.help.HelpControl
import com.roto.app.help.Helped
import com.roto.core.model.ModelSourceKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private val BETWEEN = 12.dp
private val DURATION_WIDTH = 120.dp
private const val DIGITS_MAX = 7

/** Imports finished animations as models: pick a source, check what it holds, file each row, import. */
@Composable
fun ModelImportScreen(model: ModelImportModel, pickers: Pickers, onClose: () -> Unit) {
    val state = model.state
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(BETWEEN)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Import models", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Helped(HelpControl.MODEL_CLOSE) {
                TextButton(onClick = onClose, enabled = !state.importing) { Text("Close") }
            }
        }
        SourceBar(model, pickers, scope)
        if (state.kind == ModelSourceKind.ATLAS) {
            Helped(HelpControl.MODEL_PATTERN) {
                OutlinedTextField(
                    value = state.pattern,
                    onValueChange = { scope.launch { model.setPattern(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Atlas name pattern") },
                )
            }
        }
        if (state.skipped.isNotEmpty()) {
            Text(
                "Skipped, matching no pattern: ${state.skipped.joinToString()}",
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Rows(model, Modifier.weight(1f))
        state.message?.let { SelectableMessage(it) }
        ImportBar(model, scope, onClose)
    }
}

/** The source picked, the buttons that pick one, and the duration for frames the source does not time. */
@Composable
private fun SourceBar(model: ModelImportModel, pickers: Pickers, scope: CoroutineScope) {
    val state = model.state
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BETWEEN)) {
        Text(
            state.source?.toString() ?: "No source chosen",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.kind != null && state.kind != ModelSourceKind.ASE) {
            Helped(HelpControl.MODEL_DURATION) {
                OutlinedTextField(
                    value = state.durationMs.toString(),
                    onValueChange = { text -> text.toIntOrNull()?.let { scope.launch { model.setDuration(it) } } },
                    modifier = Modifier.width(DURATION_WIDTH),
                    singleLine = true,
                    label = { Text("Frame ms") },
                )
            }
        }
        Helped(HelpControl.MODEL_CHOOSE_FILE) {
            OutlinedButton(
                onClick = { pickers.modelFile("Choose animation file")?.let { scope.launch { model.choose(it) } } },
                enabled = !state.importing,
            ) { Text("Choose file…") }
        }
        Helped(HelpControl.MODEL_CHOOSE_FOLDER) {
            OutlinedButton(
                onClick = {
                    pickers.folder("Choose frames folder", PickerCategory.FRAMES)
                        ?.let { scope.launch { model.choose(it) } }
                },
                enabled = !state.importing,
            ) { Text("Choose folder…") }
        }
    }
}

/** One editable row for each animation the source holds. */
@Composable
private fun Rows(model: ModelImportModel, modifier: Modifier) {
    val state = model.state
    if (state.rows.isEmpty()) {
        Text(
            "Choose an .ase file, an .atlas file or a folder of PNG frames.",
            modifier,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(BETWEEN)) {
        itemsIndexed(state.rows, key = { _, row -> row.row.id }) { index, row -> RowEditor(model, index, row) }
    }
}

@Composable
private fun RowEditor(model: ModelImportModel, index: Int, row: ModelImportRow) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BETWEEN)) {
            Text("${row.row.id} · ${frameCount(row.row.frameCount)}", Modifier.weight(1f))
            Choice(
                "Action",
                row.action ?: "choose",
                model.actions,
                help = HelpControl.MODEL_ROW_ACTION,
            ) { model.setAction(index, it) }
            Choice(
                "Facing",
                row.facing ?: "choose",
                model.facings,
                help = HelpControl.MODEL_ROW_FACING,
            ) { model.setFacing(index, it) }
            Helped(HelpControl.MODEL_ROW_DURATION) {
                OutlinedTextField(
                    value = row.durationMs?.toString().orEmpty(),
                    onValueChange = { model.setRowDuration(index, it.take(DIGITS_MAX).toIntOrNull()) },
                    modifier = Modifier.width(DURATION_WIDTH),
                    singleLine = true,
                    label = { Text("Frame ms") },
                    placeholder = { Text("source") },
                )
            }
            Helped(HelpControl.MODEL_LOOP) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = row.loop, onCheckedChange = { model.setLoop(index, it) })
                    Text("Loop")
                }
            }
        }
        model.problem(row)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (model.replaces(row)) {
            Text("Replaces the model ${row.key}", color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

/** The replace confirmation, and the button that imports. */
@Composable
private fun ImportBar(model: ModelImportModel, scope: CoroutineScope, onClose: () -> Unit) {
    val state = model.state
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BETWEEN)) {
        if (model.needsConfirmation) {
            Helped(HelpControl.MODEL_REPLACE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.confirmedReplace, onCheckedChange = model::confirmReplace)
                    Text("Replace existing models")
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Helped(HelpControl.MODEL_IMPORT) {
            Button(
                onClick = { scope.launch { if (model.import()) onClose() } },
                enabled = model.canImport,
            ) { Text("Import") }
        }
    }
}
