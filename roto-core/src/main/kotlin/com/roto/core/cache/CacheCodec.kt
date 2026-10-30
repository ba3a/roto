package com.roto.core.cache

import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import com.roto.core.image.Mask
import com.roto.core.image.PngCodec
import com.roto.core.image.Raster
import com.roto.core.json.JsonCodec
import kotlinx.serialization.KSerializer
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Stores one kind of stage output in a cache entry folder. [write] gets an empty folder; [read] gets
 * a folder an earlier [write] filled completely, and throws an [IOException] when its content is not
 * what [write] leaves, so [StageCache] can discard the entry and compute it again.
 */
interface CacheCodec<T : Any> {
    fun write(value: T, entry: Path)

    fun read(entry: Path): T
}

/** One raster, as `raster.png`. */
object RasterCacheCodec : CacheCodec<Raster> {
    private const val FILE_NAME = "raster.png"

    override fun write(value: Raster, entry: Path) = PngCodec.write(value, entry.resolve(FILE_NAME))

    override fun read(entry: Path): Raster = PngCodec.read(entry.resolve(FILE_NAME))
}

/**
 * A sequence of rasters — a clip's frames, or one mask per frame — as `000000.png`, `000001.png`, …
 * Frame `i` is always [fileName] of `i`, and the sequence ends at the first number with no file.
 */
object RasterSequenceCacheCodec : CacheCodec<List<Raster>> {
    private const val DIGITS = 6
    private const val EXTENSION = ".png"

    /** The same names as a printf pattern, `%06d.png`, for a program that writes the frames itself. */
    const val PRINTF_PATTERN = "%0${DIGITS}d$EXTENSION"

    fun fileName(index: Int): String {
        require(index >= 0) { "Frame index must not be negative, got $index" }
        return index.toString().padStart(DIGITS, '0') + EXTENSION
    }

    override fun write(value: List<Raster>, entry: Path) = writeEach(value.asSequence(), entry)

    override fun read(entry: Path): List<Raster> = readEach(entry).toList()

    /** Writes [rasters] one at a time, so converting them to rasters never holds them all at once. */
    fun writeEach(rasters: Sequence<Raster>, entry: Path) =
        rasters.forEachIndexed { index, raster -> PngCodec.write(raster, entry.resolve(fileName(index))) }

    /** Reads an entry one raster at a time, for the same reason. */
    fun readEach(entry: Path): Sequence<Raster> =
        generateSequence(0) { it + 1 }
            .map { entry.resolve(fileName(it)) }
            .takeWhile { Files.isRegularFile(it) }
            .map { PngCodec.read(it) }
}

/** A model, as `value.json` in the same canonical form every Roto JSON file has. */
class JsonCacheCodec<T : Any>(serializer: KSerializer<T>) : CacheCodec<T> {
    private val codec = object : JsonCodec<T>(serializer, "value.json", ::CacheException) {}

    override fun write(value: T, entry: Path) = codec.write(value, entry.resolve(codec.fileName))

    override fun read(entry: Path): T = codec.read(entry.resolve(codec.fileName))
}

/**
 * A sequence of per-frame values that each convert to and from one [Raster], stored through
 * [RasterSequenceCacheCodec]: masks, label maps.
 */
open class RasterBackedSequenceCacheCodec<T : Any>(
    private val toRaster: (T) -> Raster,
    private val fromRaster: (Raster) -> T,
) : CacheCodec<List<T>> {
    override fun write(value: List<T>, entry: Path) =
        RasterSequenceCacheCodec.writeEach(value.asSequence().map(toRaster), entry)

    override fun read(entry: Path): List<T> = RasterSequenceCacheCodec.readEach(entry).map(fromRaster).toList()
}

/** Masks, one per frame, stored as their [Mask.toRaster]. */
object MaskSequenceCacheCodec : RasterBackedSequenceCacheCodec<Mask>({ it.toRaster() }, { Mask.fromRaster(it) })

/** Label maps — zone or palette indices, one map per frame — stored as opaque gray levels. */
object LabelSequenceCacheCodec : RasterBackedSequenceCacheCodec<IndexedRaster>(::labelsToRaster, ::rasterToLabels)

private fun labelsToRaster(labels: IndexedRaster): Raster =
    Raster.of(labels.width, labels.height) { x, y -> labels[x, y].let { Argb.of(it, it, it) } }

private fun rasterToLabels(raster: Raster): IndexedRaster =
    IndexedRaster.of(raster.width, raster.height) { x, y -> Argb.red(raster[x, y]) }
