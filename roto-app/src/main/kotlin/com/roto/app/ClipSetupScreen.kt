package com.roto.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.roto.core.mask.BrushMode
import com.roto.core.mask.MaskSettings
import com.roto.core.profile.PlateSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val PANEL_WIDTH = 320.dp
private val BETWEEN = 12.dp
private val MARKER_WIDTH = 3.dp
private val SELECTED_MARKER_WIDTH = 6.dp
private val END_MARKER = Color(0xFF80DEEA)
private const val PREVIEW_DELAY_MS = 60L
private const val SAVE_DELAY_MS = 300L
private const val PROPOSAL_DELAY_MS = 800L
private const val MAX_MORPHOLOGY = 8
private const val MAX_THRESHOLD = 0.4f
private const val MIN_THRESHOLD = 0.005f
private const val MAX_BRUSH = 32
private const val THOUSANDTHS = 1000.0

/** One clip's setup: the preview and marked filmstrip on the left, plate, mask and brush on the right. */
@Composable
fun ClipSetupScreen(model: ClipSetupModel, onClose: () -> Unit) {
    val state = model.state
    val scope = rememberCoroutineScope()
    LaunchedEffect(model) { model.open() }
    LaunchedEffect(model, state.frameCount, state.frame, state.clip.mask, state.clip.plate) {
        delay(PREVIEW_DELAY_MS)
        model.refresh()
    }
    LaunchedEffect(model, state.frameCount, state.clip.mask, state.clip.plate) {
        delay(PROPOSAL_DELAY_MS)
        if (state.frameCount > 0) model.loadProposal()
    }
    LaunchedEffect(model, state.clip) {
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
                SetupPreview(model, Modifier.weight(1f).fillMaxWidth())
                MarkerStrip(model)
            }
            SetupPanel(model, Modifier.width(PANEL_WIDTH).fillMaxHeight())
        }
    }
}

/**
 * The picture, its mask veiled over it, and the brush: a stroke painted here lands in working-frame
 * pixels, whatever size the picture is shown at. Pixels are drawn square, so the artist sees exactly
 * what the mask holds.
 */
@Composable
private fun SetupPreview(model: ClipSetupModel, modifier: Modifier) {
    val state = model.state
    val shown = if (state.view == SetupView.PLATE) state.plate else state.picture
    val bitmap = remember(shown) { shown?.toImageBitmap() }
    val veil = remember(state.mask, state.view) { state.mask?.takeIf { state.view == SetupView.MASK }?.toVeil() }
    Box(
        modifier.background(Color.Black).pointerInput(model) {
            val fit = {
                val picture = model.state.picture
                picture?.let { Fit.of(size.width, size.height, it.width, it.height) }
            }
            awaitEachGesture {
                val down = awaitFirstDown()
                fit()?.let { model.beginStroke(it.pictureX(down.position.x), it.pictureY(down.position.y)) }
                drag(down.id) { change ->
                    fit()?.let { model.extendStroke(it.pictureX(change.position.x), it.pictureY(change.position.y)) }
                    change.consume()
                }
                model.endStroke()
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val image = bitmap ?: return@Canvas
            val fit = Fit.of(size.width.toInt(), size.height.toInt(), image.width, image.height)
            val offset = IntOffset(fit.left.toInt(), fit.top.toInt())
            val fitted = IntSize(fit.width, fit.height)
            drawImage(image, dstOffset = offset, dstSize = fitted, filterQuality = FilterQuality.None)
            veil?.let { drawImage(it, dstOffset = offset, dstSize = fitted, filterQuality = FilterQuality.None) }
        }
    }
}

/**
 * The clip as a filmstrip, its key-frame markers on top. A press near a marker selects it and shows
 * its frame, and dragging moves it; anywhere else moves the playhead. The last marker is drawn apart,
 * since it ends the last key frame rather than being one.
 */
@Composable
private fun MarkerStrip(model: ClipSetupModel) {
    val state = model.state
    val thumbnails = remember(state.strip) { state.strip?.map { it.toImageBitmap() } ?: emptyList() }
    Text(frameLabel(state), style = MaterialTheme.typography.bodyMedium)
    Filmstrip(thumbnails, state.frameCount.toLong(), model.markers) { position ->
        val boundaries = state.keyFrames?.boundaries.orEmpty()
        boundaries.forEachIndexed { index, boundary ->
            val width = if (index == state.marker) SELECTED_MARKER_WIDTH else MARKER_WIDTH
            val colour = if (index == boundaries.lastIndex) END_MARKER else HANDLE
            drawTick(colour, position(boundary.toLong()), width.toPx())
        }
        drawTick(Color.White, position(state.frame.toLong()), 2f)
    }
    MarkerActions(model)
}

@Composable
private fun MarkerActions(model: ClipSetupModel) {
    val state = model.state
    val keyFrames = state.keyFrames
    val source = when {
        keyFrames == null -> "Finding key frames…"
        state.clip.markers != null -> "Your markers"
        else -> "Proposed from the cycle"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = model.markers::add,
            enabled = keyFrames != null && state.frame !in keyFrames.boundaries,
        ) { Text("Add marker") }
        OutlinedButton(
            onClick = model.markers::remove,
            enabled = state.marker != null && keyFrames?.canRemove == true,
        ) { Text("Remove marker") }
        Text(source, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = model.markers::useProposal, enabled = state.clip.markers != null) {
            Text("Back to proposal")
        }
    }
}

