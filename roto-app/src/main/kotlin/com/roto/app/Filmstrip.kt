package com.roto.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

private val STRIP_HEIGHT = 96.dp
private val GRIP_REACH = 12.dp

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
 * Thumbnails across the full width, standing for a timeline of [length] — milliseconds of a video,
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
    Box(
        Modifier.fillMaxWidth().height(STRIP_HEIGHT).background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(gestures, length) {
                if (length <= 0) return@pointerInput
                val pointAt = { x: Float -> (x / size.width * length).roundToLong().coerceIn(0, length) }
                val reach = (GRIP_REACH.toPx() / size.width * length).roundToLong()
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
        Row(Modifier.fillMaxSize()) {
            thumbnails.forEach { thumbnail ->
                Image(thumbnail, null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            if (length > 0) overlay { point -> size.width * point / length }
        }
    }
}

/** A thin upright line across the strip at [x], centred on it. */
internal fun DrawScope.drawTick(colour: Color, x: Float, width: Float) {
    drawRect(colour, Offset(x - width / 2, 0f), Size(width, size.height))
}
