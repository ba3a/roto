package com.roto.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roto.app.help.HelpControl
import com.roto.app.help.Helped
import com.roto.core.palette.HexColor
import com.roto.core.palette.Ramp

private val SPACE = 12.dp
private val SWATCH = 24.dp
private val SMALL_SWATCH = 16.dp
private const val DARKEST = "Each ramp's darkest"

@Composable
internal fun ColoursStep(model: WizardModel, pickers: Pickers) {
    val state = model.state
    Column(verticalArrangement = Arrangement.spacedBy(SPACE)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SPACE)) {
            Helped(HelpControl.IMPORT_PALETTE) {
                OutlinedButton(onClick = { pickers.palette("Palette file")?.let(model::importPalette) }) {
                    Text("Import palette…")
                }
            }
            Text("${state.palette?.colors?.size ?: 0} colours")
        }
        state.palette?.let { palette ->
            RampBuilder(model, palette.colors)
            OutlineChoice(model, palette.colors)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RampBuilder(model: WizardModel, colours: List<Int>) {
    var picked by remember { mutableStateOf(emptyList<Int>()) }
    var name by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    Section("Ramps: pick colours, then name the ramp") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            colours.forEach { colour ->
                Helped(HelpControl.SWATCH) {
                    Swatch(colour, SWATCH, colour in picked) {
                        picked = if (colour in picked) picked - colour else picked + colour
                    }
                }
            }
        }
        Helped(HelpControl.RAMP_NAME) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Ramp name") },
                singleLine = true,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val target = editing
            Helped(if (target == null) HelpControl.ADD_RAMP else HelpControl.UPDATE_RAMP) {
                OutlinedButton(
                    onClick = {
                        if (target == null) model.addRamp(name, picked) else model.editRamp(target, name, picked)
                        picked = emptyList()
                        name = ""
                        editing = null
                    },
                    enabled = picked.isNotEmpty() && name.isNotBlank(),
                ) { Text(if (target == null) "Add ramp" else "Update ramp") }
            }
            Helped(HelpControl.SUGGEST_RAMPS) {
                OutlinedButton(onClick = model::suggestRamps) { Text("Suggest ramps") }
            }
            Helped(HelpControl.CLEAR_RAMP) {
                TextButton(
                    onClick = {
                        picked = emptyList()
                        name = ""
                        editing = null
                    },
                    enabled = picked.isNotEmpty() || name.isNotEmpty(),
                ) { Text("Clear") }
            }
        }
        RampRows(model) { ramp ->
            editing = ramp.name
            name = ramp.name
            picked = ramp.colors
        }
    }
}

@Composable
private fun RampRows(model: WizardModel, onEdit: (Ramp) -> Unit) {
    model.state.ramps.forEach { ramp ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ramp.name)
            ramp.colors.forEach { Swatch(it, SMALL_SWATCH, false) {} }
            Helped(HelpControl.EDIT_RAMP) { TextButton(onClick = { onEdit(ramp) }) { Text("Edit") } }
            Helped(HelpControl.REMOVE_RAMP) { TextButton(onClick = { model.removeRamp(ramp.name) }) { Text("Remove") } }
        }
    }
}

@Composable
private fun OutlineChoice(model: WizardModel, colours: List<Int>) {
    val outline = model.state.outline
    val options = listOf(DARKEST) + colours.map(HexColor::format)
    Choice("Outline", outline?.let(HexColor::format) ?: DARKEST, options, help = HelpControl.OUTLINE) { option ->
        model.chooseOutline(if (option == DARKEST) null else HexColor.parse(option))
    }
}

@Composable
private fun Swatch(colour: Int, size: Dp, selected: Boolean, onClick: () -> Unit) {
    val edge = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier.size(size).background(Color(colour)).border(if (selected) 3.dp else 1.dp, edge)
            .clickable(onClick = onClick),
    )
}