/** Where the playhead is, and what the animation shows there. */
private fun frameLabel(state: SetupState): String {
    val position = "Frame ${state.frame + 1} of ${state.frameCount}"
    val keyFrames = state.keyFrames ?: return position
    val shown = keyFrames.keyFrameAt(state.frame) ?: return "$position · after the last key frame"
    val number = "key frame ${shown + 1} of ${keyFrames.frames.size}"
    if (keyFrames.frames[shown] != state.frame) return "$position · not drawn: $number shows here"
    val duration = state.frameRate?.let { ", shown ${keyFrames.durationsMs(it)[shown]} ms" }.orEmpty()
    return "$position · $number$duration"
}

@Composable
private fun SetupPanel(model: ClipSetupModel, modifier: Modifier) {
    val scroll = rememberScrollState()
    Box(modifier) {
        Column(
            Modifier.fillMaxSize().padding(end = BETWEEN).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(BETWEEN),
        ) {
            ViewChoice(model)
            HorizontalDivider()
            PlatePanel(model)
            HorizontalDivider()
            MaskPanel(model)
            HorizontalDivider()
            BrushPanel(model)
        }
        // The brush sits below the fold on a small window, and nothing else would say the panel scrolls.
        VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun ViewChoice(model: ClipSetupModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val views = listOf(SetupView.MASK to "Mask", SetupView.FRAME to "Frame", SetupView.PLATE to "Plate")
        views.forEach { (view, name) ->
            FilterChip(
                selected = model.state.view == view,
                onClick = { model.selectView(view) },
                label = { Text(name) },
            )
        }
    }
}

@Composable
private fun PlatePanel(model: ClipSetupModel) {
    val plate = model.state.clip.plate
    Section("Clean plate") {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = plate == PlateSource.ClipMedian,
                onClick = model::useClipMedian,
                label = { Text("Median of the clip") },
            )
            FilterChip(
                selected = plate is PlateSource.EmptyRange,
                onClick = model::useEmptyRange,
                label = { Text("Empty wall") },
            )
        }
        if (plate is PlateSource.EmptyRange) {
            val end = model.videoDurationMs.toFloat().coerceAtLeast(1f)
            Setting("From ${seconds(plate.range.startMs)}", plate.range.startMs.toFloat(), 0f..end) {
                model.moveEmptyStart(it.roundToLong())
            }
            Setting("To ${seconds(plate.range.endMs)}", plate.range.endMs.toFloat(), 0f..end) {
                model.moveEmptyEnd(it.roundToLong())
            }
        }
    }
}

@Composable
private fun MaskPanel(model: ClipSetupModel) {
    val mask = model.state.clip.mask
    Section("Mask") {
        Threshold("Keep lightness", mask.lightnessWeight, 0f..1f) { value ->
            model.changeMask { it.copy(lightnessWeight = value) }
        }
        Threshold("Edge threshold", mask.low, MIN_THRESHOLD..MAX_THRESHOLD) { value ->
            model.changeMask { it.withLow(value) }
        }
        Threshold("Certain threshold", mask.high, MIN_THRESHOLD..MAX_THRESHOLD) { value ->
            model.changeMask { it.withHigh(value) }
        }
        Radius("Remove specks", mask.openRadius, MAX_MORPHOLOGY) { value ->
            model.changeMask { it.copy(openRadius = value) }
        }
        Radius("Fill gaps", mask.closeRadius, MAX_MORPHOLOGY) { value ->
            model.changeMask { it.copy(closeRadius = value) }
        }
        TextButton(onClick = { model.changeMask { MaskSettings() } }) { Text("Back to defaults") }
    }
}

@Composable
private fun BrushPanel(model: ClipSetupModel) {
    val state = model.state
    Section("Brush") {
        if (!state.canBrush) {
            Text(
                "Go to a key frame to fix its mask.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.strandedFixes > 0) {
            Text(
                "${state.strandedFixes} stroke(s) sit on frames no longer drawn; they count again if a marker returns.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(BrushMode.ADD to "Add", BrushMode.ERASE to "Erase").forEach { (mode, name) ->
                FilterChip(
                    selected = state.brush.mode == mode,
                    onClick = { model.changeBrush(state.brush.copy(mode = mode)) },
                    label = { Text(name) },
                )
            }
        }
        Radius("Size", state.brush.radius, MAX_BRUSH, minimum = 1) { model.changeBrush(state.brush.copy(radius = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = model::undoStroke, enabled = state.frameFixes.isNotEmpty()) { Text("Undo") }
            OutlinedButton(onClick = model::clearFrame, enabled = state.frameFixes.isNotEmpty()) { Text("Clear frame") }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

/** A slider for a fractional setting, in steps of a thousandth so `profile.json` stays readable. */
@Composable
private fun Threshold(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Double) -> Unit,
) {
    val thousandths = (value * THOUSANDTHS).roundToInt()
    Setting("$label: ${thousandths / THOUSANDTHS}", value.toFloat(), range) {
        onChange((it * THOUSANDTHS).roundToInt() / THOUSANDTHS)
    }
}

@Composable
private fun Radius(label: String, value: Int, maximum: Int, minimum: Int = 0, onChange: (Int) -> Unit) {
    Setting("$label: $value", value.toFloat(), minimum.toFloat()..maximum.toFloat()) { onChange(it.roundToInt()) }
}

@Composable
private fun Setting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
