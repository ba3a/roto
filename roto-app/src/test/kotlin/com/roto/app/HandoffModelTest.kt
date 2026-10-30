package com.roto.app

import com.roto.app.ClipBench.Companion.CLIP
import com.roto.app.ClipBench.Companion.result
import com.roto.core.ase.AseCodec
import com.roto.core.framebox.ScaleMarks
import com.roto.core.framebox.ScaleSettings
import com.roto.core.handoff.HandoffMerge
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import com.roto.core.publish.ArtProgress
import com.roto.core.zone.Zone
import com.roto.core.zone.ZoneSample
import com.roto.core.zone.ZoneSettings
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandoffModelTest {

    private lateinit var tempRoot: Path
    private lateinit var bench: ClipBench
    private lateinit var panel: HandoffModel

    private val zoned = CLIP.copy(zoning = ZoneSettings(listOf(Zone("body", "skin", listOf(ZoneSample(0, 14, 20))))))
    private val filmedWest = zoned.copy(facing = "WEST")
    private val scale = ScaleSettings(bodyHeight = 30, marks = ScaleMarks(CLIP.id, 0, headTopY = 10, feetY = 39))

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-handoff")
        bench = ClipBench(tempRoot)
        open(Profile(listOf("skin"), scale, listOf(zoned)))
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `a clip starts unrendered, with nothing to publish`() {
        val row = panel.state.clips.single()

        assertEquals(ArtProgress.NoHandoff, row.art)
        assertEquals("WEST", row.mirrorsInto)
        assertEquals(emptyList(), panel.state.finished)
    }

    @Test
    fun `a render writes the handoff, and what is painted shows on the next read`() {
        render()

        assertEquals("Rendered 1 clip.", panel.state.report)
        val frames = assertIs<ArtProgress.Painted>(art()).total
        assertEquals(ArtProgress.Painted(0, frames), art())

        paint(zoned, frames = 1)
        refresh()
        assertEquals(ArtProgress.Painted(1, frames), art())
        assertEquals(emptyList(), panel.state.finished)

        paint(zoned)
        refresh()
        assertEquals(ArtProgress.Painted(frames, frames), art())
        assertEquals(listOf(zoned), panel.state.finished)
    }

    @Test
    fun `rendering again keeps what is painted`() {
        render()
        paint(zoned)

        render()

        assertTrue(art().finished)
    }

    @Test
    fun `a render that cannot run says why, and the panel stays usable`() {
        open(Profile(listOf("skin"), scale = null, listOf(zoned)))

        render()

        assertContains(panel.state.message!!, "scale is not marked")
        assertNull(panel.state.running)
        assertNull(panel.state.report)
    }

    @Test
    fun `a publish takes the finished clips and runs afterPublish`() {
        open(Profile(listOf("skin"), scale, listOf(zoned, filmedWest)))
        runBlocking { panel.render(listOf(zoned)) }
        paint(zoned)
        refresh()

        runBlocking { panel.publish() }

        assertEquals("Published 1 clip, and afterPublish ran.", panel.state.report)
        assertTrue(Files.isRegularFile(bench.game.resolve("art/hero/walk_east/000.png")))
        assertFalse(Files.exists(bench.game.resolve("art/hero/walk_west")))
        assertEquals(listOf("sync", "hero"), bench.commands.single())
    }

    @Test
    fun `a failing afterPublish stays on the panel`() {
        bench.program = { result("", code = 1) }
        render()
        paint(zoned)
        refresh()

        runBlocking { panel.publish() }

        assertContains(panel.state.message!!, "afterPublish failed")
        assertNull(panel.state.report)
    }

    @Test
    fun `a clip may be opted in to mirroring only while its pair is not filmed`() {
        assertEquals("WEST", panel.state.clips.single().mirrorsInto)

        runBlocking { panel.setMirror(zoned, true) }
        assertTrue(bench.storedClip().mirror)
        assertTrue(panel.state.clips.single().clip.mirror)

        render()
        paint(zoned)
        refresh()
        runBlocking { panel.publish() }
        assertTrue(Files.isRegularFile(bench.game.resolve("art/hero/walk_west/000.png")))

        open(Profile(listOf("skin"), scale, listOf(zoned, filmedWest)))
        assertNull(panel.state.clips.first().mirrorsInto)
    }

    @Test
    fun `a handoff opens in the system's editor until one is chosen, and is read again afterwards`() {
        render()
        val handoff = bench.repository.folder("hero").handoffFile(zoned)
        val opened = mutableListOf<Path>()
        bench.systemEditor = Editor { file ->
            opened.add(file)
            paint(zoned)
        }

        runBlocking { panel.paint(zoned) }

        assertEquals(listOf(handoff), opened)
        assertTrue(art().finished)
    }

    @Test
    fun `a chosen editor is run with the handoff`() {
        val program = tempRoot.resolve("libresprite").toAbsolutePath()
        bench.home.chooseEditor(program)
        open(Profile(listOf("skin"), scale, listOf(zoned)))
        render()

        runBlocking { panel.paint(zoned) }

        val handoff = bench.repository.folder("hero").handoffFile(zoned)
        assertEquals(listOf(program.toString(), handoff.toString()), bench.commands.single())
    }

    @Test
    fun `an editor that fails says so`() {
        render()
        bench.systemEditor = Editor { throw IOException("No program for this") }
        runBlocking { panel.paint(zoned) }
        assertEquals("No program for this", panel.state.message)

        bench.home.chooseEditor(tempRoot.resolve("libresprite").toAbsolutePath())
        bench.program = { result("", code = 3) }
        open(Profile(listOf("skin"), scale, listOf(zoned)))
        runBlocking { panel.paint(zoned) }
        assertContains(panel.state.message!!, "exit code 3")
    }

    @Test
    fun `a handoff that cannot be read is named and left out of a publish`() {
        render()
        bench.repository.folder("hero").handoffFile(zoned).writeBytes(byteArrayOf(1, 2, 3))

        refresh()

        assertIs<ArtProgress.Unreadable>(art())
        assertEquals(emptyList(), panel.state.finished)
    }

    @Test
    fun `a damaged profile stays on the panel as a message`() {
        bench.repository.folder("hero").profileFile.writeText("{")

        refresh()

        assertContains(panel.state.message!!, "profile.json")
    }

    private fun open(profile: Profile) {
        bench.save(profile)
        bench.home.handOff()
        panel = assertNotNull((bench.home.screen as? Screen.HandingOff)?.model)
        refresh()
    }

    private fun art() = panel.state.clips.first().art

    private fun refresh() = runBlocking { panel.refresh() }

    private fun render() = runBlocking { panel.render() }

    /** The artist's part: the first [frames] frames' drafts copied into the `art` layer, all of them by default. */
    private fun paint(clip: Clip, frames: Int = Int.MAX_VALUE) {
        val handoff = bench.repository.folder("hero").handoffFile(clip)
        val sprite = AseCodec.read(handoff)
        val draft = sprite.layers.indexOfFirst { it.name == HandoffMerge.DRAFT }
        val art = sprite.layers.indexOfFirst { it.name == HandoffMerge.ART }
        val painted = sprite.frames.mapIndexed { index, frame ->
            val cels = frame.cels.filter { it.layerIndex != art }
            val copy = frame.cels.single { it.layerIndex == draft }.copy(layerIndex = art)
            frame.copy(cels = if (index < frames) cels + copy else cels)
        }
        AseCodec.write(sprite.copy(frames = painted), handoff)
    }
}
