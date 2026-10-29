package com.roto.core.video

import com.roto.core.cache.RasterSequenceCacheCodec
import com.roto.core.image.PngCodec
import com.roto.core.image.Raster
import com.roto.core.process.ProcessResult
import com.roto.core.process.ProcessRunner
import com.roto.core.process.SystemProcessRunner
import com.roto.core.profile.TimeRange
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Drives the `ffmpeg` and `ffprobe` programs on `PATH`: Roto never decodes video itself.
 *
 * Frames are decoded upright (`ffmpeg` applies a phone video's display rotation), scaled to a working
 * height with an area filter, and converted to 8-bit RGB. Scaling and conversion run with swscale's
 * `bitexact` flag, which keeps them off CPU-specific code paths, so one `ffmpeg` [version] gives the
 * same pixels on every machine.
 */
class Ffmpeg(
    private val runner: ProcessRunner = SystemProcessRunner,
    private val ffmpeg: String = "ffmpeg",
    private val ffprobe: String = "ffprobe",
) {

    /**
     * The first line of `ffmpeg -version`. Decoders and filters change between releases, so a stage
     * whose output comes from `ffmpeg` puts this in its cache key.
     */
    val version: String by lazy {
        run(ffmpeg, listOf("-version")).stdout.toString(Charsets.UTF_8).lineSequence().first().trim()
    }

    /** Size, duration, frame rate and rotation of [source]'s first video stream. */
    fun probe(source: Path): VideoInfo {
        val result = run(
            ffprobe,
            listOf("-v", "error", "-print_format", "json", "-show_format", "-show_streams", "-select_streams", "v:0") +
                source.toString(),
        )
        return try {
            FfprobeParser.parse(result.stdout.toString(Charsets.UTF_8))
        } catch (e: IllegalArgumentException) {
            throw VideoToolException("$source: ${e.message}", e)
        }
    }

    /**
     * Extracts [range] of [source] into [folder] as frames named by [RasterSequenceCacheCodec], taken
     * at an even [frameRate] and scaled to [height] pixels, the width following the aspect ratio.
     */
    fun extractRange(source: Path, range: TimeRange, frameRate: FrameRate, height: Int, folder: Path) {
        require(height > 0) { "Working height must be positive, got $height" }
        // ffmpeg reads the whole output path as a pattern, so a '%' in the folder would garble it.
        require('%' !in folder.toString()) { "ffmpeg cannot write frames under a path containing '%': $folder" }
        run(
            ffmpeg,
            listOf("-v", "error", "-nostdin", "-y", "-ss", seconds(range.startMs), "-i", source.toString()) +
                listOf("-t", seconds(range.durationMs), "-an", "-sn", "-dn") +
                listOf("-vf", "fps=$frameRate,${scale(height)}", "-start_number", "0") +
                folder.resolve(RasterSequenceCacheCodec.PRINTF_PATTERN).toString(),
        )
        if (!Files.isRegularFile(folder.resolve(RasterSequenceCacheCodec.fileName(0)))) {
            throw VideoToolException("$source: ffmpeg extracted no frames from ${range.startMs}..${range.endMs} ms")
        }
    }

    /** The frame of [source] shown at [timeMs], scaled to [height] pixels; for filmstrips and pickers. */
    fun thumbnail(source: Path, timeMs: Long, height: Int): Raster {
        require(timeMs >= 0) { "Thumbnail time must not be negative, got $timeMs" }
        require(height > 0) { "Thumbnail height must be positive, got $height" }
        val result = run(
            ffmpeg,
            listOf("-v", "error", "-nostdin", "-ss", seconds(timeMs), "-i", source.toString()) +
                listOf("-an", "-sn", "-dn", "-frames:v", "1", "-vf", scale(height)) +
                listOf("-f", "image2pipe", "-c:v", "png", "-"),
        )
        if (result.stdout.isEmpty()) throw VideoToolException("$source: ffmpeg found no frame at $timeMs ms")
        return PngCodec.read(ByteArrayInputStream(result.stdout))
    }

    private fun run(program: String, arguments: List<String>): ProcessResult {
        val result = try {
            runner.run(listOf(program) + arguments)
        } catch (e: IOException) {
            throw VideoToolException("Could not start $program. Roto needs ffmpeg and ffprobe installed on PATH.", e)
        }
        if (result.exitCode != 0) {
            val reason = result.stderr.lines().filter { it.isNotBlank() }.takeLast(ERROR_LINES).joinToString("\n")
            throw VideoToolException("$program failed with exit code ${result.exitCode}: $reason")
        }
        return result
    }

    private companion object {
        const val ERROR_LINES = 5
        const val MS_PER_SECOND = 1000L
        const val MS_DIGITS = 3
        const val SCALE_FLAGS = "area+accurate_rnd+full_chroma_int+bitexact"

        /** Scales to [height], keeping the aspect ratio, and converts to 8-bit RGB in the same exact pass. */
        fun scale(height: Int): String = "scale=-1:$height:flags=$SCALE_FLAGS,format=rgb24"

        /** [ms] as the decimal seconds `ffmpeg` takes, written without the locale or a float. */
        fun seconds(ms: Long): String =
            "${ms / MS_PER_SECOND}.${(ms % MS_PER_SECOND).toString().padStart(MS_DIGITS, '0')}"
    }
}
