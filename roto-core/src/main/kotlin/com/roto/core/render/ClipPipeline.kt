package com.roto.core.render

import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.draft.DraftRenderer
import com.roto.core.framebox.FrameBoxer
import com.roto.core.framebox.FrameBoxes
import com.roto.core.framebox.ScaleSettings
import com.roto.core.handoff.HandoffRender
import com.roto.core.image.IndexedRaster
import com.roto.core.palette.ProjectPalette
import com.roto.core.profile.Clip
import com.roto.core.project.Canvas
import com.roto.core.shade.ClipShading
import com.roto.core.shade.DraftColours
import com.roto.core.shade.DraftShader
import com.roto.core.shade.ShadingAnalyser
import com.roto.core.video.Ffmpeg
import com.roto.core.zone.ZoneClassifier
import java.nio.file.Path

/**
 * What every clip of a profile is drawn on and in: the project's [canvas] and [palette], and the
 * profile's [scale], null until the artist has marked it.
 */
class Drawing(val canvas: Canvas, val palette: ProjectPalette, val scale: ScaleSettings?)

/**
 * Every stage of one clip, wired output to input, from its [masking] on to its [handoff]. Each
 * stage's [StageOutput] is built on first use and keyed from the outputs it reads, so asking for a
 * late one — the drafts — builds the keys of everything before it, and a cache hit anywhere loads
 * nothing upstream of it.
 */
class ClipPipeline(
    ffmpeg: Ffmpeg,
    private val cache: StageCache,
    private val drawing: Drawing,
    private val clip: Clip,
    source: Path,
) {
    val masking = ClipMasking(ClipFootage(ffmpeg, cache, source, clip.trim), cache, clip)

    private val frames get() = masking.footage.frames

    val boxes: StageOutput<FrameBoxes> by lazy {
        val scale = requireNotNull(drawing.scale) { "The profile's scale is not marked yet; mark head and feet first" }
        FrameBoxer(cache).boxes(masking.masks, masking.track, scale, drawing.canvas)
    }

    val zones: StageOutput<List<IndexedRaster>> by lazy {
        ZoneClassifier(cache).zones(frames, masking.fixedMasks, clip.zoning)
    }

    val colours: DraftColours by lazy { DraftColours.of(drawing.palette, clip.zoning) }

    val shading: StageOutput<ClipShading> by lazy {
        ShadingAnalyser(cache).shading(frames, zones, colours.zones.map { it.size }, clip.shading)
    }

    /** Each key frame shaded at [DraftShader.FACTOR] times the canvas. */
    val shaded: StageOutput<List<IndexedRaster>> by lazy {
        DraftShader(cache).shade(DraftShader.Inputs(frames, zones, boxes, shading), masking.keyFrames, colours)
    }

    /** Each key frame's finished draft at canvas size: what the handoff's `draft` layer shows. */
    val drafts: StageOutput<List<IndexedRaster>> by lazy {
        val inputs = DraftRenderer.Inputs(shaded, zones, boxes)
        DraftRenderer(cache).render(inputs, masking.keyFrames, colours, clip.draft)
    }

    /** What the clip's handoff is written from: its reference and draft per key frame. */
    fun handoff(): HandoffRender {
        val sources = HandoffRender.Sources(frames.get(), boxes.get(), drafts.get())
        return HandoffRender.of(drawing.palette.palette, masking.keyFrames, masking.footage.frameRate, sources)
    }
}
