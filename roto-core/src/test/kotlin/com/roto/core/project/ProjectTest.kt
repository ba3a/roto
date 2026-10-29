package com.roto.core.project

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProjectTest {

    private lateinit var root: Path

    @BeforeTest
    fun setUp() {
        root = createTempDirectory("roto-project")
    }

    @AfterTest
    fun tearDown() {
        root.toFile().deleteRecursively()
    }

    @Test
    fun `opens the roto json in the root`() {
        ProjectConfigCodec.write(sampleConfig(), root.resolve(ProjectConfigCodec.FILE_NAME))

        val project = Project.open(root)

        assertEquals(sampleConfig(), project.config)
        assertEquals(root.toAbsolutePath().normalize(), project.root)
    }

    @Test
    fun `palette and ramps resolve under the root`() {
        val project = Project(root, sampleConfig())

        assertEquals(project.root.resolve("palette").resolve("faultory.gpl"), project.paletteFile)
        assertEquals(project.root.resolve("palette").resolve("ramps.json"), project.rampsFile)
    }

    @Test
    fun `frames and manifests resolve by the templates`() {
        val project = Project(root, sampleConfig())
        val clip = project.root.resolve("raw-art").resolve("worker_float_tech").resolve("walk_east")

        assertEquals(clip.resolve("000.png"), project.frameFile("worker_float_tech", "walk", "EAST", 0))
        assertEquals(clip.resolve("011.png"), project.frameFile("worker_float_tech", "walk", "EAST", 11))
        assertEquals(clip.resolve("clip.json"), project.manifestFile("worker_float_tech", "walk", "EAST"))
    }

    @Test
    fun `actions and facings must come from the config`() {
        val project = Project(root, sampleConfig())

        assertFailsWith<IllegalArgumentException> { project.frameFile("worker", "run", "EAST", 0) }
        assertFailsWith<IllegalArgumentException> { project.frameFile("worker", "walk", "east", 0) }
        assertFailsWith<IllegalArgumentException> { project.manifestFile("worker", "walk", "UP") }
    }

    @Test
    fun `afterPublish expands the profile into its arguments`() {
        val project = Project(root, sampleConfig())

        assertEquals(
            listOf("./gradlew.bat", ":editor:syncSkin", "--args=worker_float_tech"),
            project.afterPublishCommand("worker_float_tech"),
        )
        val silent = Project(root, sampleConfig().copy(afterPublish = emptyList()))
        assertEquals(emptyList(), silent.afterPublishCommand("worker_float_tech"))
    }
}
