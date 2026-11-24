package com.roto.core.preset

import com.roto.core.palette.Palette
import com.roto.core.palette.Ramp
import com.roto.core.palette.RampSet
import com.roto.core.project.Canvas
import com.roto.core.project.MirrorPair
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.assertFailsWith
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

    private fun zip(vararg entries: Pair<String, ByteArray>): Path {
        val file = folder.resolve("x.rotopreset")
        ZipOutputStream(Files.newOutputStream(file)).use { out ->
            entries.forEach { (name, bytes) ->
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return file
    }

    private fun good(): MutableMap<String, ByteArray> {
        val file = folder.resolve("good.rotopreset")
        PresetBundle.write(preset, file)
        return ZipFile(file.toFile()).use { z ->
            z.entries().toList().associateTo(LinkedHashMap()) { it.name to z.getInputStream(it).readAllBytes() }
        }
    }

    private fun rejected(name: String, vararg changes: Pair<String, ByteArray>) {
        val entries = good().apply { putAll(changes) }
        val e = assertFailsWith<PresetException> { PresetBundle.read(zip(*entries.toList().toTypedArray())) }
        assertTrue(e.message.orEmpty().contains(name), e.message)
    }

    @Test
    fun `write then read round trips`() {
        val file = folder.resolve("a.rotopreset")
        PresetBundle.write(preset, file)
        assertEquals(preset, PresetBundle.read(file))
    }

    @Test
    fun `hostile entries are rejected naming the entry`() {
        rejected("extra.txt", "extra.txt" to byteArrayOf())
        rejected("..", "../preset.json" to byteArrayOf())
        rejected("a/b", "a/b" to byteArrayOf())
        rejected("palette.hex", "palette.hex" to ByteArray(300 * 1024))
        rejected("ramps.json", "ramps.json" to ByteArray(10 * 1024 * 1024))
    }

    @Test
    fun `a missing entry is rejected naming it`() {
        val entries = good().apply { remove("ramps.json") }
        val e = assertFailsWith<PresetException> { PresetBundle.read(zip(*entries.toList().toTypedArray())) }
        assertTrue(e.message.orEmpty().contains("ramps.json"))
    }

    @Test
    fun `afterPublish in preset json is rejected`() {
        val json = good().getValue("preset.json").decodeToString().replaceFirst("{", "{\"afterPublish\": \"x\",")
        rejected("preset.json", "preset.json" to json.toByteArray())
    }

    @Test
    fun `a file over 1 MB is rejected before reading`() {
        val file = folder.resolve("big.rotopreset")
        Files.write(file, ByteArray((1 shl 20) + 1))
        val e = assertFailsWith<PresetException> { PresetBundle.read(file) }
        assertTrue(e.message.orEmpty().contains("1 MB"))
    }
}
