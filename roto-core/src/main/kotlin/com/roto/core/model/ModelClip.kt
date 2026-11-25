package com.roto.core.model

import com.roto.core.image.Raster
import com.roto.core.template.SafeName

/**
 * One frame of a [ModelClip]: its picture and how long it shows.
 *
 * @property raster the frame at canvas size, straight alpha
 * @property durationMs how long the frame shows, at least 1 ms
 */
class ModelFrame(val raster: Raster, val durationMs: Int) {
    init {
        require(durationMs >= MIN_DURATION_MS) { "A frame lasts at least $MIN_DURATION_MS ms, got $durationMs" }
    }

    /** Equal when the picture and the duration are. */
    override fun equals(other: Any?): Boolean =
        other is ModelFrame && raster == other.raster && durationMs == other.durationMs

    /** Consistent with [equals]. */
    override fun hashCode(): Int = 31 * raster.hashCode() + durationMs

    /** The size and duration, for messages and tests. */
    override fun toString(): String = "ModelFrame(${raster.width}x${raster.height}, ${durationMs}ms)"
}

/**
 * One finished animation the artist imported as reference: an action and facing, its frames at one
 * size with their durations, and whether it loops. A model is read-only; it is stored under the
 * profile's `models/<action>_<facing>/` folder.
 *
 * @property action the action, a [SafeName]
 * @property facing the facing, a [SafeName]
 * @property frames the frames in order, at least one, all the same size
 * @property loop whether the animation repeats
 * @property origin where it was imported from, for the artist's information
 */
class ModelClip(
    val action: String,
    val facing: String,
    val frames: List<ModelFrame>,
    val loop: Boolean,
    val origin: String,
) {
    init {
        SafeName.validate(action, "action")
        SafeName.validate(facing, "facing")
        require(frames.isNotEmpty()) { "Model $key has no frames" }
        val first = frames.first().raster
        frames.forEachIndexed { index, frame ->
            require(frame.raster.width == first.width && frame.raster.height == first.height) {
                "Model $key frame $index is ${frame.raster.width}x${frame.raster.height}, " +
                    "expected ${first.width}x${first.height}"
            }
        }
    }

    /** `<action>_<facing>`: the model's folder name. */
    val key: String get() = "${action}_$facing"

    /** The frames' width. */
    val width: Int get() = frames.first().raster.width

    /** The frames' height. */
    val height: Int get() = frames.first().raster.height

    /**
     * The index of the model frame shown with key frame [index] of [keyFrameCount]: the nearest to
     * `index * (n - 1) / (k - 1)` for `n` model frames and `k` key frames, in exact integer arithmetic
     * with halves rounded up. One key frame shows the first model frame.
     */
    fun frameIndexFor(index: Int, keyFrameCount: Int): Int {
        require(keyFrameCount >= 1 && index in 0 until keyFrameCount) {
            "Key frame $index is outside $keyFrameCount key frames"
        }
        if (keyFrameCount == 1) return 0
        val span = (keyFrameCount - 1).toLong()
        return ((2L * index * (frames.size - 1) + span) / (2 * span)).toInt()
    }

    /** The `model.json` content: everything but the pictures. */
    fun manifest(): ModelManifest = ModelManifest(action, facing, loop, frames.map { it.durationMs }, origin)

    /** Equal when every part is. */
    override fun equals(other: Any?): Boolean =
        other is ModelClip && action == other.action && facing == other.facing && frames == other.frames &&
            loop == other.loop && origin == other.origin

    /** Consistent with [equals]. */
    override fun hashCode(): Int = listOf(action, facing, frames, loop, origin).hashCode()

    /** The key and frame count, for messages and tests. */
    override fun toString(): String = "ModelClip($key, ${frames.size} frames)"
}

private const val MIN_DURATION_MS = 1
