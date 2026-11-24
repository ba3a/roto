package com.roto.app

import com.roto.core.image.PngCodec
import com.roto.core.image.Raster
import com.roto.core.model.ModelRepository
import com.roto.core.model.ModelSourceKind
import com.roto.core.profile.ProfileFolder
import com.roto.core.project.Canvas
import com.roto.core.project.MirrorPair
import com.roto.core.project.ProjectConfig
import com.roto.core.template.PathTemplate
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelImportModelTest {

    private lateinit var root: Path
    private lateinit var folder: ProfileFolder
    private lateinit var model: ModelImportModel

    private val config = ProjectConfig(
        id = "demo",
        palette = "palette.gpl",
        ramps = "ramps.json",
        facings = listOf("east", "west"),
        mirrorPairs = listOf(MirrorPair("east", "west")),
        actions = listOf("walk", "idle"),
        canvas = Canvas(width = 3, height = 4, groundRow = 3),
        output = PathTemplate("art/{profile}/{action}_{facing:lower}/{frame:000}.png"),
        manifest = PathTemplate("art/{profile}/{action}_{facing:lower}/clip.json"),
        afterPublish = emptyList(),
    )

    @BeforeTest
    fun setUp() {
        root = createTempDirectory("roto-model-import")
        folder = ProfileFolder(root.resolve("profile"))
        model = ModelImportModel(config, folder)
    }

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    private fun frames(name: String, size: Pair<Int, Int> = 3 to 4): Path {
        val dir = root.resolve(name).createDirectories()
        repeat(2) { frame ->
            val raster = Raster.of(size.first, size.second) { x, _ -> -1 - frame - x }
            PngCodec.write(raster, dir.resolve("00$frame.png"))
        }
        return dir
    }

    private fun choose(path: Path) = runBlocking { model.choose(path) }

    @Test
    fun `a folder proposes a row whose name reads as action and facing`() {
        choose(frames("walk_east"))
        val row = model.state.rows.single()
        assertEquals(ModelSourceKind.PNG_FOLDER, model.state.kind)
        assertEquals("walk", row.action)
        assertEquals("east", row.facing)
        assertNull(model.problem(row))
        assertTrue(model.canImport)
    }

    @Test
    fun `a name outside the project leaves the row invalid and blocks the import`() {
        choose(frames("run_north"))
        val row = model.state.rows.single()
        assertNull(row.action)
        assertEquals("Choose an action", model.problem(row))
        assertFalse(model.canImport)
        model.setAction(0, "walk")
        assertEquals("Choose a facing", model.problem(model.state.rows.single()))
        model.setFacing(0, "west")
        assertTrue(model.canImport)
    }

    @Test
    fun `an action outside the project is invalid`() {
        choose(frames("walk_east"))
        model.setAction(0, "fly")
        assertEquals("Unknown action 'fly'", model.problem(model.state.rows.single()))
        assertFalse(model.canImport)
    }

    @Test
    fun `a zero duration is invalid`() {
        choose(frames("walk_east"))
        model.setRowDuration(0, 0)
        assertEquals("A frame lasts at least 1 ms", model.problem(model.state.rows.single()))
        assertFalse(model.canImport)
    }

    @Test
    fun `an existing key needs confirmation and editing a row withdraws it`() {
        choose(frames("walk_east"))
        assertTrue(runBlocking { model.import() })
        choose(frames("walk_east"))
        assertTrue(model.replaces(model.state.rows.single()))
        assertTrue(model.needsConfirmation)
        assertFalse(model.canImport)
        model.confirmReplace(true)
        assertTrue(model.canImport)
        model.setLoop(0, false)
        assertFalse(model.canImport)
    }

    @Test
    fun `an import writes the models with the edits laid over the source`() {
        choose(frames("walk_east"))
        model.setRowDuration(0, 70)
        model.setLoop(0, false)
        assertTrue(runBlocking { model.import() })
        assertEquals(listOf("walk_east"), model.state.imported)
        val saved = ModelRepository(folder).load("walk_east")
        assertEquals(listOf(70, 70), saved.frames.map { it.durationMs })
        assertFalse(saved.loop)
    }

    @Test
    fun `a failure shows the exception message and imports nothing`() {
        choose(frames("walk_east", 5 to 5))
        assertFalse(runBlocking { model.import() })
        val message = assertNotNull(model.state.message)
        assertTrue("3x4" in message, message)
        assertFalse(ModelRepository(folder).exists("walk_east"))
    }

    @Test
    fun `a file that is no source shows why`() {
        val file = root.resolve("notes.txt").also { it.writeBytes(byteArrayOf(1)) }
        choose(file)
        assertTrue("not a PNG folder" in assertNotNull(model.state.message))
        assertFalse(model.canImport)
    }

    @Test
    fun `an atlas lists its rows and two rows with one key are both invalid`() {
        val atlas = root.resolve("a.atlas")
        val region = "\n  rotate: false\n  xy: 0, 0\n  size: 3, 4\n  orig: 3, 4\n  offset: 0, 0\n  index: -1\n"
        atlas.writeText(
            "a.png\nsize: 6,4\nformat: RGBA8888\nfilter: Nearest,Nearest\nrepeat: none\n" +
                "walk_east_000$region" + "walk_west_000$region" + "stray$region",
        )
        PngCodec.write(Raster.of(6, 4) { _, _ -> -1 }, root.resolve("a.png"))
        choose(atlas)
        assertEquals(ModelSourceKind.ATLAS, model.state.kind)
        assertEquals(listOf("walk_east", "walk_west"), model.state.rows.map { it.row.id })
        assertEquals(listOf("stray"), model.state.skipped)
        assertTrue(model.canImport)
        model.setFacing(1, "east")
        assertTrue(model.state.rows.all { model.problem(it) == "Another row is also walk_east" })
        assertFalse(model.canImport)
    }

    private fun atlas(vararg names: String): Path {
        val atlas = root.resolve("a.atlas")
        val region = "\n  rotate: false\n  xy: 0, 0\n  size: 3, 4\n  orig: 3, 4\n  offset: 0, 0\n  index: -1\n"
        atlas.writeText(
            "a.png\nsize: 6,4\nformat: RGBA8888\nfilter: Nearest,Nearest\nrepeat: none\n" +
                names.joinToString("") { "$it$region" },
        )
        PngCodec.write(Raster.of(6, 4) { _, _ -> -1 }, root.resolve("a.png"))
        return atlas
    }

    @Test
    fun `a pattern that matches nothing gives no rows and a fitting one gives them`() {
        choose(atlas("walk-east-0"))
        assertTrue(model.state.rows.isEmpty())
        assertEquals(listOf("walk-east-0"), model.state.skipped)
        runBlocking { model.setPattern("{action}-{facing:lower}-{frame}") }
        assertEquals(listOf("walk_east"), model.state.rows.map { it.row.id })
        assertTrue(model.canImport)
    }

    @Test
    fun `an atlas duration reaches the imported frames`() {
        choose(atlas("walk_east_000", "walk_east_001"))
        runBlocking { model.setDuration(55) }
        assertTrue(runBlocking { model.import() })
        assertEquals(listOf(55, 55), ModelRepository(folder).load("walk_east").frames.map { it.durationMs })
    }

    @Test
    fun `an edited action survives a new pattern`() {
        choose(atlas("walk_east_000"))
        model.setAction(0, "idle")
        runBlocking { model.setPattern("{action}_{facing:lower}_{frame:000}") }
        assertEquals("idle", model.state.rows.single().action)
    }

    @Test
    fun `a bad pattern shows its message and the model stays usable`() {
        choose(atlas("walk_east_000"))
        runBlocking { model.setPattern("{nope}") }
        assertNotNull(model.state.message)
        runBlocking { model.setPattern("{action}_{facing:lower}_{frame:000}") }
        assertEquals(listOf("walk_east"), model.state.rows.map { it.row.id })
    }
}
