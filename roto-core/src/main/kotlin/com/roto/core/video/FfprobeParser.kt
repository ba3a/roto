package com.roto.core.video

import com.roto.core.json.CanonicalJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.RoundingMode
import kotlin.math.roundToInt

/**
 * Turns `ffprobe -print_format json -show_format -show_streams` output into a [VideoInfo].
 *
 * The output is read as a JSON tree rather than decoded into a model: it carries dozens of fields
 * that vary between `ffprobe` versions and containers, and only a handful matter here. Throws an
 * [IllegalArgumentException] naming what is missing when the output describes no usable video.
 */
internal object FfprobeParser {
    private const val FULL_TURN = 360
    private const val QUARTER_TURN = 90
    private const val MS_DECIMALS = 3

    fun parse(output: String): VideoInfo {
        val root = CanonicalJson.json.parseToJsonElement(output).jsonObject
        val stream = root["streams"]?.jsonArray?.firstOrNull()?.jsonObject
        requireNotNull(stream) { "ffprobe found no video stream" }
        val codedWidth = requireNotNull(stream.int("width")) { "ffprobe reported no video width" }
        val codedHeight = requireNotNull(stream.int("height")) { "ffprobe reported no video height" }
        val rotation = rotationOf(stream)
        val sideways = rotation % (2 * QUARTER_TURN) != 0
        return VideoInfo(
            width = if (sideways) codedHeight else codedWidth,
            height = if (sideways) codedWidth else codedHeight,
            durationMs = durationMs(stream, root["format"]?.jsonObject),
            frameRate = frameRate(stream),
            rotation = rotation,
        )
    }

    /** Phone videos are often stored sideways with a display rotation, which `ffmpeg` applies on decode. */
    private fun rotationOf(stream: JsonObject): Int {
        // Newer ffprobe reports a display matrix, rotated counter-clockwise; older ones a clockwise `rotate` tag.
        val matrix = stream["side_data_list"]?.jsonArray
            ?.firstNotNullOfOrNull { it.jsonObject["rotation"]?.jsonPrimitive?.doubleOrNull }
            ?.roundToInt()
            ?.let { -it }
        val tag = stream["tags"]?.jsonObject?.string("rotate")?.toIntOrNull()
        val clockwise = Math.floorMod(matrix ?: tag ?: 0, FULL_TURN)
        require(clockwise % QUARTER_TURN == 0) { "Video rotation of $clockwise degrees is not a quarter turn" }
        return clockwise
    }

    /** The average rate describes variable-rate phone video best; the base rate stands in when it is unknown. */
    private fun frameRate(stream: JsonObject): FrameRate {
        val rate = listOf("avg_frame_rate", "r_frame_rate")
            .mapNotNull { stream.string(it) }
            .firstOrNull { it.isPositiveRate() }
        requireNotNull(rate) { "ffprobe reported no frame rate" }
        return FrameRate.parse(rate)
    }

    private fun String.isPositiveRate(): Boolean =
        runCatching { FrameRate.parse(this) }.isSuccess

    private fun durationMs(stream: JsonObject, format: JsonObject?): Long {
        val seconds = listOfNotNull(stream.string("duration"), format?.string("duration"))
            .firstNotNullOfOrNull { it.toBigDecimalOrNull() }
        requireNotNull(seconds) { "ffprobe reported no duration" }
        return seconds.movePointRight(MS_DECIMALS).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
}
