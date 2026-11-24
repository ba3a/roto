package com.roto.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val STRIP_HEIGHT = 96.dp
private val MIN_STRIP_HEIGHT = 64.dp
private val MAX_STRIP_HEIGHT = 480.dp
private val GRIP_HEIGHT = 12.dp
private val GRIP_REACH = 12.dp
private const val MAX_ZOOM = 32f
private const val ZOOM_STEP = 1.25f
private const val SCROLL_PX = 60f

/** Amber stands out from most pictures, and a handle sits on top of any picture. */
internal val HANDLE = Color(0xFFFFC107)

/** What a strip tells whoever is using it, in the units of its [length][Filmstrip]. */
interface StripGestures {
    /** A press at [at]; anything within [reach] of it counts as under the pointer. */
    fun press(at: Long, reach: Long)

    /** The pointer, still down, is now at [at]. */
    fun drag(at: Long)
}

/**
 * Thumbnails across the full width, whole and uncropped (Ctrl + wheel zooms, wheel scrolls once
 * zoomed), standing for a timeline of [length] — milliseconds of a video,
 * frames of a clip. [gestures] hears presses and drags in those units, and [overlay] draws what
 * belongs on top, given the position of any point along it.
 */
@Composable
internal fun Filmstrip(
    thumbnails: List<ImageBitmap>,
    length: Long,
    gestures: StripGestures,
    overlay: DrawScope.(position: (Long) -> Float) -> Unit,
) {
    var height by remember { mutableStateOf(STRIP_HEIGHT) }
    val view = remember { StripView() }
    ResizeGrip { delta -> height = (height + delta).coerceIn(MIN_STRIP_HEIGHT, MAX_STRIP_HEIGHT) }
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(height).clipToBounds().background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { view.viewport = it.width.toFloat() }
            .pointerInput(Unit) { zoomAndScroll(view) }
            .pointerInput(gestures, length) {
                if (length <= 0) return@pointerInput
                val pointAt = { x: Float ->
                    ((x + view.scroll) / (size.width * view.zoom) * length).roundToLong().coerceIn(0, length)
                }
                val reach = (GRIP_REACH.toPx() / (size.width * view.zoom) * length).roundToLong()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    gestures.press(pointAt(down.position.x), reach)
                    drag(down.id) { change ->
                        gestures.drag(pointAt(change.position.x))
                        change.consume()
                    }
                }
            },
    ) {
        val contentWidth = maxWidth * view.zoom
        Box(Modifier.requiredWidth(contentWidth).fillMaxHeight().offset { IntOffset(-view.scroll.roundToInt(), 0) }) {
            Row(Modifier.fillMaxSize()) {
                fitted(thumbnails, contentWidth / height).forEach { thumbnail ->
                    Image(thumbnail, null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Fit)
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                if (length > 0) overlay { point -> size.width * point / length }
            }
        }
        if (view.zoom > 1f) {
            Box(
                Modifier.align(Alignment.BottomStart).offset(maxWidth * (view.scroll / (view.viewport * view.zoom)))
                    .width(maxWidth / view.zoom).height(3.dp).background(MaterialTheme.colorScheme.outline),
            )
        }
    }
}

/** How far a strip is zoomed in and scrolled, in pixels of the zoomed strip; 1 shows the whole timeline. */
private class StripView {
    var zoom by mutableFloatStateOf(1f)
    var scroll by mutableFloatStateOf(0f)
    var viewport by mutableFloatStateOf(1f)

    /** Zooms by [wheel] notches, keeping the point under [pointerX] in place. */
    fun zoomBy(wheel: Float, pointerX: Float) {
        val next = (zoom * ZOOM_STEP.pow(-wheel)).coerceIn(1f, MAX_ZOOM)
        scroll = ((pointerX + scroll) / zoom * next - pointerX).coerceIn(0f, (next - 1) * viewport)
        zoom = next
    }

    /** Scrolls by [wheel] notches. */
    fun scrollBy(wheel: Float) {
        scroll = (scroll + wheel * SCROLL_PX).coerceIn(0f, (zoom - 1) * viewport)
    }
}

/** Ctrl + wheel zooms [view]; the wheel alone scrolls it once zoomed. */
private suspend fun PointerInputScope.zoomAndScroll(view: StripView) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type != PointerEventType.Scroll) continue
            val change = event.changes.first()
            val delta = change.scrollDelta
            when {
                event.keyboardModifiers.isCtrlPressed -> view.zoomBy(delta.y, change.position.x)
                view.zoom > 1f -> view.scrollBy(if (abs(delta.x) > abs(delta.y)) delta.x else delta.y)
                else -> continue
            }
            event.changes.forEach { it.consume() }
        }
    }
}

/**
 * Every n-th of [thumbnails], as many as fit across a strip [aspect] times wider than it is tall at
 * the thumbnails' own shape, so a wide or tall strip shows whole pictures, never cropped slices.
 */
private fun fitted(thumbnails: List<ImageBitmap>, aspect: Float): List<ImageBitmap> {
    val first = thumbnails.firstOrNull() ?: return thumbnails
    val fit = (aspect * first.height / first.width).roundToInt().coerceIn(1, thumbnails.size)
    return List(fit) { thumbnails[(it * thumbnails.size + thumbnails.size / 2) / fit] }
}

/** A bar above the strip; dragging it up makes the strip taller, down shorter. */
@Composable
private fun ResizeGrip(onResize: (Dp) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(GRIP_HEIGHT).pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onResize(-drag.y.toDp())
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(48.dp).height(4.dp).background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)))
    }
}

/** A thin upright line across the strip at [x], centred on it. */
internal fun DrawScope.drawTick(colour: Color, x: Float, width: Float) {
    drawRect(colour, Offset(x - width / 2, 0f), Size(width, size.height))
}
