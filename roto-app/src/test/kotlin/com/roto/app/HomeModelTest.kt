package com.roto.app

import com.roto.core.image.Raster
import com.roto.core.model.ModelClip
import com.roto.core.model.ModelFrame
import com.roto.core.model.ModelRepository
import com.roto.core.preset.BuiltInPresets
import com.roto.core.preset.PresetBundle
import com.roto.core.preset.ProjectExporter
import com.roto.core.preset.ProjectScaffolder
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import com.roto.core.profile.ProfileRepository
import com.roto.core.profile.TimeRange
import com.roto.core.project.Project
import com.roto.core.settings.Settings
import com.roto.core.settings.SettingsStore
import kotlinx.coroutines.runBlocking
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeModelTest {

    private lateinit var tempRoot: Path
    private lateinit var game: Path
    private lateinit var library: Path
    private lateinit var store: SettingsStore

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-home")
        game = tempRoot.resolve("game").createDirectories()
        game.resolve("roto.json").writeText(ROTO_JSON)
        library = tempRoot.resolve("library")
        store = SettingsStore(tempRoot.resolve("config").resolve("settings.json"))
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `a first start has nothing open and nothing to say`() {
        val model = HomeModel(store)

        assertNull(model.state.project)
        assertNull(model.state.settings.library)
        assertNull(model.state.message)
        assertTrue(model.state.profiles.isEmpty())
    }

    @Test
    fun `opening a project and choosing a library are remembered for the next start`() {
        HomeModel(store).apply {
            openProject(game)
            chooseLibrary(library)
        }

        val restarted = HomeModel(store)

        assertEquals("demo", restarted.state.project?.config?.id)
        assertEquals(game.toAbsolutePath(), restarted.state.project?.root)
        assertEquals(library.toAbsolutePath(), restarted.state.settings.libraryFolder)
    }

    @Test
    fun `the editor chosen is remembered for the next start`() {
        val program = tempRoot.resolve("libresprite")
        HomeModel(store).chooseEditor(program)

        assertEquals(program.toAbsolutePath(), HomeModel(store).state.settings.editorProgram)
    }

    @Test
    fun `a folder without roto json is not opened, and the open project stays`() {
        val model = HomeModel(store).apply { openProject(game) }

        model.openProject(tempRoot)

        assertContains(model.state.message!!, "holds no roto.json")
        assertEquals(game.toAbsolutePath(), model.state.project?.root)
        assertEquals(game.toAbsolutePath(), store.load().projectFolder)
    }

    @Test
    fun `a broken roto json is reported`() {
        val broken = tempRoot.resolve("broken").createDirectories()
        broken.resolve("roto.json").writeText("{ \"id\": \"demo\" }")

        val model = HomeModel(store).apply { openProject(broken) }

        assertNull(model.state.project)
        assertContains(model.state.message!!, "roto.json")
    }

    @Test
    fun `profiles of the project are listed in order, unreadable ones with their problem`() {
        val repository = ProfileRepository(library, "demo")
        repository.create("worker")
        repository.save("worker", Profile(clips = listOf(Clip("walk", "EAST", "walk.mov", TimeRange(0, 1_000)))))
        repository.create("guard")
        repository.folder("broken").profileFile.also { Files.createDirectories(it.parent) }.writeText("{")

        val model = HomeModel(store).apply {
            openProject(game)
            chooseLibrary(library)
        }

        assertEquals(listOf("broken", "guard", "worker"), model.state.profiles.map { it.id })
        assertNotNull(model.state.profiles[0].problem)
        assertEquals(0, model.state.profiles[1].profile?.clips?.size)
        assertEquals(1, model.state.profiles[2].profile?.clips?.size)
        assertEquals("broken", model.state.selected)
    }

    @Test
    fun `a new profile is created in the library and selected`() {
        val model = HomeModel(store).apply {
            openProject(game)
            chooseLibrary(library)
        }

        assertTrue(model.createProfile(" hero "))

        assertEquals(listOf("hero"), model.state.profiles.map { it.id })
        assertEquals("hero", model.state.selectedEntry?.id)
        assertTrue(ProfileRepository(library, "demo").exists("hero"))
    }

    @Test
    fun `a profile that cannot be made leaves a message`() {
        val model = HomeModel(store)
        assertFalse(model.createProfile("hero"))
        assertContains(model.state.message!!, "Open a project")

        model.openProject(game)
        model.chooseLibrary(library)
        assertFalse(model.createProfile("../hero"))
        assertContains(model.state.message!!, "cannot be a profile name")

        model.createProfile("hero")
        assertFalse(model.createProfile("HERO"))
        assertContains(model.state.message!!, "already exists")
        model.dismissMessage()
        assertNull(model.state.message)
    }


    @Test
    fun `the selected profile lists its models and removing one updates the list`() {
        val repository = ProfileRepository(library, "demo")
        repository.create("hero")
        val models = ModelRepository(repository.folder("hero"))
        val frame = ModelFrame(Raster(2, 2), 100)
        models.save(ModelClip("walk", "EAST", listOf(frame), true, "walk.ase"))
        val model = HomeModel(store).apply {
            openProject(game)
            chooseLibrary(library)
        }

        assertEquals(listOf("walk_EAST"), model.state.models)
        model.removeModel("walk_EAST")

        assertTrue(model.state.models.isEmpty())
        assertFalse(models.exists("walk_EAST"))
    }

    @Test
    fun `importing a model opens its screen on the selected profile`() {
        ProfileRepository(library, "demo").create("hero")
        val model = HomeModel(store).apply {
            openProject(game)
            chooseLibrary(library)
        }

        model.importModel()

        assertTrue(model.screen is Screen.ImportingModel)
    }
    @Test
    fun `importing a good bundle opens the wizard on the new preset`() {
        val bundle = tempRoot.resolve("mine.rotopreset")
        PresetBundle.write(BuiltInPresets.load(BuiltInPresets.ids().first()).copy(name = "Mine"), bundle)
        val model = HomeModel(store)

        model.importBundle(bundle)

        val wizard = (model.screen as Screen.Wizard).model
        assertEquals("Mine", wizard.state.preset?.preset?.name)
        assertFalse(wizard.state.preset!!.builtIn)
        assertNull(model.state.message)
    }

    @Test
    fun `a bad bundle gives a message and changes nothing`() {
        val bundle = tempRoot.resolve("bad.rotopreset")
        bundle.writeText("not a zip")
        val model = HomeModel(store)

        model.importBundle(bundle)

        assertEquals(Screen.Home, model.screen)
        assertContains(model.state.message!!, "could not be imported")
        assertFalse(Files.exists(store.configFolder.resolve("presets")))
    }

    @Test
    fun `exporting writes a bundle that reads back as the project's preset`() {
        val bundle = tempRoot.resolve("out").resolve("mine.rotopreset")
        bundle.parent.createDirectories()
        val project = ProjectScaffolder.scaffold(
            BuiltInPresets.load(BuiltInPresets.ids().first()), "demo", tempRoot.resolve("made"),
        )
        val model = HomeModel(store)
        model.openProject(project.root)

        runBlocking { model.exportPreset(bundle) }

        assertNull(model.state.message)
        assertEquals(ProjectExporter.export(project, "mine", ""), PresetBundle.read(bundle))
    }

    @Test
    fun `exporting a project with custom output paths gives the message and writes nothing`() {
        val bundle = tempRoot.resolve("custom.rotopreset")
        val model = HomeModel(store)
        model.openProject(game)

        runBlocking { model.exportPreset(bundle) }

        assertContains(model.state.message!!, "These output paths are custom; choose a layout to export")
        assertFalse(Files.exists(bundle))
    }

    @Test
    fun `exporting with no project open does nothing`() {
        val bundle = tempRoot.resolve("none.rotopreset")
        val model = HomeModel(store)

        runBlocking { model.exportPreset(bundle) }

        assertNull(model.state.message)
        assertFalse(Files.exists(bundle))
    }

    @Test
    fun `finishing the wizard opens the project and remembers it`() {
        val model = HomeModel(store)
        model.startWizard()

        model.finishWizard(Project.open(game))

        assertEquals(Screen.Home, model.screen)
        assertEquals("demo", model.state.project?.config?.id)
        assertEquals(game.toAbsolutePath(), HomeModel(store).state.settings.projectFolder)
    }

    @Test
    fun `damaged settings start afresh with a message`() {
        Files.createDirectories(store.file.parent)
        store.file.writeText("not json")

        val model = HomeModel(store)

        assertEquals(Settings(), model.state.settings)
        assertContains(model.state.message!!, "Settings could not be read")
    }

    private companion object {
        const val ROTO_JSON = """{
  "id": "demo",
  "palette": "palette.gpl",
  "ramps": "ramps.json",
  "facings": ["EAST", "WEST"],
  "actions": ["walk"],
  "canvas": { "width": 20, "height": 40, "groundRow": 39 },
  "output": "art/{profile}/{action}_{facing:lower}/{frame:000}.png",
  "manifest": "art/{profile}/{action}_{facing:lower}/clip.json"
}
"""
    }
}
