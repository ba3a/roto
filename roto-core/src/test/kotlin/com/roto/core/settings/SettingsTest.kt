package com.roto.core.settings

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SettingsTest {

    private lateinit var tempRoot: Path
    private lateinit var store: SettingsStore

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-settings")
        store = SettingsStore(tempRoot.resolve("config").resolve(SettingsCodec.fileName))
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `a missing file is the empty settings`() {
        assertEquals(Settings(), store.load())
        assertNull(store.load().libraryFolder)
    }

    @Test
    fun `saved settings read back, folders absolute and normalised`() {
        val library = tempRoot.resolve("art").resolve("..").resolve("library")
        val settings = Settings().withLibrary(library).withProject(tempRoot.resolve("game"))

        store.save(settings)

        assertEquals(settings, store.load())
        assertEquals(tempRoot.resolve("library").toAbsolutePath(), store.load().libraryFolder)
        assertEquals(tempRoot.resolve("game").toAbsolutePath(), store.load().projectFolder)
        assertContains(store.file.readText(), "\"library\"")
    }

    @Test
    fun `the editor is optional, and reads back as a program`() {
        assertNull(Settings().editorProgram)

        val settings = Settings().withEditor(tempRoot.resolve("tools").resolve("..").resolve("libresprite"))
        store.save(settings)

        assertEquals(tempRoot.resolve("libresprite").toAbsolutePath(), store.load().editorProgram)
    }

    @Test
    fun `a relative path is rejected`() {
        assertFailsWith<IllegalArgumentException> { Settings(library = "library") }
        assertFailsWith<IllegalArgumentException> { Settings(project = "") }
        assertFailsWith<IllegalArgumentException> { Settings(editor = "libresprite") }
    }

    @Test
    fun `a damaged or unknown file is a settings exception`() {
        Files.createDirectories(store.file.parent)
        listOf("{ \"library\": 3 }", "{ \"libary\": null }", "{ \"project\": \"game\" }").forEach { text ->
            store.file.writeText(text)
            val failure = assertFailsWith<SettingsException>(text) { store.load() }
            assertContains(failure.message!!, SettingsCodec.fileName)
        }
    }

    @Test
    fun `the settings folder is each system's own per-user configuration folder`() {
        val home = Path.of("/home/artist").toAbsolutePath()
        val appData = home.resolve("roaming").toString()

        assertEquals(
            Path.of(appData).resolve("Roto"),
            SettingsStore.folder("Windows 10", mapOf("APPDATA" to appData), home),
        )
        assertEquals(home.resolve("AppData/Roaming/Roto"), SettingsStore.folder("Windows 11", emptyMap(), home))
        assertEquals(
            home.resolve("Library/Application Support/Roto"),
            SettingsStore.folder("Mac OS X", emptyMap(), home),
        )
        assertEquals(home.resolve(".config/roto"), SettingsStore.folder("Linux", emptyMap(), home))
        assertEquals(
            Path.of("/xdg").resolve("roto"),
            SettingsStore.folder("Linux", mapOf("XDG_CONFIG_HOME" to "/xdg"), home),
        )
    }

    @Test
    fun `reopening a project brings back its library, and a library change follows the open project`() {
        val a = tempRoot.resolve("a")
        val b = tempRoot.resolve("b")
        val one = tempRoot.resolve("lib-one")
        val two = tempRoot.resolve("lib-two")

        val settings = Settings().withProject(a).withLibrary(one).withProject(b).withLibrary(two).withProject(a)

        assertEquals(one.toAbsolutePath(), settings.libraryFolder)
        assertEquals(listOf(a, b).map { it.toAbsolutePath().toString() }, settings.recent.map { it.project })
        assertEquals(two.toAbsolutePath().toString(), settings.recent.last().library)
    }

    @Test
    fun `recent projects are capped and a project is listed once`() {
        val last = MAX_RECENT_PROJECTS + 3
        val settings = (1..last).fold(Settings()) { acc, i -> acc.withProject(tempRoot.resolve("p$i")) }

        assertEquals(MAX_RECENT_PROJECTS, settings.recent.size)
        assertEquals(tempRoot.resolve("p$last").toAbsolutePath().toString(), settings.recent.first().project)
        assertFailsWith<IllegalArgumentException> { settings.copy(recent = settings.recent + settings.recent.first()) }
    }

    @Test
    fun `the last folder of each kind of chooser reads back`() {
        val settings = Settings()
            .withFolder("VIDEO", tempRoot.resolve("clips"))
            .withFolder("PHOTO", tempRoot.resolve("shots"))

        store.save(settings)

        assertEquals(tempRoot.resolve("clips").toAbsolutePath(), store.load().folder("VIDEO"))
        assertNull(store.load().folder("PALETTE"))
        assertFailsWith<IllegalArgumentException> { Settings(folders = mapOf("VIDEO" to "clips")) }
    }

    @Test
    fun `the library is the default until one is chosen`() {
        val home = tempRoot.resolve("home")
        val withDefault = SettingsStore(store.file, SettingsStore.homeLibrary(home))

        assertEquals(home.resolve("Roto"), withDefault.library())
        val chosen = Settings().withLibrary(tempRoot.resolve("mine"))
        assertEquals(tempRoot.resolve("mine").toAbsolutePath(), withDefault.library(chosen))
    }
}
