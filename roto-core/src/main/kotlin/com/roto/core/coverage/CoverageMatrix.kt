package com.roto.core.coverage

import com.roto.core.project.ProjectConfig

/**
 * The cell to work on next.
 *
 * @property action the cell's action
 * @property facing the cell's facing
 * @property state the cell's state
 */
data class CoverageNext(val action: String, val facing: String, val state: CoverageState)

/**
 * Every action (row) and facing (column) of a profile, in `roto.json` order, with a cell for each pair.
 *
 * @property actions the rows, in `roto.json` order
 * @property facings the columns, in `roto.json` order
 */
class CoverageMatrix(
    val actions: List<String>,
    val facings: List<String>,
    cells: Map<Pair<String, String>, CoverageCell>,
) {

    private val cells: Map<Pair<String, String>, CoverageCell> = cells.toMap()

    init {
        for ((action, facing) in this.cells.keys) {
            require(action in actions) { "Cell action '$action' is not in the actions" }
            require(facing in facings) { "Cell facing '$facing' is not in the facings" }
        }
        for (action in actions) {
            for (facing in facings) {
                require((action to facing) in this.cells) { "No cell for $action $facing" }
            }
        }
    }

    /** The cell of [action] and [facing], which both must be in the matrix. */
    fun cell(action: String, facing: String): CoverageCell =
        requireNotNull(cells[action to facing]) { "No cell for $action $facing" }

    /**
     * What to do next, or null when nothing is left.
     *
     * The filmed clip with the most frames painted but not finished comes first, then an out of date one, a
     * finished one, a clip with no handoff and an unfilmed cell. Ties go to the lower `roto.json` position
     * (action, then facing).
     */
    val next: CoverageNext? by lazy { findNext() }

    private fun findNext(): CoverageNext? = actions
        .flatMap { action -> facings.map { facing -> CoverageNext(action, facing, cell(action, facing).state) } }
        .filter { rankOf(it.state) != null }
        .minWithOrNull(compareBy<CoverageNext> { rankOf(it.state) }.thenByDescending { paintedOf(it.state) })

    /** Builds the matrix for `config`'s actions and facings. */
    companion object {

        /** The matrix of `config`, with [cells] by action and facing. */
        fun of(config: ProjectConfig, cells: Map<Pair<String, String>, CoverageCell>): CoverageMatrix =
            CoverageMatrix(config.actions, config.facings, cells)
    }
}

/** The order Next takes states in: the first entry wins. */
private enum class Rank { PAINTING, OUT_OF_DATE, FINISHED, SETUP, NOT_FILMED }

private fun rankOf(state: CoverageState): Rank? = when (state) {
    is CoverageState.Painting -> Rank.PAINTING
    is CoverageState.OutOfDate -> Rank.OUT_OF_DATE
    CoverageState.Finished -> Rank.FINISHED
    CoverageState.Setup -> Rank.SETUP
    CoverageState.NotFilmed -> Rank.NOT_FILMED
    CoverageState.Mirrored, CoverageState.Model, CoverageState.Published, is CoverageState.Unreadable -> null
}

private fun paintedOf(state: CoverageState): Int = (state as? CoverageState.Painting)?.painted ?: 0
