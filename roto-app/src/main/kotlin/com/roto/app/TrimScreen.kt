package com.roto.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.roto.core.image.Raster
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val GRIP_WIDTH = 8.dp
private val BETWEEN = 12.dp
private const val PREVIEW_DELAY_MS = 80L
private val OUTSIDE_RANGE = Color.Black.copy(alpha = 0.6f)


/** Trims one video: a preview of the frame under the playhead, the filmstrip below it, and where the clip goes. */
@Composable
fun TrimScreen(model: TrimModel, onClose: () -> Unit) {
    val state = model.state
    LaunchedEffect(model) { model.loadStrip() }
    LaunchedEffect(model, state.playheadMs) {
        delay(PREVIEW_DELAY_MS)
        model.loadPreview()
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(BETWEEN)) {
        TrimHeader(model, onClose)
        Preview(state.preview, Modifier.weight(1f).fillMaxWidth())
        Text(
            "${seconds(state.range.startMs)} to ${seconds(state.range.endMs)}, ${frameCount(model.frameCount)}" +
                " · showing ${seconds(state.playheadMs)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        TrimStrip(model)
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        model.warning?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
        TrimActions(model, onClose)
    }
}

@Composable
private fun TrimHeader(model: TrimModel, onClose: () -> Unit) {
    val info = model.video.info
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(model.video.file.fileName.toString(), style = MaterialTheme.typography.titleLarge)
            Text(
                "${seconds(info.durationMs)}, ${info.width}x${info.height}, ${framesPerSecond(info.frameRate)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClose, enabled = !model.state.saving) { Text("Cancel") }
    }
}

@Composable
private fun Preview(preview: Raster?, modifier: Modifier) {
    val bitmap = remember(preview) { preview?.toImageBitmap() }
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap, "The frame under the playhead", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            Text("Decoding…", color = Color.White)
        }
    }
}

/**
 * The whole video as thumbnails, with the range's two handles, the part outside it dimmed, and the
 * playhead. A press near a handle takes that handle; anywhere else it moves the playhead.
 */
@Composable
private fun TrimStrip(model: TrimModel) {
    val state = model.state
    val thumbnails = remember(state.strip) { state.strip?.map { it.toImageBitmap() } ?: emptyList() }
    Filmstrip(thumbnails, model.video.info.durationMs, model) { position ->
        val startX = position(state.range.startMs)
        val endX = position(state.range.endMs)
        drawRect(OUTSIDE_RANGE, Offset.Zero, Size(startX, size.height))
        drawRect(OUTSIDE_RANGE, Offset(endX, 0f), Size(size.width - endX, size.height))
        val grip = GRIP_WIDTH.toPx()
        drawRect(HANDLE, Offset(startX, 0f), Size(grip, size.height))
        drawRect(HANDLE, Offset(endX - grip, 0f), Size(grip, size.height))
        drawTick(Color.White, position(state.playheadMs), 2f)
    }
}

/** Where the clip is filed, and the button that saves it. A clip being re-trimmed stays where it is. */
@Composable
private fun TrimActions(model: TrimModel, onClose: () -> Unit) {
    val state = model.state
    val scope = rememberCoroutineScope()
    val fixed = model.editing != null
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BETWEEN)) {
        Choice("Action", state.action, model.actions, enabled = !fixed, onChoose = model::chooseAction)
        Choice("Facing", state.facing, model.facings, enabled = !fixed, onChoose = model::chooseFacing)
        Spacer(Modifier.weight(1f))
        if (state.saving) Text("Extracting frames…", style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = { scope.launch { if (model.save()) onClose() } },
            enabled = !state.saving,
        ) { Text(if (fixed) "Save trim" else "Add clip") }
    }
}
