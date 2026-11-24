package com.roto.core.model

import com.roto.core.image.Raster
import com.roto.core.template.SafeName

/**
 * One animation a [ModelSource] found: what the import dialog lists, one row per proposed model.
 *
 * @property id the source's own name for the animation (a folder or tag name), unique within the source
 * @property action the guessed action, or null when the source gives no hint; the artist confirms it
 * @property facing the guessed facing, or null when the source gives no hint; the artist confirms it
 * @property frameCount how many frames it has, at least 1
 */
data class ModelRow(val id: String, val action: String?, val facing: String?, val frameCount: Int) {
    init {
        require(id.isNotEmpty()) { "A model row needs an id" }
        require(frameCount >= 1) { "Row $id has $frameCount frames, expected at least 1" }
    }
}

/**
 * Where a model's frames come from: a folder of PNGs, an `.ase` file or an atlas. Each reader hands
 * back the same normalised parts, so the importer never knows the format. A frame that is not canvas
 * size fails with a [ModelException] naming both sizes; Roto never rescales a model.
 */
interface ModelSource {
    /** What the source holds, one row per animation, in a fixed order. */
    fun rows(): List<ModelRow>

    /** The frames of [row], in order, at canvas size. */
    fun frames(row: ModelRow): List<Raster>

    /** Each frame's duration in ms, one per frame of [row]. */
    fun durations(row: ModelRow): List<Int>

    /** Whether [row] loops. */
    fun loop(row: ModelRow): Boolean
}

/** Splits `<action>_<facing>` at its last underscore; null parts when the name does not read that way. */
internal fun guessActionAndFacing(name: String): Pair<String?, String?> {
    val cut = name.lastIndexOf('_')
    if (cut <= 0 || cut == name.lastIndex) return null to null
    val action = name.substring(0, cut)
    val facing = name.substring(cut + 1)
    return if (SafeName.isValid(action) && SafeName.isValid(facing)) action to facing else null to null
}
