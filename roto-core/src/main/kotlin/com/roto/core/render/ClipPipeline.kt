package com.roto.core.render

import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.cycle.CycleAnalyser
import com.roto.core.cycle.CycleAnalysis
import com.roto.core.cycle.KeyFrames
import com.roto.core.draft.DraftRenderer
import com.roto.core.framebox.FrameBoxer
import com.roto.core.framebox.FrameBoxes
import com.roto.core.framebox.ScaleSettings
import com.roto.core.handoff.HandoffRender
import com.roto.core.image.IndexedRaster
import com.roto.core.image.Mask
import com.roto.core.image.Raster
import com.roto.core.mask.ForegroundMasker
import com.roto.core.palette.ProjectPalette
import com.roto.core.plate.CleanPlateBuilder
import com.roto.core.profile.Clip
import com.roto.core.project.Canvas
import com.roto.core.shade.ClipShading
import com.roto.core.shade.DraftColours
import com.roto.core.shade.DraftShader
import com.roto.core.shade.ShadingAnalyser
import com.roto.core.track.RootTrack
import com.roto.core.track.RootTracker
import com.roto.core.video.Ffmpeg
import com.roto.core.video.FrameExtractor
import com.roto.core.video.FrameRate
import com.roto.core.zone.ZoneClassifier
import java.nio.file.Path

/**
 * What every clip of a profile is drawn on and in: the project's [canvas] and [palette], and the
 * profile's [scale], null until the artist has marked it.
 */
class Drawing(val canvas: Canvas, val palette: ProjectPalette, val scale: ScaleSettings?)

/**
 * Every stage of one [clip], wired output to input, from its [source] video to its [handoff]. Each
 * stage's [StageOutput] is built on first use and keyed from the outputs it reads, so asking for a
 * late one — the drafts — builds the keys of everything before it, and a cache hit anywhere loads
 * nothing upstream of it. An early one — the masks, for a preview — builds nothing after it.
 *
 * The frames are taken at the source's own frame rate, which `ffprobe` reports.
 */
class ClipPipeline(
    private val ffmpeg: Ffmpeg,
    private val cache: StageCache,
    private val drawing: Drawing,
    val clip: Clip,
    private val source: Path,
) {
    private val extractor by lazy { FrameExtractor(ffmpeg, cache) }

    val frameRate: FrameRate by lazy { ffmpeg.probe(source).frameRate }

    val frames: StageOutput<List<Raster>> by lazy { extractor.frames(source, clip.trim, frameRate) }

    val plate: StageOutput<Raster> by lazy {
        CleanPlateBuilder(cache).plate(clip.plate, frames) { range -> extractor.frames(source, range, frameRate) }
    }

    val masks: StageOutput<List<Mask>> by lazy { ForegroundMasker(cache).masks(frames, plate, clip.mask) }

    val track: StageOutput<RootTrack> by lazy { RootTracker(cache).track(masks, clip.root) }

    val cycle: StageOutput<CycleAnalysis> by lazy {
        CycleAnalyser(cache).analyse(masks, track, frameRate, clip.cycle, clip.loop)
    }

    /** The artist's markers when set, otherwise the cycle stage's proposal. */
    val keyFrames: KeyFrames by lazy { clip.keyFrames(cycle.get()) }

    val boxes: StageOutput<FrameBoxes> by lazy {
        val scale = requireNotNull(drawing.scale) { "The profile's scale is not marked yet; mark head and feet first" }
        FrameBoxer(cache).boxes(masks, track, scale, drawing.canvas)
    }

    val zones: StageOutput<List<IndexedRaster>> by lazy { ZoneClassifier(cache).zones(frames, masks, clip.zoning) }

    val colours: DraftColours by lazy { DraftColours.of(drawing.palette, clip.zoning) }

    val shading: StageOutput<ClipShading> by lazy {
        ShadingAnalyser(cache).shading(frames, zones, colours.zones.map { it.size }, clip.shading)
    }

    /** Each key frame shaded at [DraftShader.FACTOR] times the canvas. */
    val shaded: StageOutput<List<IndexedRaster>> by lazy {
        DraftShader(cache).shade(DraftShader.Inputs(frames, zones, boxes, shading), keyFrames, colours)
    }

    /** Each key frame's finished draft at canvas size: what the handoff's `draft` layer shows. */
    val drafts: StageOutput<List<IndexedRaster>> by lazy {
        DraftRenderer(cache).render(DraftRenderer.Inputs(shaded, zones, boxes), keyFrames, colours, clip.draft)
    }

    /** What the clip's handoff is written from: its reference and draft per key frame. */
    fun handoff(): HandoffRender {
        val sources = HandoffRender.Sources(frames.get(), boxes.get(), drafts.get())
        return HandoffRender.of(drawing.palette.palette, keyFrames, frameRate, sources)
    }
}
