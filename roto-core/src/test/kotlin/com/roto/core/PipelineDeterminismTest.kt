package com.roto.core

import com.roto.core.ase.AseCodec
import com.roto.core.cache.RasterSequenceCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.cycle.CycleAnalyser
import com.roto.core.cycle.walker
import com.roto.core.draft.DraftRenderer
import com.roto.core.draft.DraftSettings
import com.roto.core.framebox.FrameBoxer
import com.roto.core.framebox.ScaleMarks
import com.roto.core.framebox.ScaleSettings
import com.roto.core.handoff.HandoffMerge
import com.roto.core.handoff.HandoffRender
import com.roto.core.handoff.HandoffWriter
import com.roto.core.image.Argb
import com.roto.core.image.PngCodec
import com.roto.core.image.Raster
import com.roto.core.mask.ForegroundMasker
import com.roto.core.mask.MaskSettings
import com.roto.core.palette.ProjectPalette
import com.roto.core.plate.CleanPlateBuilder
import com.roto.core.process.FakeProcessRunner
import com.roto.core.process.ProcessResult
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import com.roto.core.profile.ProfileFolder
import com.roto.core.profile.ProfileRepository
import com.roto.core.profile.TimeRange
import com.roto.core.project.Project
import com.roto.core.publish.Publisher
import com.roto.core.shade.DraftColours
import com.roto.core.shade.DraftShader
import com.roto.core.shade.ShadingAnalyser
import com.roto.core.track.RootTracker
import com.roto.core.video.Ffmpeg
import com.roto.core.video.FrameExtractor
import com.roto.core.video.FrameRate
import com.roto.core.zone.Zone
import com.roto.core.zone.ZoneClassifier
import com.roto.core.zone.ZoneSample
import com.roto.core.zone.ZoneSettings
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The whole pipeline, end to end, over a synthetic walk-across clip: frames from a stand-in `ffmpeg`,
 * then plate, mask, root track, cycle proposal, frame boxes, zones, shading, draft, handoff, a
 * scripted artist tracing the draft into the `art` layer, and publish with mirroring.
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
        val first = PipelineRun(tempRoot.resolve("first")).apply { renderAndPublish() }
        val second = PipelineRun(tempRoot.resolve("second"))
        inLocale(Locale.forLanguageTag("tr-TR")) { second.renderAndPublish() }

        assertSameFiles(files(first.root), files(second.root))
    }

    @Test
    fun `rendering and publishing again from a warm cache changes no byte`() {
        val run = PipelineRun(tempRoot.resolve("run")).apply { renderAndPublish() }
        val cold = files(run.root)

        run.renderAndPublish()

        assertSameFiles(cold, files(run.root))
    }

    @Test
    fun `the run goes through every stage and leaves real art behind`() {
        val run = PipelineRun(tempRoot.resolve("run")).apply { renderAndPublish() }

        val stages = files(run.root).keys
            .filter { it.startsWith("library/$PROJECT_ID/$PROFILE_ID/cache/") }
            .map { it.split('/')[CACHE_STAGE_SEGMENT] }
            .toSet()
        assertEquals(
            setOf(
                FrameExtractor.STAGE, CleanPlateBuilder.STAGE, ForegroundMasker.STAGE, RootTracker.STAGE,
                CycleAnalyser.STAGE, FrameBoxer.STAGE, ZoneClassifier.STAGE, ShadingAnalyser.STAGE,
                DraftShader.STAGE, DraftRenderer.STAGE,
            ),
            stages,
        )
        val sprite = AseCodec.read(run.folder.handoffFile(WALK_EAST))
        val draftLayer = sprite.layers.indexOfFirst { it.name == HandoffMerge.DRAFT }
        assertEquals(CYCLE_KEY_FRAMES, sprite.frames.size)
        sprite.frames.forEach { frame ->
            val draft = frame.cels.single { it.layerIndex == draftLayer }.image
            val colours = draft.toByteArray().map { it.toInt() and BYTE }.filter { it != 0 }.toSet()
            assertTrue(colours.size >= ZONES.size, "A draft frame shows only colours $colours")
        }
        listOf(WALK_EAST.facing, "WEST").forEach { facing ->
            sprite.frames.indices.forEach { index ->
                val file = run.project.frameFile(PROFILE_ID, WALK_EAST.action, facing, index)
                assertTrue(PngCodec.read(file).toArgbArray().any { Argb.alpha(it) != 0 }, "$file is blank")
            }
        }
    }

    /**
     * One library and one consuming project under [root], set up from scratch: the sample `roto.json`
     * and palette, and a profile with one walk clip whose source "video" the stand-in `ffmpeg`
     * decodes into [syntheticFrame]s.
     */
    private class PipelineRun(val root: Path) {

        private val runner = FakeProcessRunner { command -> answer(command) }
        private val ffmpeg = Ffmpeg(runner)
        val project: Project = Project.open(copyProject(root.resolve("game")))
        private val repository = ProfileRepository(root.resolve("library"), project.config.id)
        val folder: ProfileFolder = repository.folder(PROFILE_ID)

        init {
            repository.create(PROFILE_ID)
            folder.sourcesFolder.resolve(WALK_EAST.source).writeBytes(SOURCE_BYTES)
            repository.save(PROFILE_ID, PROFILE)
        }

        fun renderAndPublish() {
            val profile = repository.load(PROFILE_ID)
            val palette = ProjectPalette.load(project)
            val cache = StageCache(folder.cacheFolder)
            profile.clips.forEach { clip -> render(profile, clip, palette, cache) }
            profile.clips.forEach { clip -> trace(folder.handoffFile(clip)) }
            Publisher(project, runner).publish(PROFILE_ID, profile, folder)
        }

        /** Every stage of [clip], wired output to input, ending in its handoff. */
        private fun render(profile: Profile, clip: Clip, palette: ProjectPalette, cache: StageCache) {
            val extractor = FrameExtractor(ffmpeg, cache)
            val source = folder.sourceFile(clip)
            val frames = extractor.frames(source, clip.trim, FRAME_RATE)
            val plate = CleanPlateBuilder(cache).plate(clip.plate, frames) { extractor.frames(source, it, FRAME_RATE) }
            val masks = ForegroundMasker(cache).masks(frames, plate, clip.mask)
            val track = RootTracker(cache).track(masks, clip.root)
            val cycle = CycleAnalyser(cache).analyse(masks, track, FRAME_RATE, clip.cycle, clip.loop)
            val keyFrames = clip.keyFrames(cycle.get())
            val boxes = FrameBoxer(cache).boxes(masks, track, checkNotNull(profile.scale), project.config.canvas)
            val zones = ZoneClassifier(cache).zones(frames, masks, clip.zoning)
            val colours = DraftColours.of(palette, clip.zoning)
            val shading = ShadingAnalyser(cache).shading(frames, zones, colours.zones.map { it.size }, clip.shading)
            val shaded = DraftShader(cache).shade(DraftShader.Inputs(frames, zones, boxes, shading), keyFrames, colours)
            val drafts = DraftRenderer(cache)
                .render(DraftRenderer.Inputs(shaded, zones, boxes), keyFrames, colours, clip.draft)
            val sources = HandoffRender.Sources(frames.get(), boxes.get(), drafts.get())
            val render = HandoffRender.of(palette.palette, keyFrames, FRAME_RATE, sources)
            HandoffWriter.write(folder.handoffFile(clip), render)
        }

        /** The artist's part, scripted: every frame's draft traced into the `art` layer, unless already painted. */
        private fun trace(handoff: Path) {
            val sprite = AseCodec.read(handoff)
            val draft = sprite.layers.indexOfFirst { it.name == HandoffMerge.DRAFT }
            val art = sprite.layers.indexOfFirst { it.name == HandoffMerge.ART }
            if (sprite.frames.any { frame -> frame.cels.any { it.layerIndex == art } }) return
            val frames = sprite.frames.map { frame ->
                frame.copy(cels = frame.cels + frame.cels.single { it.layerIndex == draft }.copy(layerIndex = art))
            }
            AseCodec.write(sprite.copy(frames = frames), handoff)
        }

        /** `ffmpeg -version`, a range extraction (always the whole synthetic clip), or the `afterPublish` hook. */
        private fun answer(command: List<String>): ProcessResult = when {
            command.last() == "-version" -> FakeProcessRunner.success("ffmpeg version 7.1")
            command.first() == "ffmpeg" -> {
                val entry = Path.of(command.last()).parent
                repeat(CLIP_FRAMES) {
                    PngCodec.write(syntheticFrame(it), entry.resolve(RasterSequenceCacheCodec.fileName(it)))
                }
                FakeProcessRunner.success()
            }
            else -> FakeProcessRunner.success("synced")
        }

        private fun copyProject(game: Path): Path {
            listOf("roto.json", "palette/faultory.gpl", "palette/ramps.json").forEach { name ->
                val target = game.resolve(name)
                Files.createDirectories(target.parent)
                javaClass.getResourceAsStream("/project/$name")!!.use { Files.copy(it, target) }
            }
            return game
        }
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
        const val PROJECT_ID = "faultory"
        const val PROFILE_ID = "worker"
        const val BYTE = 0xFF

        /** `library/<project>/<profile>/cache/<stage>/…`: the segment naming the stage. */
        const val CACHE_STAGE_SEGMENT = 4

        val FRAME_RATE = FrameRate(30, 1)
        val SOURCE_BYTES = byteArrayOf(0x52, 0x4F, 0x54, 0x4F)

        /** The synthetic clip: [walker] crossing a textured wall above a floor, 2 px a frame. */
        const val CLIP_FRAMES = 40
        const val FRAME_WIDTH = 160
        const val FRAME_HEIGHT = 60
        const val FIGURE_TOP = 12
        const val FLOOR_TOP = 52
        const val HEAD_ROWS = 6
        const val TORSO_ROWS = 22
        const val LIGHT_PER_PIXEL = 4

        /** The default `CycleSettings.keyFrames`, which the proposal spreads over one walk cycle. */
        const val CYCLE_KEY_FRAMES = 8

        val ZONES = listOf(
            Zone("head", ramp = "skin", samples = listOf(ZoneSample(frame = 0, x = 20, y = 14))),
            Zone("jacket", ramp = "suit", samples = listOf(ZoneSample(frame = 0, x = 20, y = 25))),
            Zone("trousers", ramp = "visor", samples = listOf(ZoneSample(frame = 0, x = 20, y = 36))),
        )

        /**
         * Walked across the frame and filmed at 30 fps; masked with lightness at full weight, since the
         * figure is lit unevenly against a darker wall; dithered; opted in to mirroring into WEST.
         * Its key frames are left to the cycle proposal.
         */
        val WALK_EAST = Clip(
            action = "walk",
            facing = "EAST",
            source = "walk.mov",
            trim = TimeRange(500, 1_833),
            mask = MaskSettings(lightnessWeight = 1.0),
            zoning = ZoneSettings(ZONES),
            draft = DraftSettings(dither = true),
            mirror = true,
        )

        val PROFILE = Profile(
            ramps = listOf("skin", "suit", "visor"),
            scale = ScaleSettings(
                bodyHeight = 48,
                marks = ScaleMarks(WALK_EAST.id, frame = 0, headTopY = FIGURE_TOP, feetY = FLOOR_TOP - 1),
                squash = 0.9,
            ),
            clips = listOf(WALK_EAST),
        )

        /**
         * Frame [index] of the clip: a three-colour figure — skin head, blue jacket, green trousers —
         * lit from the left, so each zone spans several shades; a faintly textured wall and a floor.
         */
        fun syntheticFrame(index: Int): Raster {
            val figure = walker(index, width = FRAME_WIDTH)
            val centre = 20 + 2 * index
            return Raster.of(FRAME_WIDTH, FRAME_HEIGHT) { x, y ->
                val row = y - FIGURE_TOP
                when {
                    row in 0 until figure.height && figure[x, row] ->
                        lit(bodyColour(row), (centre - x) * LIGHT_PER_PIXEL)
                    y >= FLOOR_TOP -> Argb.of(60, 55, 50)
                    else -> Argb.of(90 + (5 * x + 3 * y) % 7, 95 + (3 * x + 7 * y) % 5, 100)
                }
            }
        }

        private fun bodyColour(row: Int): Int = when {
            row < HEAD_ROWS -> Argb.of(225, 150, 115)
            row < TORSO_ROWS -> Argb.of(80, 130, 220)
            else -> Argb.of(60, 150, 120)
        }

        private fun lit(argb: Int, light: Int): Int = Argb.of(
            (Argb.red(argb) + light).coerceIn(0, BYTE),
            (Argb.green(argb) + light).coerceIn(0, BYTE),
            (Argb.blue(argb) + light).coerceIn(0, BYTE),
        )
    }
}
