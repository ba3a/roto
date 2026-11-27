package com.roto.core.coverage

/** Where one action and facing of a profile stands. */
sealed interface CoverageState {

    /** No clip of the profile has this action and facing. */
    data object NotFilmed : CoverageState

    /** Not filmed, but the paired facing is filmed and mirroring is on for it. */
    data object Mirrored : CoverageState

    /** Not filmed, but a model clip exists for it. */
    data object Model : CoverageState

    /** Filmed, with no handoff yet. */
    data object Setup : CoverageState

    /**
     * The handoff has art on [painted] of its [total] frames.
     *
     * @property painted how many frames hold art
     * @property total how many frames the handoff has
     */
    data class Painting(val painted: Int, val total: Int) : CoverageState {
        init {
            require(total > 0) { "A handoff has at least one frame, got $total" }
            require(painted in 0 until total) { "Painted $painted must be below $total, or the clip is finished" }
        }
    }

    /** Art on every frame, not published. */
    data object Finished : CoverageState

    /** Published, and the published frames match the art. */
    data object Published : CoverageState

    /**
     * Published, but the art or the retime now differs.
     *
     * @property reason what differs
     */
    data class OutOfDate(val reason: String) : CoverageState

    /**
     * The handoff cannot be read.
     *
     * @property reason why it cannot be read
     */
    data class Unreadable(val reason: String) : CoverageState
}

/**
 * One cell of the coverage matrix.
 *
 * @property state where the cell stands
 * @property hasModel whether a model clip exists for the cell, shown as a small mark beside a filmed clip's state
 */
data class CoverageCell(val state: CoverageState, val hasModel: Boolean = false)
