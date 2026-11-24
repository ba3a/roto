package com.roto.core.preset

import com.roto.core.palette.Palette
import com.roto.core.palette.Ramp
import com.roto.core.palette.RampSet
import com.roto.core.project.Canvas
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PresetLibraryTest {
    private val dark = 0xFF202020.toInt()
    private val light = 0xFFE0E0E0.toInt()

    private fun preset(name: String) = ProjectPreset(
        name, "d", listOf("LEFT"), emptyList(), listOf("idle"), Canvas(32, 32, 30),
        OutputLayout.ONE_FOLDER_PER_CLIP, "art", Palette(listOf(dark, light)),
        RampSet(ramps = listOf(Ramp("skin", listOf(dark, light)))),
    )

    private val user: Path = Files.createTempDirectory("library").resolve("presets")
    private val library = PresetLibrary(mapOf("zeta" to preset("Zeta"), "alpha" to preset("Alpha")), user)

    @Test
    fun `built-in presets list before user ones, each sorted by id`() {
        library.add(preset("Mine B"))
        library.add(preset("Mine A"))

        assertEquals(listOf("alpha", "zeta", "Mine-A", "Mine-B"), library.list().map { it.id })
        assertEquals(listOf(true, true, false, false), library.list().map { it.builtIn })
    }

    @Test
    fun `importing a bundle adds it`() {
        val bundle = Files.createTempDirectory("bundle").resolve("x.rotopreset")
        PresetBundle.write(preset("Imported one"), bundle)

        assertEquals("Imported-one", library.add(bundle))

        val entry = assertIs<LibraryPreset>(library.list().last())
        assertEquals(preset("Imported one"), entry.preset)
    }

    @Test
    fun `a bad bundle adds nothing`() {
        val bundle = Files.createTempDirectory("bundle").resolve("bad.rotopreset")
        bundle.writeText("not a zip")

        assertFailsWith<PresetException> { library.add(bundle) }

        assertTrue(!Files.exists(user) || user.listDirectoryEntries().isEmpty())
    }

    @Test
    fun `an id that already exists is refused`() {
        library.add(preset("Mine"))

        assertFailsWith<PresetException> { library.add(preset("mine")) }
        assertFailsWith<PresetException> { library.add(preset("Alpha")) }
        assertEquals(1, user.listDirectoryEntries().size)
    }

    @Test
    fun `a name with no usable id is refused`() {
        assertFailsWith<PresetException> { library.add(preset("!!!")) }
    }

    @Test
    fun `a broken user preset is listed as a problem`() {
        library.add(preset("Good"))
        user.resolve("broken").createDirectories()

        val entries = library.list()

        assertIs<BrokenPreset>(entries.single { it.id == "broken" })
        assertIs<LibraryPreset>(entries.single { it.id == "Good" })
    }

    @Test
    fun `remove deletes only a user preset`() {
        library.add(preset("Mine"))

        assertFailsWith<PresetException> { library.remove("alpha") }
        assertFailsWith<PresetException> { library.remove("nothing") }
        assertFailsWith<PresetException> { library.remove("..") }
        library.remove("Mine")

        assertEquals(listOf("alpha", "zeta"), library.list().map { it.id })
    }
}
