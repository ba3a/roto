package com.roto.core.model

import com.roto.core.image.Raster
import com.roto.core.profile.ProfileFolder
import com.roto.core.project.Canvas
import com.roto.core.project.sampleConfig
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelImporterTest {

    private val dir = createTempDirectory("roto-importer")
    private val folder = ProfileFolder(dir.resolve("profile"))
    private val config = sampleConfig().copy(
        facings = listOf("east", "west"),
        mirrorPairs = emptyList(),
        actions = listOf("walk", "idle"),
        canvas = Canvas(3, 4, 3),
    )
    private val importer = ModelImporter(folder, config)

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    private class FakeSource(private val size: Pair<Int, Int> = 3 to 4, private val colour: Int = -1) : ModelSource {
        val row = ModelRow("walk", "walk", "east", 2)
        override fun rows() = listOf(row)
        override fun frames(row: ModelRow) = List(2) { index ->
            Raster.of(size.first, size.second) { x, _ -> colour + index + x }
        }
        override fun durations(row: ModelRow) = listOf(80, 120)
        override fun loop(row: ModelRow) = true
    }

    private fun confirmed(source: FakeSource, action: String = "walk", facing: String = "east") =
        listOf(ConfirmedRow(source.row, action, facing))

    private fun import(source: FakeSource = FakeSource(), replace: Boolean = false, writer: ModelFileWriter? = null) =
        if (writer == null) {
            importer.import(source, confirmed(source), "walk.ase", replace)
        } else {
            importer.import(source, confirmed(source), "walk.ase", replace, writer)
        }

    private fun leftovers(): List<String> =
        folder.path.listDirectoryEntries().map { it.name }.filter { it != "models" }

    @Test
    fun `an import writes the model and returns its key`() {
        assertEquals(listOf("walk_east"), import())

        val model = ModelRepository(folder).load("walk_east")
        assertEquals(listOf(80, 120), model.frames.map { it.durationMs })
        assertTrue(model.loop)
        assertEquals("walk.ase", model.origin)
        assertEquals(emptyList(), leftovers())
    }

    @Test
    fun `model json is written after every frame`() {
        val order = mutableListOf<String>()
        import(writer = { file, write ->
            order.add(file.name)
            write()
        })

        assertEquals(listOf("000.png", "001.png", "model.json"), order)
    }

    @Test
    fun `a failing model json write leaves no folder`() {
        assertFailsWith<ModelException> {
            import(writer = { file, write ->
                if (file.name == "model.json") throw IOException("disk full")
                write()
            })
        }

        assertFalse(folder.modelFolder("walk_east").exists())
        assertEquals(emptyList(), ModelRepository(folder).keys())
        assertEquals(emptyList(), leftovers())
    }

    @Test
    fun `a failed replace keeps the existing model`() {
        import(FakeSource(colour = -100))
        val before = ModelRepository(folder).contentHash("walk_east")

        assertFailsWith<ModelException> {
            import(replace = true, writer = { file, write ->
                if (file.name == "001.png") throw IOException("disk full")
                write()
            })
        }

        assertEquals(before, ModelRepository(folder).contentHash("walk_east"))
    }

    @Test
    fun `an existing key is refused without replace and replaced with it`() {
        import(FakeSource(colour = -100))
        val before = ModelRepository(folder).contentHash("walk_east")

        assertFailsWith<ModelException> { import() }
        assertEquals(before, ModelRepository(folder).contentHash("walk_east"))

        import(replace = true)
        assertTrue(before != ModelRepository(folder).contentHash("walk_east"))
        assertEquals(emptyList(), leftovers())
    }

    @Test
    fun `the same source twice gives byte-identical folders`() {
        import()
        val first = bytes(folder.modelFolder("walk_east"))
        ModelRepository(folder).remove("walk_east")

        import()

        assertEquals(first.keys, bytes(folder.modelFolder("walk_east")).keys)
        first.forEach { (name, content) -> assertContentEquals(content, bytes(folder.modelFolder("walk_east"))[name]) }
    }

    @Test
    fun `an unknown action or facing is refused and nothing is written`() {
        val source = FakeSource()

        assertFailsWith<ModelException> { importer.import(source, confirmed(source, action = "jump"), "x", false) }
        assertFailsWith<ModelException> { importer.import(source, confirmed(source, facing = "north"), "x", false) }

        assertFalse(folder.modelsFolder.exists())
    }

    @Test
    fun `a frame that is not canvas size is refused naming both sizes`() {
        val e = assertFailsWith<ModelException> { import(FakeSource(size = 5 to 4)) }

        assertTrue("5x4" in e.message.orEmpty() && "3x4" in e.message.orEmpty())
        assertEquals(emptyList(), ModelRepository(folder).keys())
    }

    private fun bytes(model: Path): Map<String, ByteArray> =
        model.listDirectoryEntries().associate { it.name to Files.readAllBytes(it) }
}
