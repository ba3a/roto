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
    fun `a relative folder is rejected`() {
        assertFailsWith<IllegalArgumentException> { Settings(library = "library") }
        assertFailsWith<IllegalArgumentException> { Settings(project = "") }
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
}
