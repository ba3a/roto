package com.roto.core.coverage

import com.roto.core.coverage.CoverageState.Finished
import com.roto.core.coverage.CoverageState.Mirrored
import com.roto.core.coverage.CoverageState.Model
import com.roto.core.coverage.CoverageState.NotFilmed
import com.roto.core.coverage.CoverageState.OutOfDate
import com.roto.core.coverage.CoverageState.Painting
import com.roto.core.coverage.CoverageState.Published
import com.roto.core.coverage.CoverageState.Setup
import com.roto.core.coverage.CoverageState.Unreadable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CoverageMatrixTest {

    private val actions = listOf("walk", "idle", "run")
    private val facings = listOf("left", "right")

    private fun grid(cell: (String, String) -> CoverageCell): Map<Pair<String, String>, CoverageCell> =
        actions.flatMap { a -> facings.map { f -> (a to f) to cell(a, f) } }.toMap()

    private fun matrix(vararg states: Pair<Pair<String, String>, CoverageState>): CoverageMatrix {
        val given = states.toMap()
        return CoverageMatrix(actions, facings, grid { a, f -> CoverageCell(given[a to f] ?: Published) })
    }

    @Test
    fun `rows and columns follow the given order`() {
        val m = matrix()
        assertEquals(listOf("walk", "idle", "run"), m.actions)
        assertEquals(listOf("left", "right"), m.facings)
    }

    @Test
    fun `most painted frames come first`() {
        val m = matrix(
            ("walk" to "left") to Finished,
            ("idle" to "left") to Painting(1, 8),
            ("run" to "right") to Painting(5, 8),
            ("run" to "left") to OutOfDate("retime"),
        )
        assertEquals(CoverageNext("run", "right", Painting(5, 8)), m.next)
    }

    @Test
    fun `equal painted counts go to the lower position`() {
        val m = matrix(
            ("idle" to "right") to Painting(3, 8),
            ("idle" to "left") to Painting(3, 4),
            ("run" to "left") to Painting(3, 8),
        )
        assertEquals("idle", m.next?.action)
        assertEquals("left", m.next?.facing)
    }

    @Test
    fun `out of date, finished, setup and not filmed follow in that order`() {
        val positions = actions.flatMap { a -> facings.map { f -> a to f } }
        val ladder = listOf(OutOfDate("art"), Finished, Setup, NotFilmed)
        for (i in ladder.indices) {
            val rest = ladder.drop(i)
            // The highest-ranked state sits last in roto.json order, so only its rank can win.
            val m = matrix(
                *rest.reversed().mapIndexed { n, s -> positions.reversed()[n] to s }.toTypedArray(),
            )
            assertEquals(rest.first(), m.next?.state)
        }
    }

    @Test
    fun `ties within a rank go to the lower position`() {
        val m = matrix(("idle" to "right") to Setup, ("walk" to "right") to Setup, ("run" to "left") to Setup)
        assertEquals(CoverageNext("walk", "right", Setup), m.next)
    }

    @Test
    fun `not filmed comes last`() {
        val m = matrix(("walk" to "left") to NotFilmed, ("run" to "right") to Setup)
        assertEquals(CoverageNext("run", "right", Setup), m.next)
    }

    @Test
    fun `next is null when nothing is left`() {
        val m = matrix(
            ("walk" to "left") to Mirrored,
            ("walk" to "right") to Model,
            ("idle" to "left") to Unreadable("bad"),
        )
        assertNull(m.next)
    }

    @Test
    fun `a model mark does not change the state`() {
        val m = CoverageMatrix(actions, facings, grid { _, _ -> CoverageCell(Published, hasModel = true) })
        assertEquals(true, m.cell("walk", "left").hasModel)
        assertNull(m.next)
    }

    @Test
    fun `a missing cell is refused`() {
        assertFailsWith<IllegalArgumentException> { CoverageMatrix(actions, facings, emptyMap()) }
    }

    @Test
    fun `painting needs fewer painted frames than total`() {
        assertFailsWith<IllegalArgumentException> { Painting(4, 4) }
    }
}
