package com.roto.app

import com.roto.core.coverage.CoverageCell
import com.roto.core.coverage.CoverageState
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import com.roto.core.profile.TimeRange
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverviewModelTest {

    private class Screens : OverviewActions {
        val opened = mutableListOf<String>()
        override fun importClip(action: String, facing: String) {
            opened += "import $action $facing"
        }
        override fun importModel() {
            opened += "model"
        }
        override fun setUp(clip: Clip) {
            opened += "setup ${clip.action} ${clip.facing}"
        }
        override fun handOff() {
            opened += "handoff"
        }
    }

    private val actions = listOf("walk", "idle")
    private val facings = listOf("left", "right")
    private val clip = Clip(action = "walk", facing = "left", source = "walk.mp4", trim = TimeRange(0, 400))
    private val profile = Profile(clips = listOf(clip))

    private fun model(screens: Screens, cells: OverviewCells) = OverviewModel(profile, actions, facings, cells, screens)

    private fun fixed(vararg states: Pair<Pair<String, String>, CoverageState>): OverviewCells {
        val map = states.toMap()
        return OverviewCells { action, facing -> CoverageCell(map[action to facing] ?: CoverageState.NotFilmed) }
    }

    @Test
    fun `cells appear one by one as readers finish`() = runBlocking {
        val gates = actions.flatMap { a -> facings.map { f -> (a to f) to CompletableDeferred<CoverageCell>() } }
            .toMap()
        val model = model(Screens()) { a, f -> gates.getValue(a to f).await() }
        val job = async(start = CoroutineStart.UNDISPATCHED) { model.load() }

        assertTrue(model.state.cells.values.all { it == OverviewCell.Loading })
        gates.getValue("idle" to "left").complete(CoverageCell(CoverageState.Setup))
        repeat(5) { yield() }

        assertEquals(OverviewCell.Ready(CoverageCell(CoverageState.Setup)), model.state.cell("idle", "left"))
        assertEquals(OverviewCell.Loading, model.state.cell("walk", "left"))
        assertNull(model.state.next)

        val rest = gates.filterKeys { it != ("idle" to "left") }.values
        rest.forEach { it.complete(CoverageCell(CoverageState.Finished)) }
        job.await()

        assertEquals("walk", model.state.next?.action)
    }

    @Test
    fun `each state opens its screen`() = runBlocking {
        val screens = Screens()
        val model = model(
            screens,
            fixed(
                ("walk" to "left") to CoverageState.Setup,
                ("walk" to "right") to CoverageState.Mirrored,
                ("idle" to "left") to CoverageState.Model,
            ),
        )
        model.load()

        model.open("walk", "left")
        model.open("walk", "right")
        model.open("idle", "left")
        model.open("idle", "right")

        assertEquals(listOf("setup walk left", "handoff", "model", "import idle right"), screens.opened)
    }

    @Test
    fun `handoff states open the handoff and a loading cell opens nothing`() = runBlocking {
        val screens = Screens()
        val model = model(screens, fixed(("walk" to "left") to CoverageState.Painting(1, 3)))

        model.open("walk", "left")
        model.load()
        model.open("walk", "left")

        assertEquals(listOf("handoff"), screens.opened)
    }

    @Test
    fun `next opens its cell`() = runBlocking {
        val screens = Screens()
        val model = model(screens, fixed(("walk" to "left") to CoverageState.Painting(1, 3)))
        model.load()

        model.openNext()

        assertEquals(listOf("handoff"), screens.opened)
    }
}
