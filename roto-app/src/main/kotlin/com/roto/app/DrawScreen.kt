package com.roto.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.roto.core.zone.Zone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val PANEL_WIDTH = 300.dp
private val DRAFT_WIDTH = 240.dp
private val BETWEEN = 12.dp
private val DRAFT_BACKGROUND = Color(0xFF3A3A3A)
private val FRAME_BOX = Color(0xFF80DEEA)
private val MARK = Color(0xFFFFC107)
private const val DRAFT_DELAY_MS = 400L
private const val SAVE_DELAY_MS = 300L
private const val MIN_SQUASH = 0.5f
private const val HUNDREDTHS = 100.0
private const val SAMPLE_RADIUS = 4f
private const val MAX_BRUSH = 32

/**
 * Where a clip meets the canvas: the frame with its marks and frame box on the left, the draft in
 * the middle, following every change, and scale and zones on the right.
 */
@Composable
fun DrawScreen(model: DrawModel, onClose: () -> Unit) {
    val state = model.state
    val scope = rememberCoroutineScope()
    LaunchedEffect(model) { model.open() }
    LaunchedEffect(model, state.frameCount, state.scale, state.clip.zoning, state.clip.draft) {
        delay(DRAFT_DELAY_MS)
        if (state.frameCount > 0) model.refreshDraft()
    }
    LaunchedEffect(model, state.clip, state.scale) {
        delay(SAVE_DELAY_MS)
        model.save()
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(BETWEEN)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.clip.id, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = {
                    scope.launch {
                        model.save()
                        onClose()
                    }
                },
            ) { Text("Done") }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(BETWEEN)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PictureView(state.picture, model.picture, Modifier.weight(1f).fillMaxWidth()) { drawMarks(state, it) }
                FrameStrip(model)
            }
            DraftPreview(state, Modifier.width(DRAFT_WIDTH).fillMaxHeight())
            DrawPanel(model, Modifier.width(PANEL_WIDTH).fillMaxHeight())
        }
    }
}

/** On the picture: the head and feet marks, the canvas on this frame, and the samples taken here. */
private fun DrawScope.drawMarks(state: DrawState, fit: Fit) {
    val line = { y: Int, colour: Color ->
        val at = fit.screenY(y.toDouble())
        drawRect(colour, Offset(fit.left, at), Size(fit.width.toFloat(), 2f))
    }
    state.head?.let { line(it, MARK) }
    state.feet?.let { line(it + 1, MARK) }
    state.boxes?.let { boxes ->
        val frame = state.frame.coerceAtMost(boxes.centreX.lastIndex)
        val left = fit.screenX(boxes.frameX(frame, 0.0))
        val right = fit.screenX(boxes.frameX(frame, boxes.canvas.width.toDouble()))
        val top = fit.screenY(boxes.frameY(0.0))
        val bottom = fit.screenY(boxes.frameY(boxes.canvas.height.toDouble()))
        drawRect(FRAME_BOX, Offset(left, top), Size(right - left, bottom - top), style = Stroke(2f))
    }
    state.clip.zoning.zones.flatMap { it.samples }.filter { it.frame == state.frame }.forEach { sample ->
        val centre = Offset(fit.screenX(sample.x + 0.5), fit.screenY(sample.y + 0.5))
        drawCircle(Color.White, SAMPLE_RADIUS, centre)
    }
    state.stroke?.let { stroke ->
        stroke.points.forEach { point ->
            val centre = Offset(fit.screenX(point.x + 0.5), fit.screenY(point.y + 0.5))
            drawCircle(MARK.copy(alpha = 0.5f), stroke.radius * fit.scale, centre)
        }
    }
}

@Composable
private fun FrameStrip(model: DrawModel) {
    val state = model.state
    val thumbnails = remember(state.strip) { state.strip?.map { it.toImageBitmap() } ?: emptyList() }
    val position = "Frame ${state.frame + 1} of ${state.frameCount}"
    Text(if (state.onKeyFrame) "$position · key frame" else position, style = MaterialTheme.typography.bodyMedium)
    Filmstrip(thumbnails, state.frameCount.toLong(), model.strip) { position ->
        state.keyFrames?.frames?.forEach { frame -> drawTick(HANDLE, position(frame.toLong()), 3.dp.toPx()) }
        drawTick(Color.White, position(state.frame.toLong()), 2f)
    }
}

