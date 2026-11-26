package com.roto.core.model

import com.roto.core.ase.AseCel
import com.roto.core.ase.AseCodec
import com.roto.core.ase.AseFormatException
import com.roto.core.ase.AseSprite
import com.roto.core.image.Argb
import com.roto.core.image.Raster
import com.roto.core.project.Canvas
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.name

/**
 * An `.ase` or `.aseprite` file as animations. Each frame is its visible layers composited cel by
 * cel (cel position, layer and cel opacity, palette colour), durations come from the file. Each tag
 * becomes one row; a file without tags gives one row of all its frames. A tag sets the loop flag
 * (repeating forever loops); without a tag the artist's [defaultLoop] applies. A file outside the
 * supported subset fails with the [AseFormatException] as it is.
 *
 * @param file the `.ase` file
 * @param canvas the project canvas; the sprite must fit in it, and a smaller one is placed by [ModelCanvas]
 * @param defaultLoop the artist's loop choice for a file without tags
 */
class AseModelSource(
    private val file: Path,
    private val canvas: Canvas,
    private val defaultLoop: Boolean,
) : ModelSource {

    private class Entry(val row: ModelRow, val from: Int, val to: Int, val loop: Boolean)

    private val sprite: AseSprite by lazy {
        val read = try {
            AseCodec.read(file)
        } catch (e: AseFormatException) {
            throw e
        } catch (e: IOException) {
            throw ModelException("$file: ${e.message ?: e.javaClass.simpleName}", e)
        }
        ModelCanvas.requireFits(file.name, read.width, read.height, canvas)
        read
    }

    private val entries: List<Entry> by lazy {
        val tags = sprite.tags
        val found = if (tags.isEmpty()) {
            val name = file.name.substringBeforeLast('.')
            val (action, facing) = guessActionAndFacing(name)
            val row = ModelRow(name, action, facing, sprite.frames.size)
            listOf(Entry(row, 0, sprite.frames.lastIndex, defaultLoop))
        } else {
            tags.map { tag ->
                val (action, facing) = guessActionAndFacing(tag.name)
                val row = ModelRow(tag.name, action, facing, tag.to - tag.from + 1)
                Entry(row, tag.from, tag.to, tag.repeat == 0)
            }
        }
        val duplicate = found.groupBy { it.row.id }.entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) throw ModelException("${file.name}: more than one tag is named '${duplicate.key}'")
        found
    }

    private fun entry(row: ModelRow): Entry =
        entries.firstOrNull { it.row == row } ?: throw ModelException("${file.name} has no animation '${row.id}'")

    /** One row per tag, or one for the whole file. */
    override fun rows(): List<ModelRow> = entries.map { it.row }

    /** The frames of [row], each composited at sprite size and placed on the canvas. */
    override fun frames(row: ModelRow): List<Raster> =
        entry(row).let { (it.from..it.to).map { index -> ModelCanvas.place(file.name, composite(index), canvas) } }

    /** The file's frame durations for [row]. */
    override fun durations(row: ModelRow): List<Int> =
        entry(row).let { (it.from..it.to).map { index -> sprite.frames[index].durationMs } }

    /** The tag's loop flag, or the artist's choice for an untagged file. */
    override fun loop(row: ModelRow): Boolean = entry(row).loop

    private fun composite(frameIndex: Int): Raster {
        val out = Raster.of(sprite.width, sprite.height) { _, _ -> Argb.TRANSPARENT }
        val cels = sprite.frames[frameIndex].cels.sortedBy { it.layerIndex }
        for (cel in cels) {
            val layer = sprite.layers[cel.layerIndex]
            if (!layer.visible) continue
            paint(out, cel, divideRounded(layer.opacity * cel.opacity, OPAQUE))
        }
        return out
    }

    private fun paint(out: Raster, cel: AseCel, opacity: Int) {
        for (cy in 0 until cel.image.height) {
            for (cx in 0 until cel.image.width) {
                val x = cel.x + cx
                val y = cel.y + cy
                val index = cel.image[cx, cy]
                if (x !in 0 until out.width || y !in 0 until out.height || index == sprite.transparentIndex) continue
                val colour = sprite.palette[index]
                val alpha = divideRounded(Argb.alpha(colour) * opacity, OPAQUE)
                if (alpha > 0) out[x, y] = over(colour, alpha, out[x, y])
            }
        }
    }
}

private const val OPAQUE = 255

private fun divideRounded(numerator: Int, denominator: Int): Int = (numerator + denominator / 2) / denominator

/** [top] at [alpha] over [bottom], straight alpha, integer arithmetic. */
private fun over(top: Int, alpha: Int, bottom: Int): Int {
    val below = Argb.alpha(bottom)
    if (alpha == OPAQUE || below == 0) return Argb.of(Argb.red(top), Argb.green(top), Argb.blue(top), alpha)
    val belowWeight = divideRounded(below * (OPAQUE - alpha), OPAQUE)
    val outAlpha = alpha + belowWeight
    fun mix(t: Int, b: Int) = (t * alpha + b * belowWeight + outAlpha / 2) / outAlpha
    return Argb.of(
        mix(Argb.red(top), Argb.red(bottom)),
        mix(Argb.green(top), Argb.green(bottom)),
        mix(Argb.blue(top), Argb.blue(bottom)),
        outAlpha,
    )
}
