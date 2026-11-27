package com.roto.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roto.app.help.HelpControl
import com.roto.app.help.Helped

private val BETWEEN = 12.dp

/**
 * Makes a variant: the profile to recolour, the ramp swaps, the outline colour and a name, with a clip of
 * the base shown as the swaps would colour it. [onCreated] gets the new profile's id.
 */
@Composable
fun VariantScreen(model: VariantModel, onClose: () -> Unit, onCreated: (String) -> Unit) {
    val state = model.state
    LaunchedEffect(model, state.base, state.swaps, state.outline) { model.refreshPreview() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(BETWEEN),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("New variant", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Helped(HelpControl.VARIANT_CANCEL) { TextButton(onClick = onClose) { Text("Cancel") } }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BETWEEN)) {
            Choice("Base", state.base ?: "none", model.bases, help = HelpControl.VARIANT_BASE) { model.chooseBase(it) }
            Helped(HelpControl.VARIANT_NAME) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = model::rename,
                    singleLine = true,
                    placeholder = { Text("Name of the variant") },
                )
            }
        }
        SwapList(model)
        val outlines = listOf(VariantModel.KEEP_OUTLINE) + model.outlineChoices.map { VariantModel.outlineName(it) }
        Choice(
            "Outline",
            VariantModel.outlineName(state.outline),
            outlines,
            help = HelpControl.VARIANT_OUTLINE,
        ) { choice -> model.chooseOutline(model.outlineChoices.firstOrNull { VariantModel.outlineName(it) == choice }) }
        val ramps = model.colours.ramps.ramps.flatMap { it.colors }
        FlipbookPane(model.player, "Preview", mirrored = false, rampColours = ramps, onClose = null)
        state.message?.let { SelectableMessage(it) }
        model.problem?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Helped(HelpControl.VARIANT_CREATE) {
            Button(onClick = { model.create()?.let(onCreated) }, enabled = model.canCreate) { Text("Create variant") }
        }
    }
}

/** The swaps chosen, each with a button to remove it, and the pair of choices that adds one. */
@Composable
private fun SwapList(model: VariantModel) {
    val names = model.rampNames
    var from by remember(names) { mutableStateOf(names.firstOrNull().orEmpty()) }
    var to by remember(names) { mutableStateOf(names.lastOrNull().orEmpty()) }
    model.state.swaps.forEachIndexed { index, swap ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BETWEEN)) {
            Text("${swap.from} → ${swap.to}", Modifier.weight(1f))
            Helped(HelpControl.VARIANT_REMOVE_SWAP) {
                TextButton(onClick = { model.removeSwap(index) }) { Text("Remove swap") }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BETWEEN),
    ) {
        Choice("From", from, names, help = HelpControl.VARIANT_FROM) { from = it }
        Choice("To", to, names, help = HelpControl.VARIANT_TO) { to = it }
        Helped(HelpControl.VARIANT_ADD_SWAP) {
            OutlinedButton(onClick = { model.addSwap(from, to) }, enabled = names.isNotEmpty()) { Text("Add swap") }
        }
    }
}
