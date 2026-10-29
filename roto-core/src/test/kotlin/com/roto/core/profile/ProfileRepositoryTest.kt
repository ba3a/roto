package com.roto.core.profile

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileRepositoryTest {

    private lateinit var library: Path
    private lateinit var repository: ProfileRepository

    @BeforeTest
    fun setUp() {
        library = createTempDirectory("roto-library")
        repository = ProfileRepository(library, "faultory")
    }

    @AfterTest
    fun tearDown() {
        library.toFile().deleteRecursively()
    }

    @Test
    fun `profiles live under the project id in the library`() {
        val folder = repository.folder("worker_float_tech")

        assertEquals(library.toAbsolutePath().normalize().resolve("faultory"), repository.projectFolder)
        assertEquals(repository.projectFolder.resolve("worker_float_tech"), folder.path)
        assertEquals(folder.path.resolve("profile.json"), folder.profileFile)
        assertEquals(folder.path.resolve("sources"), folder.sourcesFolder)
        assertEquals(folder.path.resolve("cache"), folder.cacheFolder)
        assertEquals(folder.path.resolve("handoff"), folder.handoffFolder)
    }

    @Test
    fun `clip files sit in sources and handoff`() {
        val folder = repository.folder("worker_float_tech")
        val idle = sampleProfile().clip("idle", "SOUTH")!!

        assertEquals(folder.sourcesFolder.resolve("idle").resolve("IMG_0420.MOV"), folder.sourceFile(idle))
        assertEquals(folder.handoffFolder.resolve("idle_SOUTH.ase"), folder.handoffFile(idle))
    }

    @Test
    fun `create makes an empty profile with its folders`() {
        val profile = repository.create("worker_float_tech")

        val folder = repository.folder("worker_float_tech")
        assertEquals(Profile(), profile)
        assertEquals(Profile(), repository.load("worker_float_tech"))
        assertTrue(folder.sourcesFolder.isDirectory())
        assertTrue(folder.handoffFolder.isDirectory())
        assertTrue(repository.exists("worker_float_tech"))
    }

    @Test
    fun `create refuses an existing id, even differing only in case`() {
        repository.create("worker_float_tech")
        repository.save("worker_float_tech", sampleProfile())

        assertFailsWith<FileAlreadyExistsException> { repository.create("worker_float_tech") }
        assertFailsWith<FileAlreadyExistsException> { repository.create("Worker_Float_Tech") }
        assertEquals(sampleProfile(), repository.load("worker_float_tech"))
    }

    @Test
    fun `save then load gives back the profile, and save replaces without leftovers`() {
        repository.create("worker_float_tech")
        repository.save("worker_float_tech", sampleProfile().copy(ramps = listOf("old")))

        repository.save("worker_float_tech", sampleProfile())

        assertEquals(sampleProfile(), repository.load("worker_float_tech"))
        val files = repository.folder("worker_float_tech").path.listDirectoryEntries().map { it.name }.sorted()
        assertEquals(listOf("handoff", "profile.json", "sources"), files)
    }

    @Test
    fun `ids are sorted and skip folders that hold no profile`() {
        listOf("worker_line_inspector", "machine_bench_assembler", "worker_float_tech").forEach(repository::create)
        Files.createDirectories(repository.projectFolder.resolve("scratch"))
        Files.createDirectories(repository.projectFolder.resolve("not a profile"))
        repository.projectFolder.resolve("notes.txt").writeText("x")

        assertEquals(
            listOf("machine_bench_assembler", "worker_float_tech", "worker_line_inspector"),
            repository.ids(),
        )
    }

    @Test
    fun `a project with no folder yet has no profiles`() {
        assertEquals(emptyList(), repository.ids())
        assertFalse(repository.exists("worker_float_tech"))
    }

    @Test
    fun `projects in one library are kept apart`() {
        repository.create("worker_float_tech")

        assertEquals(emptyList(), ProfileRepository(library, "other_game").ids())
    }

    @Test
    fun `loading a missing profile names the file`() {
        val error = assertFailsWith<NoSuchFileException> { repository.load("ghost") }

        assertEquals(repository.folder("ghost").profileFile.toString(), error.file)
    }

    @Test
    fun `a broken profile file is a profile error naming the file`() {
        repository.create("worker_float_tech")
        repository.folder("worker_float_tech").profileFile.writeText("{ \"clips\": 3 }")

        val error = assertFailsWith<ProfileException> { repository.load("worker_float_tech") }
        assertTrue(error.message.orEmpty().startsWith(repository.folder("worker_float_tech").profileFile.toString()))
    }

    @Test
    fun `ids that are not safe names are rejected`() {
        assertFailsWith<IllegalArgumentException> { repository.folder("../escape") }
        assertFailsWith<IllegalArgumentException> { repository.create("") }
        assertFailsWith<IllegalArgumentException> { ProfileRepository(library, "my game") }
    }
}