/** The draft of the key frame showing, at canvas size blown up with square pixels. */
@Composable
private fun DraftPreview(state: DrawState, modifier: Modifier) {
    val bitmap = remember(state.draft) { state.draft?.toImageBitmap() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().weight(1f).background(DRAFT_BACKGROUND), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val image = bitmap ?: return@Canvas
                drawPixels(image, Fit.of(size.width.toInt(), size.height.toInt(), image.width, image.height))
            }
            if (bitmap == null) {
                val note = state.missing ?: "Drawing…"
                Text(note, Modifier.padding(BETWEEN), color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(
            if (state.drawing) "Drawing…" else "Draft",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DrawPanel(model: DrawModel, modifier: Modifier) {
    val scroll = rememberScrollState()
    Box(modifier) {
        Column(
            Modifier.fillMaxSize().padding(end = BETWEEN).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(BETWEEN),
        ) {
            ScalePanel(model)
            HorizontalDivider()
            ZonePanel(model)
            HorizontalDivider()
            FinishPanel(model)
        }
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun ScalePanel(model: DrawModel) {
    val state = model.state
    val scale = state.scale
    Section("Scale") {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToolChip(model, DrawTool.Head, "Mark head")
            ToolChip(model, DrawTool.Feet, "Mark feet")
        }
        if (scale != null) {
            Radius("Body height", scale.bodyHeight, model.tallestBody, minimum = 1, onChange = model::setBodyHeight)
            val squash = (scale.squash * HUNDREDTHS).roundToInt() / HUNDREDTHS
            Setting("Squash: $squash", scale.squash.toFloat(), MIN_SQUASH..1f) {
                model.setSquash((it * HUNDREDTHS).roundToInt() / HUNDREDTHS)
            }
            if (scale.marks.clip != state.clip.id) {
                Text("Marked on ${scale.marks.clip}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ToolChip(model: DrawModel, tool: DrawTool, name: String) {
    val inHand = model.state.tool == tool
    FilterChip(
        selected = inHand,
        onClick = { model.useTool(if (inHand) null else tool) },
        label = { Text(name) },
    )
}

@Composable
private fun ZonePanel(model: DrawModel) {
    val state = model.state
    Section("Zones") {
        state.clip.zoning.zones.forEach { zone -> ZoneRow(model, zone) }
        NewZone(model)
        if (state.tool is DrawTool.Paint) {
            Radius("Brush size", state.brushRadius, MAX_BRUSH, minimum = 1, onChange = model::setBrushRadius)
        }
        OutlinedButton(onClick = model::undoStroke, enabled = state.frameFixes.isNotEmpty()) { Text("Undo stroke") }
    }
}

@Composable
private fun ZoneRow(model: DrawModel, zone: Zone) {
    Column {
        Text("${zone.name} · ${sampleCount(zone.samples.size)}", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToolChip(model, DrawTool.Sample(zone.name), "Sample")
            ToolChip(model, DrawTool.Paint(zone.name), "Paint")
        }
        Choice("Ramp", zone.ramp, model.state.ramps) { model.chooseRamp(zone.name, it) }
        Row {
            TextButton(onClick = { model.removeSample(zone.name) }) { Text("Undo sample") }
            TextButton(onClick = { model.removeZone(zone.name) }) { Text("Remove") }
        }
    }
}

/** Names a zone and picks its ramp; the next click on the figure makes it. */
@Composable
private fun NewZone(model: DrawModel) {
    val state = model.state
    var name by remember { mutableStateOf("") }
    var ramp by remember { mutableStateOf<String?>(null) }
    val chosen = ramp ?: state.ramps.firstOrNull()
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("New zone") },
    )
    if (chosen != null) Choice("Ramp", chosen, state.ramps) { ramp = it }
    Button(
        onClick = {
            if (chosen != null) model.useTool(DrawTool.NewZone(name.trim(), chosen))
            name = ""
        },
        enabled = name.isNotBlank() && chosen != null,
    ) { Text("Add, then click the figure") }
    (state.tool as? DrawTool.NewZone)?.let {
        Text("Click the figure to sample ${it.name}.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FinishPanel(model: DrawModel) {
    val dither = model.state.clip.draft.dither
    Section("Finish") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = dither, onCheckedChange = model::setDither)
            Text("Dither ramp edges", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun sampleCount(count: Int): String = if (count == 1) "1 sample" else "$count samples"
