package com.roto.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.roto.core.profile.Clip
import com.roto.core.publish.ArtProgress
import kotlinx.coroutines.launch

private val BETWEEN = 12.dp

/** What can be done to a listed clip. */
private class HandoffActions(
    val onRender: (Clip) -> Unit,
    val onPaint: (Clip) -> Unit,
    val onMirror: (Clip, Boolean) -> Unit,
)

/**
 * The way out of Roto and back: render the handoffs, paint them in the editor, publish what is done.
 * The handoffs are read again whenever the window's focus changes, since coming back to it is how an
 * artist returns from saving in their editor.
 */
@Composable
fun HandoffScreen(model: HandoffModel, onClose: () -> Unit) {
    val state = model.state
    val scope = rememberCoroutineScope()
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(model, focused) { model.refresh() }
    val idle = state.running == null
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(BETWEEN)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.profileId, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Button(onClick = onClose) { Text("Done") }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        (state.running ?: state.report)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { model.render() } }, enabled = idle) { Text("Render all") }
            Button(onClick = { scope.launch { model.publish() } }, enabled = idle && state.finished.isNotEmpty()) {
                Text("Publish ${state.finished.size} of ${clipCount(state.clips.size)}")
            }
        }
        HorizontalDivider()
        val actions = HandoffActions(
            onRender = { clip -> scope.launch { model.render(listOf(clip)) } },
            onPaint = { clip -> scope.launch { model.paint(clip) } },
            onMirror = { clip, mirror -> scope.launch { model.setMirror(clip, mirror) } },
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(BETWEEN)) {
            items(state.clips, key = { it.clip.id }) { row -> ClipRow(row, idle, actions) }
        }
    }
}

@Composable
private fun ClipRow(row: HandoffClip, idle: Boolean, actions: HandoffActions) {
    val clip = row.clip
    val rendered = row.art != ArtProgress.NoHandoff
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClipProgress(row, Modifier.weight(1f))
            TextButton(onClick = { actions.onRender(clip) }, enabled = idle) {
                Text(if (rendered) "Render again" else "Render")
            }
            OutlinedButton(onClick = { actions.onPaint(clip) }, enabled = idle && rendered) { Text("Open in editor") }
        }
        // A clip already opted in keeps its box even once its pair is filmed, so it can be turned off.
        if (clip.mirror || row.mirrorsInto != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = clip.mirror, onCheckedChange = { actions.onMirror(clip, it) }, enabled = idle)
                Text(
                    row.mirrorsInto?.let { "Also publish mirrored as $it" } ?: "Publish mirrored",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ClipProgress(row: HandoffClip, modifier: Modifier) {
    val colours = MaterialTheme.colorScheme
    Column(modifier) {
        Text("${row.clip.action} · ${row.clip.facing}", style = MaterialTheme.typography.bodyLarge)
        Text(
            describe(row.art),
            style = MaterialTheme.typography.bodySmall,
            color = if (row.art is ArtProgress.Unreadable) colours.error else colours.onSurfaceVariant,
        )
    }
}

private fun describe(art: ArtProgress): String = when (art) {
    ArtProgress.NoHandoff -> "Not rendered yet"
    is ArtProgress.Painted -> "${art.painted} of ${frameCount(art.total)} painted" + if (art.finished) ", ready" else ""
    is ArtProgress.Unreadable -> art.reason
}
