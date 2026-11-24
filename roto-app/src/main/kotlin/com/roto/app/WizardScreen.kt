package com.roto.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.roto.core.preset.LibraryPreset

private val BETWEEN = 12.dp
private val PREVIEW_SIZE = 320.dp
private const val FIGURE_HEADS = 1.8f
private const val FIGURE_SHARE = 0.75f

/**
 * The project wizard: a header with the step, the step's controls, the message, and Back / Next. The
 * model decides what is valid; the screen only shows it. [onCancel] leaves the wizard.
 */
@Composable
fun WizardScreen(model: WizardModel, pickers: Pickers, onCancel: () -> Unit) {
    val state = model.state
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(BETWEEN)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val position = "Step ${state.step.ordinal + 1} of ${WizardStep.entries.size}"
            val title = "${stepTitle(state.step)} ($position)"
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            when (state.step) {
                WizardStep.Preset -> PresetStep(model)
                WizardStep.Name -> NameStep(model, pickers)
                WizardStep.Animation -> AnimationStep(model)
                WizardStep.Canvas -> CanvasStep(model)
                WizardStep.Colours, WizardStep.Output, WizardStep.Review -> Unit // their screens land in later steps
            }
        }
        state.message?.let { SelectableMessage(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(BETWEEN)) {
            OutlinedButton(onClick = model::back, enabled = state.step != WizardStep.Preset) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Button(onClick = model::next, enabled = model.canNext && state.step != WizardStep.Review) { Text("Next") }
        }
    }
}

private fun stepTitle(step: WizardStep): String =
    when (step) {
        WizardStep.Preset -> "Preset"
        WizardStep.Name -> "Name and folder"
        WizardStep.Animation -> "Animation"
        WizardStep.Canvas -> "Canvas"
        WizardStep.Colours -> "Colours"
        WizardStep.Output -> "Output"
        WizardStep.Review -> "Review"
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PresetStep(model: WizardModel) {
    val chosen = model.state.preset
    Section("Start from") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            model.presets.forEach { entry ->
                FilterChip(
                    selected = entry.id == chosen?.id,
                    onClick = { model.choosePreset(entry) },
                    label = { Text(entry.preset.name) },
                )
            }
        }
    }
    chosen?.let { PresetSummary(it) }
}

@Composable
private fun PresetSummary(entry: LibraryPreset) {
    val p = entry.preset
    Column(Modifier.padding(top = BETWEEN), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(p.description)
        Text("Canvas: ${p.canvas.width} x ${p.canvas.height}, ground row ${p.canvas.groundRow}")
        Text("Facings: ${p.facings.joinToString()}")
        Text("Actions: ${p.actions.joinToString()}")
        Text("Palette: ${p.palette.colors.size} colours")
    }
}

@Composable
private fun NameStep(model: WizardModel, pickers: Pickers) {
    val state = model.state
    Column(verticalArrangement = Arrangement.spacedBy(BETWEEN)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BETWEEN)) {
            OutlinedButton(
                onClick = { pickers.folder("Project folder", state.folder)?.let(model::chooseFolder) },
            ) { Text("Choose folder…") }
            Text(state.folder?.toString() ?: "No folder chosen")
        }
        OutlinedTextField(
            value = state.id,
            onValueChange = model::editId,
            label = { Text("Project id") },
            singleLine = true,
        )
        state.existingProject?.let { Text("This folder is already a Roto project.") }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AnimationStep(model: WizardModel) {
    val state = model.state
    var selected by remember { mutableStateOf(emptyList<String>()) }
    var text by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(BETWEEN)) {
        Section("Facings (select two to pair them)") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.facings.forEach { facing ->
                    FilterChip(
                        selected = facing in selected,
                        onClick = { selected = toggled(selected, facing) },
                        label = { Text(facing) },
                    )
                }
            }
            state.mirrorPairs.forEach { pair ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${pair.first} mirrors ${pair.second}")
                    TextButton(onClick = { model.unpairFacings(pair.first, pair.second) }) { Text("Unpair") }
                }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Name for add or rename") },
            singleLine = true,
        )
        FacingButtons(model, selected, text) { selected = emptyList() }
        Section("Actions") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.actions.forEach { action ->
                    FilterChip(
                        selected = action in selected,
                        onClick = { selected = if (action in selected) emptyList() else listOf(action) },
                        label = { Text(action) },
                    )
                }
            }
        }
        ActionButtons(model, selected, text) { selected = emptyList() }
    }
}

