package com.roto.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.roto.app.help.HelpControl
import com.roto.app.help.Helped
import com.roto.core.image.Argb
import com.roto.core.image.Raster
import com.roto.core.palette.HexColor
import com.roto.core.publish.Mirror
import com.roto.core.review.FlipbookFrame
import com.roto.core.review.FlipbookSource
import com.roto.core.review.LintFinding
import com.roto.core.review.SeamDiff

private const val CHECKER_SQUARE = 8f
private const val GHOST_ALPHA = 0.4f
private const val CHECKER_NAME = "Checker"
private const val GREY_NAME = "Mid grey"
private val MID_GREY = Color(0xFF808080)
private val CHECKER_LIGHT = Color(0xFFCCCCCC)
private val CHECKER_DARK = Color(0xFF999999)
private val SEAM_COLOUR = Argb.of(255, 0, 255)
private val MARK_COLOUR = Color(0xFFFF00FF)
private const val MARK_RADIUS = 3
private const val MARK_STROKE = 2f
private val VIEWPORT_HEIGHT = 360.dp

/**
 * The player of one clip's handoff: the frame at the zoom the artist chose, a status line saying which
 * layer it shows, and the controls of [model]. [mirrored] says the frames were read flipped, so the model
 * ghost is flipped to match; [mirrorChoice] draws the control that changes it, if the clip has one. The
 * tick comes from the window's frame clock, so the picture follows [model] alone.
 */
@Composable
internal fun FlipbookPane(
    model: FlipbookModel,
    title: String,
    mirrored: Boolean,
    rampColours: List<Int>,
    onClose: () -> Unit,
    mirrorChoice: (@Composable () -> Unit)? = null,
) {
    LaunchedEffect(model) {
        while (true) withFrameMillis { model.tick(it) }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Helped(HelpControl.FLIPBOOK_CLOSE) { TextButton(onClick = onClose) { Text("Close player") } }
        }
        when (val load = model.load) {
            FlipbookLoad.Loading -> Text("Reading the handoff…", style = MaterialTheme.typography.bodyMedium)
            is FlipbookLoad.Disabled -> SelectableMessage(load.reason)
            is FlipbookLoad.Ready -> {
                FlipbookControls(model, rampColours, mirrorChoice)
                FlipbookStatus(model, load)
                FlipbookView(model, mirrored)
            }
        }
    }
}

