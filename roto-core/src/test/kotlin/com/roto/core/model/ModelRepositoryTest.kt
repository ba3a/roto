package com.roto.core.model

import com.roto.core.image.Raster
import com.roto.core.profile.ProfileFolder
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ModelRepositoryTest {

    private val dir = createTempDirectory("roto-models")
    private val folder = ProfileFolder(dir)
    private val repository = ModelRepository(folder)

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun `save then load gives back an equal model and lists its key`() {
        val model = model()
        assertEquals(emptyList(), repository.keys())

        repository.save(model)

        assertEquals(model, repository.load("walk_east"))
        assertEquals(listOf("walk_east"), repository.keys())
        assertTrue(repository.exists("walk_east"))
    }

    @Test
    fun `the hash changes when one pixel or one duration changes`() {
        repository.save(model())
        val base = repository.contentHash("walk_east")

        repository.save(model(pixel = 0xFF00FF00.toInt()))
        val pixel = repository.contentHash("walk_east")
        repository.save(model(duration = 90))
        val duration = repository.contentHash("walk_east")
        repository.save(model())

        assertNotEquals(base, pixel)
        assertNotEquals(base, duration)
        assertNotEquals(pixel, duration)
        assertEquals(base, repository.contentHash("walk_east"))
    }

    @Test
    fun `a corrupt frame fails naming the file`() {
        repository.save(model())
        val frame = folder.modelFolder("walk_east").resolve("001.png")
        Files.write(frame, byteArrayOf(1, 2, 3))

        val failure = assertFailsWith<ModelException> { repository.load("walk_east") }

        assertTrue(failure.message!!.contains("001.png"), failure.message)
    }

    @Test
    fun `a missing frame or model json fails naming the file`() {
        repository.save(model())
        val modelFolder = folder.modelFolder("walk_east")
        Files.delete(modelFolder.resolve("000.png"))
        assertTrue(assertFailsWith<ModelException> { repository.load("walk_east") }.message!!.contains("000.png"))

        Files.delete(modelFolder.resolve("model.json"))
        assertTrue(assertFailsWith<ModelException> { repository.load("walk_east") }.message!!.contains("model.json"))
    }

    @Test
    fun `saving a shorter model over a longer one leaves no stale frame`() {
        repository.save(model())
        repository.save(model(frames = 1))

        assertFalse(folder.modelFolder("walk_east").resolve("001.png").exists())
        assertEquals(1, repository.load("walk_east").frames.size)
    }

    @Test
    fun `remove deletes the folder`() {
        repository.save(model())

        repository.remove("walk_east")

        assertFalse(folder.modelFolder("walk_east").exists())
        assertEquals(emptyList(), repository.keys())
        repository.remove("walk_east")
    }

    private fun model(pixel: Int = 0xFFFF0000.toInt(), duration: Int = 80, frames: Int = 2): ModelClip =
        ModelClip(
            "walk",
            "east",
            List(frames) { index ->
                ModelFrame(Raster.of(3, 2) { x, y -> if (x == index && y == 0) pixel else 0 }, duration)
            },
            loop = true,
            origin = "walk.ase",
        )
}
