package com.roto.core.palette

import com.roto.core.image.Argb
import com.roto.core.image.IndexedRaster
import com.roto.core.project.Project
import com.roto.core.project.sampleConfig
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProjectPaletteTest {

    private lateinit var root: Path

    @BeforeTest
    fun setUp() {
        root = createTempDirectory("roto-project-palette")
        val folder = root.resolve("palette").createDirectories()
        listOf("faultory.gpl", "ramps.json").forEach { name ->
            javaClass.getResourceAsStream("/project/palette/$name")!!.use { Files.copy(it, folder.resolve(name)) }
        }
    }

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun `palette labels paint as their colours, 0 staying transparent`() {
        val palette = Palette(listOf(Argb.of(1, 2, 3), Argb.of(4, 5, 6)))
        val labels = IndexedRaster.of(3, 1) { x, _ -> x }

        val painted = palette.paint(labels)

        assertEquals(Argb.TRANSPARENT, painted[0, 0])
        assertEquals(Argb.of(1, 2, 3), painted[1, 0])
        assertEquals(Argb.of(4, 5, 6), painted[2, 0])
        assertFailsWith<IllegalArgumentException> { palette.paint(IndexedRaster.of(1, 1) { _, _ -> 3 }) }
    }

    @Test
    fun `the project's palette and ramps load together`() {
        val palette = ProjectPalette.load(Project(root, sampleConfig()))

        assertEquals(11, palette.palette.colors.size)
        assertEquals(listOf("visor", "skin"), palette.ramps(listOf("visor", "skin")).map { it.name })
    }

    @Test
    fun `a profile ramp the project lacks is named`() {
        val palette = ProjectPalette.load(Project(root, sampleConfig()))

        val error = assertFailsWith<IllegalArgumentException> { palette.ramps(listOf("skin", "hair")) }

        assertContains(error.message.orEmpty(), "hair")
    }

    @Test
    fun `ramp and outline colours must be palette colours`() {
        val palette = Palette(listOf(Argb.of(0, 0, 0), Argb.of(255, 255, 255)))
        val ramp = Ramp("gray", listOf(Argb.of(0, 0, 0), Argb.of(128, 128, 128)))

        assertFailsWith<IllegalArgumentException> { ProjectPalette(palette, RampSet(ramps = listOf(ramp))) }
        assertFailsWith<IllegalArgumentException> {
            ProjectPalette(palette, RampSet(outline = Argb.of(1, 1, 1), ramps = emptyList()))
        }
    }

    @Test
    fun `a ramps file using a colour its palette lacks fails naming the file`() {
        val ramps = root.resolve("palette").resolve("ramps.json")
        ramps.writeText("""{ "ramps": [{ "name": "a", "colors": ["#010203"] }] }""")

        val error = assertFailsWith<RampSetException> { ProjectPalette.load(Project(root, sampleConfig())) }

        assertContains(error.message.orEmpty(), "ramps.json")
        assertContains(error.message.orEmpty(), "#010203")
    }
}
