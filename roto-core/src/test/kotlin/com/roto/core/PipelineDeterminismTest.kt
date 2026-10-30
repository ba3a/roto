package com.roto.core

import com.roto.core.ase.AseCodec
import com.roto.core.cycle.CycleAnalyser
import com.roto.core.draft.DraftRenderer
import com.roto.core.framebox.FrameBoxer
import com.roto.core.handoff.HandoffMerge
import com.roto.core.image.Argb
import com.roto.core.image.PngCodec
import com.roto.core.mask.ForegroundMasker
import com.roto.core.mask.MaskFixer
import com.roto.core.plate.CleanPlateBuilder
import com.roto.core.publish.Publisher
import com.roto.core.render.ProfileRenderer
import com.roto.core.render.SyntheticWalk
import com.roto.core.render.SyntheticWalk.Companion.CLIP
import com.roto.core.render.SyntheticWalk.Companion.PROFILE_ID
import com.roto.core.shade.DraftShader
import com.roto.core.shade.ShadingAnalyser
import com.roto.core.track.RootTracker
import com.roto.core.video.FrameExtractor
import com.roto.core.zone.ZoneClassifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The whole pipeline, end to end, over a [SyntheticWalk]: frames from a stand-in `ffmpeg`, then
 * plate, mask, root track, cycle proposal, frame boxes, zones, shading, draft and handoff by the
 * [ProfileRenderer], a scripted artist tracing the draft into the `art` layer, and publish with
 * mirroring.
 *
 * Determinism is checked the strictest way there is: every file the pipeline leaves behind — cache
 * entries, handoff, published frames and manifests — is compared byte for byte between runs in
 * different folders and under a different default locale, and between a cold run and a warm one.
 */
class PipelineDeterminismTest {

    private lateinit var tempRoot: Path

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-pipeline")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `two cold runs in different folders and locales leave byte-identical files`() {
        val first = SyntheticWalk(tempRoot.resolve("first")).apply { renderAndPublish() }
        val second = SyntheticWalk(tempRoot.resolve("second"))
        inLocale(Locale.forLanguageTag("tr-TR")) { second.renderAndPublish() }

        assertSameFiles(files(first.root), files(second.root))
    }

    @Test
    fun `rendering and publishing again from a warm cache changes no byte`() {
        val walk = SyntheticWalk(tempRoot.resolve("run")).apply { renderAndPublish() }
        val cold = files(walk.root)

        walk.renderAndPublish()

        assertSameFiles(cold, files(walk.root))
    }

    @Test
    fun `a still leaves byte-identical files too`() {
        val first = SyntheticWalk(tempRoot.resolve("first")).apply {
            useStill()
            renderAndPublish()
        }
        val second = SyntheticWalk(tempRoot.resolve("second")).apply { useStill() }
        inLocale(Locale.forLanguageTag("tr-TR")) { second.renderAndPublish() }

        assertSameFiles(files(first.root), files(second.root))
    }

    @Test
    fun `the run goes through every stage and leaves real art behind`() {
        val walk = SyntheticWalk(tempRoot.resolve("run")).apply { renderAndPublish() }

        val stages = files(walk.root).keys
            .filter { it.startsWith("library/${walk.project.config.id}/$PROFILE_ID/cache/") }
            .map { it.split('/')[CACHE_STAGE_SEGMENT] }
            .toSet()
        assertEquals(
            setOf(
                FrameExtractor.STAGE, CleanPlateBuilder.STAGE, ForegroundMasker.STAGE, MaskFixer.STAGE,
                RootTracker.STAGE,
                CycleAnalyser.STAGE, FrameBoxer.STAGE, ZoneClassifier.STAGE, ShadingAnalyser.STAGE,
                DraftShader.STAGE, DraftRenderer.STAGE,
            ),
            stages,
        )
        val sprite = AseCodec.read(walk.folder.handoffFile(CLIP))
        val draftLayer = sprite.layers.indexOfFirst { it.name == HandoffMerge.DRAFT }
        assertEquals(SyntheticWalk.KEY_FRAMES, sprite.frames.size)
        sprite.frames.forEach { frame ->
            val draft = frame.cels.single { it.layerIndex == draftLayer }.image
            val colours = draft.toByteArray().map { it.toInt() and BYTE }.filter { it != 0 }.toSet()
            assertTrue(colours.size >= SyntheticWalk.ZONES.size, "A draft frame shows only colours $colours")
        }
        listOf(CLIP.facing, "WEST").forEach { facing ->
            sprite.frames.indices.forEach { index ->
                val file = walk.project.frameFile(PROFILE_ID, CLIP.action, facing, index)
                assertTrue(PngCodec.read(file).toArgbArray().any { Argb.alpha(it) != 0 }, "$file is blank")
            }
        }
    }

    /** Renders the profile as `profile.json` holds it, traces its art, and publishes it. */
    private fun SyntheticWalk.renderAndPublish() {
        val profile = repository.load(PROFILE_ID)
        ProfileRenderer(project, ffmpeg).render(profile, folder)
        profile.clips.forEach { trace(it) }
        Publisher(project, runner).publish(PROFILE_ID, profile, folder)
    }

    private fun files(root: Path): Map<String, ByteArray> =
        Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) }.toList()
                .associate { root.relativize(it).joinToString("/") to Files.readAllBytes(it) }
                .toSortedMap()
        }

    private fun assertSameFiles(expected: Map<String, ByteArray>, actual: Map<String, ByteArray>) {
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (path, bytes) -> assertContentEquals(bytes, actual.getValue(path), path) }
    }

    private fun <T> inLocale(locale: Locale, body: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            return body()
        } finally {
            Locale.setDefault(previous)
        }
    }

    private companion object {
        const val BYTE = 0xFF

        /** `library/<project>/<profile>/cache/<stage>/…`: the segment naming the stage. */
        const val CACHE_STAGE_SEGMENT = 4
    }
}
