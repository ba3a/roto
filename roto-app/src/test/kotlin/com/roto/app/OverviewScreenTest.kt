package com.roto.app

import com.roto.core.coverage.CoverageCell
import com.roto.core.coverage.CoverageState
import kotlin.test.Test
import kotlin.test.assertEquals

class OverviewScreenTest {
    @Test
    fun `a handoff in progress shows how many frames are painted`() {
        assertEquals("Painting 2/6", stateLabel(CoverageState.Painting(2, 6)))
        assertEquals("Out of date", stateLabel(CoverageState.OutOfDate("art changed")))
    }

    @Test
    fun `the next line reads while cells load and names the next cell once they are read`() {
        val loading = OverviewState(listOf("walk"), listOf("east"), emptyMap())
        assertEquals("Next: reading the profile…", nextLabel(loading))

        val ready = loading.withState(CoverageState.Setup)
        assertEquals("Next: walk · east (Setup)", nextLabel(ready))

        val done = loading.withState(CoverageState.Published)
        assertEquals("Everything is published", nextLabel(done))
    }

    private fun OverviewState.withState(state: CoverageState) =
        copy(cells = mapOf(("walk" to "east") to OverviewCell.Ready(CoverageCell(state))))

    @Test
    fun `an unreadable cell is listed with its reason`() {
        val state = OverviewState(
            listOf("walk"),
            listOf("east"),
            mapOf(("walk" to "east") to OverviewCell.Ready(CoverageCell(CoverageState.Unreadable("bad file")))),
        )
        assertEquals(listOf("walk · east: bad file"), problems(state))
    }
}
