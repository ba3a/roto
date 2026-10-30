package com.roto.app

import com.roto.app.ClipBench.Companion.CLIP
import com.roto.app.ClipBench.Companion.PHOTO_FRAME
import com.roto.app.ClipBench.Companion.png
import com.roto.app.ClipBench.Companion.result
import com.roto.app.ClipBench.Companion.wall
import com.roto.core.framebox.ScaleMarks
import com.roto.core.framebox.ScaleSettings
import com.roto.core.image.Raster
import com.roto.core.profile.Clip
import com.roto.core.profile.PlateSource
import com.roto.core.profile.Profile
import com.roto.core.publish.ArtProgress
import com.roto.core.zone.Zone
import com.roto.core.zone.ZoneSample
import com.roto.core.zone.ZoneSettings
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
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

class StillModelTest {

    private lateinit var tempRoot: Path
    private lateinit var bench: ClipBench
    private lateinit var photo: Path
    private lateinit var wallPhoto: Path

    /** The block one photo shows: inside it the sample lands, and head and feet mark its ends. */
    private val zoning = ZoneSettings(listOf(Zone("body", "skin", listOf(ZoneSample(0, 26, 20)))))
    private val scale = ScaleSettings(bodyHeight = 30, marks = ScaleMarks("walk_EAST", 0, headTopY = 10, feetY = 39))

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-still")
        bench = ClipBench(tempRoot)
        bench.save(Profile())
        bench.photo = { name -> png(if ("wall" in name) wall() else ClipBench.frame(PHOTO_FRAME)) }
        photo = tempRoot.resolve("pose.jpg").also { it.writeBytes(byteArrayOf(1)) }
        wallPhoto = tempRoot.resolve("wall.jpg").also { it.writeBytes(byteArrayOf(2)) }
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `a photo starts on the first action and facing, without a wall`() {
        val still = opened()

        runBlocking { still.load() }

        assertEquals(ClipBench.frame(PHOTO_FRAME), still.state.picture)
        assertEquals("walk", still.state.action)
        assertEquals("EAST", still.state.facing)
        assertNull(still.state.wall)
        assertNull(still.warning)
    }

    @Test
    fun `nothing is saved until the wall is chosen`() {
        val still = opened()

        assertFalse(runBlocking { still.save() })

        assertEquals(emptyList(), stored())
    }

    @Test
    fun `saving files the photo and its wall, nothing being copied before that`() {
        val still = opened()
        runBlocking { still.chooseWall(wallPhoto) }
        assertEquals(wall(), still.state.wallPicture)
        assertFalse(Files.exists(sources().resolve("pose.jpg")))

        still.chooseFacing("WEST")
        assertTrue(runBlocking { still.save() })

        assertEquals(listOf(Clip.still("walk", "WEST", "pose.jpg", "wall.jpg")), stored())
        assertTrue(Files.exists(sources().resolve("pose.jpg")))
        assertTrue(Files.exists(sources().resolve("wall.jpg")))
    }

    @Test
    fun `a wall that does not line up says so, and nothing is saved`() {
        bench.photo = { name -> png(if ("wall" in name) Raster.of(30, 50) { _, _ -> 0 } else ClipBench.frame(0)) }
        val still = opened()
        runBlocking { still.chooseWall(wallPhoto) }

        assertFalse(runBlocking { still.save() })

        assertContains(still.state.message!!, "30x50")
        assertFalse(still.state.saving)
        assertEquals(emptyList(), stored())
    }

    @Test
    fun `a photo ffmpeg cannot read says so, and a good wall after a bad one clears that`() {
        bench.photo = { result("", code = 1) }
        val still = opened()

        runBlocking { still.load() }
        assertContains(still.state.message!!, "exit code 1")

        bench.photo = { png(wall()) }
        runBlocking { still.chooseWall(wallPhoto) }
        assertNull(still.state.message)
    }

    @Test
    fun `filing over a filmed clip warns what it costs`() {
        bench.save(Profile(clips = listOf(CLIP)))

        assertContains(opened().warning!!, "walk_EAST is already filmed")
    }

    @Test
    fun `a still sets up with its one key frame, under the brush, and takes another wall`() {
        val setup = setUp(saved())

        runBlocking {
            setup.open()
            setup.refresh()
            setup.loadProposal()
        }

        assertNull(setup.state.message)
        assertEquals(1, setup.state.frameCount)
        assertEquals(listOf(0, 1), setup.state.proposal)
        assertTrue(setup.state.canBrush)
        assertEquals(wall(), setup.state.plate)
        assertTrue(assertNotNull(setup.state.mask).count() > 0)

        val other = tempRoot.resolve("wall-2.jpg").also { it.writeBytes(byteArrayOf(3)) }
        runBlocking {
            setup.useWallPhoto(other)
            setup.save()
        }
        assertEquals(PlateSource.Photo("wall-2.jpg"), bench.storedClip().plate)
    }

    @Test
    fun `a wall that no longer lines up leaves the plate as it was`() {
        val setup = setUp(saved())
        bench.photo = { png(Raster.of(30, 50) { _, _ -> 0 }) }
        val other = tempRoot.resolve("wall-2.jpg").also { it.writeBytes(byteArrayOf(3)) }

        runBlocking { setup.useWallPhoto(other) }

        assertContains(setup.state.message!!, "30x50")
        assertEquals(PlateSource.Photo("wall.jpg"), setup.state.clip.plate)
    }

    @Test
    fun `a still draws, and hands off a single frame`() {
        val zoned = saved().copy(zoning = zoning)
        bench.save(Profile(listOf("skin"), scale, listOf(zoned)))
        bench.home.draw(zoned)
        val draw = assertNotNull((bench.home.screen as? Screen.Drawing)?.model)

        runBlocking {
            draw.open()
            draw.refreshDraft()
        }
        assertNull(draw.state.message)
        assertNotNull(draw.state.draft)

        bench.home.handOff()
        val panel = assertNotNull((bench.home.screen as? Screen.HandingOff)?.model)
        runBlocking {
            panel.refresh()
            panel.render()
        }
        assertNull(panel.state.message)
        assertEquals(ArtProgress.Painted(0, 1), panel.state.clips.single().art)
    }

    private fun opened(): StillModel {
        bench.home.importPhoto(photo)
        return assertNotNull((bench.home.screen as? Screen.AddingStill)?.model)
    }

    /** The still as filed through the screen, in the place [ClipBench.storedClip] reads. */
    private fun saved(): Clip {
        val still = opened()
        runBlocking {
            still.chooseWall(wallPhoto)
            still.save()
        }
        bench.home.closeClip()
        return bench.storedClip()
    }

    private fun setUp(clip: Clip): ClipSetupModel {
        bench.home.setUp(clip)
        return assertIs<Screen.SettingUp>(bench.home.screen).model
    }

    private fun stored(): List<Clip> = bench.repository.load("hero").clips

    private fun sources(): Path = bench.repository.folder("hero").sourcesFolder
}
