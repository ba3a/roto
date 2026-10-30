package com.roto.core.render

import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.cycle.CycleAnalyser
import com.roto.core.cycle.CycleAnalysis
import com.roto.core.cycle.KeyFrames
import com.roto.core.image.Mask
import com.roto.core.image.Raster
import com.roto.core.mask.ForegroundMasker
import com.roto.core.mask.MaskFixer
import com.roto.core.plate.CleanPlateBuilder
import com.roto.core.profile.Clip
import com.roto.core.track.RootTrack
import com.roto.core.track.RootTracker

/**
 * The stages of one [clip] that need only its [footage]: plate, masks, root track, key frames. Clip
 * setup previews these before a palette or a scale exists; [ClipPipeline] carries on from them. Each
 * [StageOutput] is built on first use, like [ClipPipeline]'s, and follows the clip's settings, so a
 * new setting takes a new [ClipMasking] over the same [footage].
 */
class ClipMasking(val footage: ClipFootage, private val cache: StageCache, val clip: Clip) {

    val plate: StageOutput<Raster> by lazy {
        CleanPlateBuilder(cache).plate(clip.plate, footage)
    }

    val masks: StageOutput<List<Mask>> by lazy { ForegroundMasker(cache).masks(footage.frames, plate, clip.mask) }

    /** The [masks] with the artist's brush fixes: what a draft is drawn from, and nothing else reads. */
    val fixedMasks: StageOutput<List<Mask>> by lazy { MaskFixer(cache).fixed(masks, clip.fixes) }

    val track: StageOutput<RootTrack> by lazy { RootTracker(cache).track(masks, clip.root) }

    val cycle: StageOutput<CycleAnalysis> by lazy {
        CycleAnalyser(cache).analyse(masks, track, footage.frameRate, clip.cycle, clip.loop)
    }

    /** The artist's markers when set, otherwise the cycle stage's proposal. */
    val keyFrames: KeyFrames by lazy { clip.keyFrames { cycle.get() } }
}
