package com.roto.core.preset

import com.roto.core.palette.Palette
import com.roto.core.palette.Ramp
import com.roto.core.palette.RampSet
import com.roto.core.project.Canvas
import com.roto.core.project.MirrorPair
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PresetBundleTest {
    private val dark = 0xFF202020.toInt()
    private val light = 0xFFE0E0E0.toInt()
    private val preset = ProjectPreset(
        "Side", "A side view", listOf("LEFT", "RIGHT"), listOf(MirrorPair("LEFT", "RIGHT")), listOf("idle"),
        Canvas(32, 32, 30), OutputLayout.ONE_FOLDER_PER_PROFILE, "art", Palette(listOf(dark, light)),
        RampSet(ramps = listOf(Ramp("skin", listOf(dark, light)))),
    )
    private val folder: Path = createTempDirectory("bundle")

    @AfterTest
    fun cleanUp() {
        @OptIn(kotlin.io.path.ExperimentalPathApi::class)
        folder.deleteRecursively()
    }

    @Test
    fun `exporting the same preset twice gives identical bytes`() {
        val a = folder.resolve("a.rotopreset")
        val b = folder.resolve("b.rotopreset")
        PresetBundle.write(preset, a)
        PresetBundle.write(preset, b)
        assertContentEquals(Files.readAllBytes(a), Files.readAllBytes(b))
    }

    @Test
    fun `the archive holds the three entries in order`() {
        val file = folder.resolve("a.rotopreset")
        PresetBundle.write(preset, file)
        ZipFile(file.toFile()).use { zip ->
            assertEquals(listOf("preset.json", "palette.hex", "ramps.json"), zip.entries().toList().map { it.name })
            val palette = zip.getInputStream(zip.getEntry("palette.hex")).readAllBytes().decodeToString()
            assertEquals("#202020\n#e0e0e0\n", palette)
        }
    }

    @Test
    fun `no extra field or comment is written`() {
        val file = folder.resolve("a.rotopreset")
        PresetBundle.write(preset, file)
        ZipFile(file.toFile()).use { zip ->
            assertNull(zip.comment)
            zip.entries().toList().forEach {
                assertEquals(0, it.extra?.size ?: 0)
                assertNull(it.comment)
            }
        }
        assertTrue(Files.readAllBytes(file).isNotEmpty())
    }
}
