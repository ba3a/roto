package com.roto.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.roto.app.help.HelpControl
import com.roto.app.help.Helped
import com.roto.core.palette.HexColor
import com.roto.core.preset.OutputLayout

private val GAP = 12.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun OutputStep(model: WizardModel, pickers: Pickers) {
    val state = model.state
    var advanced by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
        Section("Published frames go under") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GAP)) {
                Helped(HelpControl.CHOOSE_OUTPUT_FOLDER) {
                    OutlinedButton(
                        onClick = {
                            pickers.folder("Output folder", PickerCategory.OUTPUT, state.folder)
                                ?.let(model::chooseOutputFolder)
                        },
                        enabled = state.folder != null,
                    ) { Text("Choose folder…") }
                }
                Helped(HelpControl.OUTPUT_FOLDER) {
                    OutlinedTextField(
                        value = state.outputFolder,
                        onValueChange = model::editOutputFolder,
                        label = { Text("Folder inside the project") },
                        singleLine = true,
                    )
                }
            }
        }
        Section("Layout") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutputLayout.entries.forEach { layout ->
                    Helped(HelpControl.LAYOUT) {
                        FilterChip(
                            selected = layout == state.layout,
                            onClick = { model.chooseLayout(layout) },
                            label = { Text(layoutLabel(layout)) },
                        )
                    }
                }
            }
        }
        Helped(HelpControl.ADVANCED) {
            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide advanced" else "Advanced") }
        }
        if (advanced) AdvancedFields(model)
    }
}

private fun layoutLabel(layout: OutputLayout): String =
    when (layout) {
        OutputLayout.ONE_FOLDER_PER_CLIP -> "One folder per clip"
        OutputLayout.ONE_FOLDER_PER_PROFILE -> "One folder per profile"
    }

@Composable
private fun AdvancedFields(model: WizardModel) {
    val state = model.state
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TemplateField("Output template", state.output, HelpControl.OUTPUT_TEMPLATE, model::editOutputTemplate)
        TemplateField("Manifest template", state.manifest, HelpControl.MANIFEST_TEMPLATE, model::editManifestTemplate)
        TemplateField(
            "After publish command (one argument per word)",
            state.afterPublish,
            HelpControl.AFTER_PUBLISH,
            model::editAfterPublish,
        )
    }
}

/** A text field that shows what the artist typed, and tells the model its trimmed form. */
@Composable
private fun TemplateField(label: String, value: String, help: HelpControl, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Helped(help) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it)
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun ReviewStep(model: WizardModel, onCreate: () -> Unit) {
    val s = model.state
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Project ${s.id} in ${s.folder}")
        Text("Preset: ${s.preset?.preset?.name.orEmpty()}")
        Text("Facings: ${s.facings.joinToString()}")
        Text("Mirrored: ${s.mirrorPairs.joinToString { "${it.first} and ${it.second}" }.ifEmpty { "none" }}")
        Text("Actions: ${s.actions.joinToString()}")
        Text("Canvas: ${s.width} x ${s.height}, ground row ${s.groundRow}")
        Text("Palette: ${s.palette?.colors?.size ?: 0} colours")
        Text("Ramps: ${s.ramps.joinToString { it.name }}")
        Text("Outline: ${s.outline?.let(HexColor::format) ?: "each ramp's darkest colour"}")
        Text("Published frames: ${s.outputFolder} (${layoutLabel(s.layout)})")
        Text("Output: ${s.output}")
        Text("Manifest: ${s.manifest}")
        Text("After publish: ${s.afterPublish.ifEmpty { "none" }}")
        Section("Files to create") {
            model.files.forEach { Text(it.toString()) }
        }
        Helped(HelpControl.CREATE_PROJECT) {
            Button(onClick = onCreate, modifier = Modifier.padding(top = GAP)) { Text("Create project") }
        }
    }
}