@Composable
private fun FlipbookControls(model: FlipbookModel, rampColours: List<Int>, mirrorChoice: (@Composable () -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Helped(HelpControl.FLIPBOOK_PLAY) {
            Button(onClick = { if (model.playing) model.pause() else model.play() }) {
                Text(if (model.playing) "Pause" else "Play")
            }
        }
        Helped(HelpControl.FLIPBOOK_PREVIOUS) { OutlinedButton(onClick = { model.step(-1) }) { Text("Previous") } }
        Helped(HelpControl.FLIPBOOK_NEXT) { OutlinedButton(onClick = { model.step(1) }) { Text("Next") } }
        Choice(
            "Speed",
            "${model.speed} %",
            FLIPBOOK_SPEEDS.map { "$it %" },
            help = HelpControl.FLIPBOOK_SPEED,
        ) { choice -> model.chooseSpeed(choice.removeSuffix(" %").toInt()) }
        Choice(
            "Zoom",
            "${model.zoom}x",
            FLIPBOOK_ZOOMS.map { "${it}x" },
            help = HelpControl.FLIPBOOK_ZOOM,
        ) { choice -> model.chooseZoom(choice.removeSuffix("x").toInt()) }
        val colours = rampColours.distinct().associateBy { HexColor.format(it) }
        Choice(
            "Background",
            backgroundName(model.background),
            listOf(CHECKER_NAME, GREY_NAME) + colours.keys,
            help = HelpControl.FLIPBOOK_BACKGROUND,
        ) { choice ->
            model.chooseBackground(
                when (choice) {
                    CHECKER_NAME -> FlipbookBackground.Checker
                    GREY_NAME -> FlipbookBackground.MidGrey
                    else -> FlipbookBackground.Colour(checkNotNull(colours[choice]))
                },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Toggle(HelpControl.FLIPBOOK_ONION, "Onion skin", model.onionOn, true) { model.toggleOnion() }
        Toggle(HelpControl.FLIPBOOK_SEAM, "Loop seam", model.seamOn, true) { model.toggleSeam() }
        Toggle(HelpControl.FLIPBOOK_GHOST, "Model ghost", model.ghostOn, model.hasModel) { model.toggleGhost() }
        mirrorChoice?.invoke()
    }
}

@Composable
private fun Toggle(control: HelpControl, label: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Helped(control) { Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled) }
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun backgroundName(background: FlipbookBackground): String = when (background) {
    FlipbookBackground.Checker -> CHECKER_NAME
    FlipbookBackground.MidGrey -> GREY_NAME
    is FlipbookBackground.Colour -> HexColor.format(background.argb)
}

@Composable
private fun FlipbookStatus(model: FlipbookModel, load: FlipbookLoad.Ready) {
    val frame = model.frame ?: return
    val seam = model.seam
    val text = buildString {
        append("Frame ${model.index + 1} of ${load.flipbook.frames.size} · ${frame.durationMs} ms · ${badge(frame)}")
        if (model.seamOn) {
            append(if (seam == null) " · one frame, no seam" else " · seam: ${seam.count} px change")
        }
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun badge(frame: FlipbookFrame): String = when (frame.source) {
    FlipbookSource.Art -> "art"
    FlipbookSource.Draft -> "draft"
    FlipbookSource.Reference -> "reference"
}

@Composable
private fun FlipbookView(model: FlipbookModel, mirrored: Boolean) {
    val frame = model.frame ?: return
    val shown = model.onion ?: frame.image
    val ghost = model.ghost
    val seam = model.seam
    val image = remember(shown) { shown.toImageBitmap() }
    val ghostImage = remember(ghost, mirrored) {
        ghost?.let { (if (mirrored) Mirror.flip(it) else it).toImageBitmap() }
    }
    val seamImage = remember(seam) { seam?.toHighlight() }
    val zoom = model.zoom
    Box(
        Modifier.fillMaxWidth().heightIn(max = VIEWPORT_HEIGHT).background(Color.Black)
            .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()),
    ) {
        Canvas(Modifier.size((frame.image.width * zoom).dp, (frame.image.height * zoom).dp)) {
            drawBackground(model.background)
            ghostImage?.let { drawLayer(it, zoom, GHOST_ALPHA) }
            drawLayer(image, zoom, 1f)
            seamImage?.let { drawLayer(it, zoom, 1f) }
            model.marked?.let { drawMark(it, zoom) }
        }
    }
}

/** A box round the pixel [at], [MARK_RADIUS] pixels out, so it shows at any zoom. */
private fun DrawScope.drawMark(at: LintFinding.Pixel, zoom: Int) {
    val side = (2 * MARK_RADIUS + 1) * zoom.toFloat()
    drawRect(
        MARK_COLOUR,
        Offset((at.x - MARK_RADIUS) * zoom.toFloat(), (at.y - MARK_RADIUS) * zoom.toFloat()),
        Size(side, side),
        style = Stroke(MARK_STROKE),
    )
}

private fun DrawScope.drawBackground(background: FlipbookBackground) {
    when (background) {
        FlipbookBackground.MidGrey -> drawRect(MID_GREY)
        is FlipbookBackground.Colour -> drawRect(Color(background.argb))
        FlipbookBackground.Checker -> drawChecker()
    }
}

private fun DrawScope.drawChecker() {
    val columns = (size.width / CHECKER_SQUARE).toInt() + 1
    val rows = (size.height / CHECKER_SQUARE).toInt() + 1
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val colour = if ((row + column) % 2 == 0) CHECKER_LIGHT else CHECKER_DARK
            val corner = Offset(column * CHECKER_SQUARE, row * CHECKER_SQUARE)
            drawRect(colour, corner, Size(CHECKER_SQUARE, CHECKER_SQUARE))
        }
    }
}

private fun DrawScope.drawLayer(image: ImageBitmap, zoom: Int, alpha: Float) {
    drawImage(
        image,
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(image.width * zoom, image.height * zoom),
        alpha = alpha,
        filterQuality = FilterQuality.None,
    )
}

/** The pixels that change across the seam, opaque magenta on clear. */
private fun SeamDiff.toHighlight(): ImageBitmap =
    Raster.of(mask.width, mask.height) { x, y -> if (mask[x, y]) SEAM_COLOUR else Argb.TRANSPARENT }.toImageBitmap()
