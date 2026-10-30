package com.roto.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val BETWEEN = 12.dp

/** Files one photo as a still: the photo beside the empty wall it is masked against, and where the clip goes. */
@Composable
fun StillScreen(model: StillModel, pickers: Pickers, onClose: () -> Unit) {
    val state = model.state
    LaunchedEffect(model) { model.load() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(BETWEEN)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.photo.fileName.toString(), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onClose, enabled = !state.saving) { Text("Cancel") }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BETWEEN)) {
            Preview(state.picture, "The photo", "Decoding…", Modifier.weight(1f).fillMaxHeight())
            val note = "Choose a photo of the empty wall, taken from the same spot."
            Preview(state.wallPicture, "The empty wall", note, Modifier.weight(1f).fillMaxHeight())
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        model.warning?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
        StillActions(model, pickers, onClose)
    }
}

/** Where the still is filed, the wall it is masked against, and the button that saves it. */
@Composable
private fun StillActions(model: StillModel, pickers: Pickers, onClose: () -> Unit) {
    val state = model.state
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BETWEEN)) {
        Choice("Action", state.action, model.actions, onChoose = model::chooseAction)
        Choice("Facing", state.facing, model.facings, onChoose = model::chooseFacing)
        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = { pickers.photo("Photo of the empty wall")?.let { scope.launch { model.chooseWall(it) } } },
            enabled = !state.saving,
        ) { Text("Choose wall photo…") }
        Button(
            onClick = { scope.launch { if (model.save()) onClose() } },
            enabled = state.wall != null && !state.saving,
        ) { Text("Add still") }
    }
}
