package com.roto.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roto.app.help.HelpControl
import com.roto.app.help.Helped
import kotlinx.coroutines.launch

/**
 * Changes how the watched clip is published without touching its handoff: its speed and how many of
 * its frames it keeps. The numbers and the player follow every change; Save stores it in the profile.
 */
@Composable
internal fun RetimeSection(model: RetimeModel, rampColours: List<Int>, onSave: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    val retime = model.retime
    LaunchedEffect(model, retime) { model.preview() }
    Section("Retime") {
        Radius(
            "Retime speed",
            retime.speedPercent,
            RETIME_SPEEDS.last,
            RETIME_SPEEDS.first,
            HelpControl.RETIME_SPEED,
        ) { model.setSpeed(it) }
        Radius("Use frames", model.frameCount, model.totalFrames, 1, HelpControl.RETIME_FRAMES) { model.useFrames(it) }
        Text(
            "${frameCount(model.frameCount)}, ${seconds(model.totalMs)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Helped(HelpControl.RETIME_RESET) { OutlinedButton(onClick = model::reset) { Text("Reset retime") } }
            Helped(HelpControl.RETIME_SAVE) {
                Button(onClick = { scope.launch { onSave() } }, enabled = model.changed) { Text("Save retime") }
            }
        }
        Column(Modifier.fillMaxWidth()) {
            FlipbookPane(model.player, "Retimed", false, rampColours, onClose = null)
        }
    }
}
