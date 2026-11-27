package com.roto.core.review

import com.roto.core.framebox.DepthSettings
import com.roto.core.image.Raster
import com.roto.core.model.ModelMeasurement
import com.roto.core.palette.Palette
import com.roto.core.palette.RampSet
import kotlinx.serialization.Serializable

/**
 * The thresholds the art lint rules use. Not stored per profile: lint is advice, so a default is enough.
 *
 * @property groundDriftPx how many rows a frame's lowest opaque row may differ from the clip's median
 * @property seamPercent how many percent of the figure's opaque pixels may differ across the loop seam
 */
@Serializable
data class LintSettings(val groundDriftPx: Int = 1, val seamPercent: Int = 5) {
    init {
        require(groundDriftPx >= 0) { "groundDriftPx must not be negative, got $groundDriftPx" }
        require(seamPercent in 0..100) { "seamPercent must be from 0 to 100, got $seamPercent" }
    }
}

/**
 * What a rule compares a clip with.
 *
 * @property measurement the model's geometry from [com.roto.core.model.ModelMeasure]
 * @property durationsMs the duration of every model frame
 */
class ModelReference(val measurement: ModelMeasurement, val durationsMs: List<Int>)

/**
 * What a rule may need besides the frames.
 *
 * @property palette the project's palette
 * @property ramps the project's ramps
 * @property outline whether the clip's draft is outlined
 * @property depth whether the clip follows depth
 * @property loop whether the clip loops, so its last frame is followed by its first
 * @property model the clip's model, when it has one
 * @property clipDurationsMs the duration of every frame of the clip's handoff, painted or not; empty when unknown, which leaves the frame count and duration total unchecked
 * @property settings the rules' thresholds
 */
class LintContext(
    val palette: Palette,
    val ramps: RampSet,
    val outline: Boolean,
    val depth: DepthSettings,
    val loop: Boolean = false,
    val model: ModelReference? = null,
    val clipDurationsMs: List<Int> = emptyList(),
    val settings: LintSettings = LintSettings(),
)

/**
 * One mistake a rule found.
 *
 * @property rule the name of the rule
 * @property frame the zero-based index of the frame
 * @property pixel where in the frame, or null for the frame as a whole
 * @property message one line saying what is wrong
 */
data class LintFinding(val rule: String, val frame: Int, val pixel: Pixel?, val message: String) {
    /** A pixel position [x], [y]. */
    data class Pixel(val x: Int, val y: Int)
}

/** One check on the painted frames of a clip. A rule only reads: lint never changes the art. */
interface LintRule {
    /** The rule's name, as reports show it. */
    val name: String

    /** The findings in [frames]; a finding's frame is its index in [frames]. */
    fun check(frames: List<Raster>, context: LintContext): List<LintFinding>
}

/**
 * What a lint run found, in a fixed order: frame, then rule order, then `y`, then `x`.
 *
 * @property findings every finding in that order
 * @property counts the number of findings per rule, in rule order, with zero for a clean rule
 * @property skipped the indexes of the frames that were not painted and so not checked
 */
class LintReport(val findings: List<LintFinding>, val counts: Map<String, Int>, val skipped: List<Int>) {
    /** Whether no rule found anything. */
    val clean: Boolean get() = findings.isEmpty()
}
