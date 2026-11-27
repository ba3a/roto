package com.roto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roto.core.coverage.CoverageCell
import com.roto.core.coverage.CoverageMatrix
import com.roto.core.coverage.CoverageNext
import com.roto.core.coverage.CoverageReader
import com.roto.core.coverage.CoverageState
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Where the overview reads one cell from: a [CoverageReader] off the UI thread, or a stand-in in a test. */
fun interface OverviewCells {
    /** The cell of [action] and [facing]. */
    suspend fun cell(action: String, facing: String): CoverageCell
}

/** Reads each cell of [reader] off the UI thread; a cell that cannot be read shows as unreadable. */
fun overviewCells(reader: CoverageReader): OverviewCells = OverviewCells { action, facing ->
    var problem: String? = null
    offUiThread({ problem = it }) { reader.cell(action, facing) }
        ?: CoverageCell(CoverageState.Unreadable(problem ?: "The cell cannot be read"))
}

/** The screens a cell can open; the home model opens them. */
interface OverviewActions {
    /** Opens the import of a clip for [action] and [facing]. */
    fun importClip(action: String, facing: String)

    /** Opens the model import list. */
    fun importModel()

    /** Opens the setup of [clip]. */
    fun setUp(clip: Clip)

    /** Opens the handoff screen. */
    fun handOff()
}

/** One cell of the overview: [Loading] until its reader finishes, then [Ready]. */
sealed interface OverviewCell {
    /** Its reader has not finished. */
    data object Loading : OverviewCell

    /**
     * Its reader finished.
     *
     * @property cell what it read
     */
    data class Ready(val cell: CoverageCell) : OverviewCell
}

/**
 * What the overview shows: the [actions] (rows) and [facings] (columns), each cell, and [next] once every
 * cell has been read.
 *
 * @property actions the rows, in `roto.json` order
 * @property facings the columns, in `roto.json` order
 * @property cells the cells, by action and facing
 */
data class OverviewState(
    val actions: List<String>,
    val facings: List<String>,
    val cells: Map<Pair<String, String>, OverviewCell>,
) {
    /** The cell of [action] and [facing]. */
    fun cell(action: String, facing: String): OverviewCell = cells[action to facing] ?: OverviewCell.Loading

    /** The cells as a matrix, or null while any is loading. */
    val matrix: CoverageMatrix?
        get() {
            val ready = cells.mapNotNull { (key, cell) -> (cell as? OverviewCell.Ready)?.let { key to it.cell } }
            return if (ready.size == actions.size * facings.size) {
                CoverageMatrix(actions, facings, ready.toMap())
            } else {
                null
            }
        }

    /** What to do next, or null while cells load or when nothing is left. */
    val next: CoverageNext? get() = matrix?.next
}

/**
 * The overview's state and what a click does: cells fill in as their readers finish, and a cell opens the
 * screen that moves it forward through [screens]. The rules are `roto-core`'s.
 *
 * @property profile the profile shown
 * @property cells where each cell is read from
 * @property screens what opens the screens
 */
class OverviewModel(
    private val profile: Profile,
    actions: List<String>,
    facings: List<String>,
    private val cells: OverviewCells,
    private val screens: OverviewActions,
) {
    /** What the overview shows. */
    var state by mutableStateOf(OverviewState(actions, facings, emptyMap()))
        private set

    /** Reads every cell at once; each shows as soon as its own read is done. */
    suspend fun load() {
        val grid = state.actions.flatMap { action -> state.facings.map { facing -> action to facing } }
        state = state.copy(cells = grid.associateWith { OverviewCell.Loading })
        coroutineScope {
            for ((action, facing) in grid) {
                launch {
                    val cell = cells.cell(action, facing)
                    state = state.copy(cells = state.cells + ((action to facing) to OverviewCell.Ready(cell)))
                }
            }
        }
    }

    /** Opens the screen that moves the cell of [action] and [facing] forward; nothing while it loads. */
    fun open(action: String, facing: String) {
        val ready = state.cell(action, facing) as? OverviewCell.Ready ?: return
        when (ready.cell.state) {
            CoverageState.NotFilmed -> screens.importClip(action, facing)
            CoverageState.Model -> screens.importModel()
            CoverageState.Setup -> profile.clip(action, facing)?.let(screens::setUp)
            CoverageState.Mirrored, CoverageState.Finished, CoverageState.Published,
            is CoverageState.Painting, is CoverageState.OutOfDate, is CoverageState.Unreadable,
            -> screens.handOff()
        }
    }

    /** Opens the screen of the [OverviewState.next] cell, if there is one. */
    fun openNext() {
        val next = state.next ?: return
        open(next.action, next.facing)
    }
}