/** [selected] with [name] added, or removed if it was in; at most the last two stay. */
private fun toggled(selected: List<String>, name: String): List<String> =
    if (name in selected) selected - name else (selected + name).takeLast(2)

@Composable
private fun FacingButtons(model: WizardModel, selected: List<String>, text: String, clear: () -> Unit) {
    val facings = selected.filter { it in model.state.facings }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { model.addFacing(text) }, enabled = text.isNotBlank()) { Text("Add facing") }
        OutlinedButton(
            onClick = { model.renameFacing(facings.single(), text); clear() },
            enabled = facings.size == 1 && text.isNotBlank(),
        ) { Text("Rename facing") }
        OutlinedButton(
            onClick = { facings.forEach(model::removeFacing); clear() },
            enabled = facings.isNotEmpty(),
        ) { Text("Remove facing") }
        OutlinedButton(
            onClick = { model.pairFacings(facings[0], facings[1]); clear() },
            enabled = facings.size == 2,
        ) { Text("Pair facings") }
    }
}

@Composable
private fun ActionButtons(model: WizardModel, selected: List<String>, text: String, clear: () -> Unit) {
    val actions = selected.filter { it in model.state.actions }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { model.addAction(text) }, enabled = text.isNotBlank()) { Text("Add action") }
        OutlinedButton(
            onClick = { model.renameAction(actions.single(), text); clear() },
            enabled = actions.size == 1 && text.isNotBlank(),
        ) { Text("Rename action") }
        OutlinedButton(
            onClick = { actions.forEach(model::removeAction); clear() },
            enabled = actions.isNotEmpty(),
        ) { Text("Remove action") }
    }
}

@Composable
private fun CanvasStep(model: WizardModel) {
    val state = model.state
    Row(horizontalArrangement = Arrangement.spacedBy(BETWEEN)) {
        Column(Modifier.width(200.dp), verticalArrangement = Arrangement.spacedBy(BETWEEN)) {
            NumberField("Width", state.width, model::editWidth)
            NumberField("Height", state.height, model::editHeight)
            NumberField("Ground row", state.groundRow, model::editGroundRow)
        }
        CanvasPreview(state.width, state.height, state.groundRow)
    }
}

/** A whole-number field; text that is not a number is kept while typing but changes nothing. */
@Composable
private fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            text = typed
            typed.trim().toIntOrNull()?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
    )
}

/** The canvas as a pixel grid, its ground row, and a figure 1.8 heads tall to judge the scale. */
@Composable
private fun CanvasPreview(width: Int, height: Int, groundRow: Int) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val ground = MaterialTheme.colorScheme.primary
    val figure = MaterialTheme.colorScheme.tertiary
    Canvas(Modifier.width(PREVIEW_SIZE).height(PREVIEW_SIZE)) {
        if (width < 1 || height < 1) return@Canvas
        val cell = minOf(size.width / width, size.height / height)
        drawGrid(width, height, cell, line)
        drawRect(Color.Gray, Offset.Zero, Size(width * cell, height * cell), style = Stroke(1f))
        val floor = (groundRow + 1) * cell
        drawLine(ground, Offset(0f, floor), Offset(width * cell, floor), strokeWidth = 2f)
        val body = (groundRow + 1) * cell * FIGURE_SHARE
        val head = body / FIGURE_HEADS
        val left = (width * cell - head) / 2
        drawRect(figure, Offset(left, floor - body), Size(head, head), style = Stroke(2f))
        drawRect(figure, Offset(left, floor - body + head), Size(head, body - head), style = Stroke(2f))
    }
}

private fun DrawScope.drawGrid(width: Int, height: Int, cell: Float, colour: Color) {
    for (x in 1 until width) drawLine(colour, Offset(x * cell, 0f), Offset(x * cell, height * cell))
    for (y in 1 until height) drawLine(colour, Offset(0f, y * cell), Offset(width * cell, y * cell))
}
