package com.roto.core.mask

import com.roto.core.cache.CacheKey
import com.roto.core.cache.MaskSequenceCacheCodec
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.image.Mask
import kotlinx.serialization.builtins.ListSerializer

/**
 * The mask fix stage: a clip's masks with the artist's [BrushStroke]s applied by [MaskBrush], cached
 * under the masks and the strokes. With no strokes it is the masks themselves, so an unfixed clip
 * stores nothing twice and keeps every downstream key.
 *
 * It sits beside the mask stage's output, not on it. The root track, the cycle and the ground line
 * read the raw masks, and key frames come from those, so a fix painted on a key frame cannot move
 * that key frame or re-crop the clip. Only what a draft is drawn from reads the fixed masks.
 */
class MaskFixer(private val cache: StageCache) {

    fun fixed(masks: StageOutput<List<Mask>>, strokes: List<BrushStroke>): StageOutput<List<Mask>> {
        if (strokes.isEmpty()) return masks
        val key = CacheKey.of(STAGE, VERSION) {
            put("masks", masks.key)
            put("strokes", strokes, ListSerializer(BrushStroke.serializer()))
        }
        return StageOutput(key) {
            cache.get(key, MaskSequenceCacheCodec) {
                val clipMasks = masks.get()
                strokes.firstOrNull { it.frame !in clipMasks.indices }?.let { stroke ->
                    throw IllegalArgumentException(
                        "A mask fix is on frame ${stroke.frame}, but the clip has ${clipMasks.size} frames",
                    )
                }
                val byFrame = strokes.groupBy { it.frame }
                clipMasks.mapIndexed { frame, mask -> MaskBrush.apply(mask, byFrame[frame].orEmpty()) }
            }
        }
    }

    companion object {
        const val STAGE = "mask-fix"
        private const val VERSION = 1
    }
}
