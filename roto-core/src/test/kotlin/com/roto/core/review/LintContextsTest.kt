package com.roto.core.review

import com.roto.core.framebox.DepthSettings
import com.roto.core.image.Raster
import com.roto.core.model.ModelClip
import com.roto.core.model.ModelFrame
import com.roto.core.model.ModelMeasure
import com.roto.core.model.ModelRepository
import com.roto.core.preset.BuiltInPresets
import com.roto.core.preset.ProjectScaffolder
import com.roto.core.profile.Clip
import com.roto.core.profile.ProfileFolder
import com.roto.core.profile.TimeRange
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val RED = 0xFFFF0000.toInt()

class LintContextsTest {
    private val dir = createTempDirectory("roto-lint-contexts")
    private val project = ProjectScaffolder.scaffold(
        BuiltInPresets.load(BuiltInPresets.ids().first()),
        "demo",
        dir.resolve("project").also(Files::createDirectories),
    )
    private val models = ModelRepository(ProfileFolder(dir.resolve("profile")))

    @AfterTest
    fun cleanUp() {
        dir.toFile().deleteRecursively()
    }

    private val model = ModelClip(
        "walk",
        "east",
        listOf(60, 90).map { ModelFrame(Raster.of(4, 6) { x, y -> if (x == 1 && y > 1) RED else 0 }, it) },
        loop = true,
        origin = "walk.ase",
    )

    private fun clip(model: String?) = Clip(
        "walk",
        "east",
        "a.MOV",
        TimeRange(0, 1_000),
        depth = DepthSettings(follow = true),
        loop = false,
        model = model,
    )

    @Test
    fun `a clip with a model gets that model's measurement and durations`() {
        models.save(model)

        val context = LintContexts.of(project, clip("walk_east"), models, listOf(80, 80, 80))

        val reference = assertNotNull(context.model)
        assertEquals(listOf(60, 90), reference.durationsMs)
        assertEquals(ModelMeasure.measure(model.frames.map { it.raster }), reference.measurement)
    }

    @Test
    fun `a clip without a model gets none`() {
        assertNull(LintContexts.of(project, clip(null), models, listOf(80)).model)
    }

    @Test
    fun `the project palette, the clip's settings and its durations are carried through`() {
        val context = LintContexts.of(project, clip(null), models, listOf(80, 70), LintSettings(groundDriftPx = 3))

        assertTrue(context.palette.colors.isNotEmpty())
        assertTrue(context.outline)
        assertTrue(context.depth.follow)
        assertEquals(false, context.loop)
        assertEquals(listOf(80, 70), context.clipDurationsMs)
        assertEquals(3, context.settings.groundDriftPx)
    }
}
