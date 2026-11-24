package com.roto.core.model

import com.roto.core.image.Raster
import com.roto.core.profile.ProfileFolder
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModelClipTest {

    private fun frame(width: Int = 4, height: Int = 6, ms: Int = 100) = ModelFrame(Raster(width, height), ms)

    private fun clip(
        action: String = "walk",
        facing: String = "east",
        frames: List<ModelFrame> = listOf(frame()),
    ) = ModelClip(action, facing, frames, loop = true, origin = "walk.ase")

    @Test
    fun `a valid clip has a key, a size and a manifest`() {
        val clip = clip(frames = listOf(frame(ms = 80), frame(ms = 120)))

        assertEquals("walk_east", clip.key)
        assertEquals(4, clip.width)
        assertEquals(6, clip.height)
        assertEquals(ModelManifest("walk", "east", true, listOf(80, 120), "walk.ase"), clip.manifest())
    }

    @Test
    fun `empty frames, bad names, bad durations and mixed sizes are refused`() {
        assertFailsWith<IllegalArgumentException> { clip(frames = emptyList()) }
        assertFailsWith<IllegalArgumentException> { clip(action = "../walk") }
        assertFailsWith<IllegalArgumentException> { clip(facing = "") }
        assertFailsWith<IllegalArgumentException> { frame(ms = 0) }
        assertFailsWith<IllegalArgumentException> { frame(ms = -5) }
        assertFailsWith<IllegalArgumentException> { clip(frames = listOf(frame(), frame(width = 5))) }
    }

    @Test
    fun `the models folder is under the profile`() {
        val folder = ProfileFolder(Path.of("lib", "game", "hero"))

        assertEquals(Path.of("lib", "game", "hero", "models"), folder.modelsFolder)
        assertEquals(Path.of("lib", "game", "hero", "models", "walk_east"), folder.modelFolder("walk_east"))
        assertFailsWith<IllegalArgumentException> { folder.modelFolder("..") }
    }
}
