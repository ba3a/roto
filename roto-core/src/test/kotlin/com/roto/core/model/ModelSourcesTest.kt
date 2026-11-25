package com.roto.core.model

import com.roto.core.project.Canvas
import com.roto.core.project.ProjectConfig
import com.roto.core.template.PathTemplate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals

class ModelSourcesTest {

    @TempDir
    lateinit var dir: Path

    private val config = ProjectConfig(
        id = "demo",
        palette = "palette.gpl",
        ramps = "ramps.json",
        facings = listOf("east"),
        mirrorPairs = emptyList(),
        actions = listOf("walk"),
        canvas = Canvas(width = 3, height = 4, groundRow = 3),
        output = PathTemplate("art/{profile}/{action}_{facing:lower}/{frame:000}.png"),
        manifest = PathTemplate("art/{profile}/{action}_{facing:lower}/clip.json"),
        afterPublish = emptyList(),
    )

    private fun file(name: String): Path = dir.resolve(name).also { it.writeText("x") }

    @Test
    fun `detects a folder`() {
        assertEquals(ModelSourceKind.PNG_FOLDER, ModelSources.detect(dir.resolve("frames").createDirectories()))
    }

    @Test
    fun `detects an ase file by either extension`() {
        assertEquals(ModelSourceKind.ASE, ModelSources.detect(file("a.ase")))
        assertEquals(ModelSourceKind.ASE, ModelSources.detect(file("b.ASEPRITE")))
    }

    @Test
    fun `detects an atlas`() {
        assertEquals(ModelSourceKind.ATLAS, ModelSources.detect(file("a.atlas")))
    }

    @Test
    fun `a missing path is not found`() {
        assertThrows<NoSuchFileException> { ModelSources.detect(dir.resolve("none.ase")) }
    }

    @Test
    fun `an unknown file is refused`() {
        assertThrows<ModelException> { ModelSources.detect(file("notes.txt")) }
    }

    @Test
    fun `a bad atlas pattern is a model exception`() {
        assertThrows<ModelException> { ModelSources.open(file("a.atlas"), config, 100, "{nope}") }
    }
}
