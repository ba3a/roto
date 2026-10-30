package com.roto.core.render

import com.roto.core.cache.StageCache
import com.roto.core.handoff.HandoffWriter
import com.roto.core.palette.ProjectPalette
import com.roto.core.profile.Clip
import com.roto.core.profile.Profile
import com.roto.core.profile.ProfileFolder
import com.roto.core.project.Project
import com.roto.core.video.Ffmpeg
import java.nio.file.Path

/**
 * Renders a profile's clips into their handoffs: each clip runs through its [ClipPipeline] against
 * the profile's cache, and its handoff is written by [HandoffWriter], which keeps whatever the artist
 * has painted. A clip whose stages are all cached renders without decoding or computing anything.
 *
 * Clips render in order and the first that cannot stops the render; handoffs already written stay.
 */
class ProfileRenderer(private val project: Project, private val ffmpeg: Ffmpeg = Ffmpeg()) {

    /** What a render did: every handoff [files] it wrote, in clip order. */
    class Report(val files: List<Path>)

    /** Renders [clips] — all of [profile]'s unless told otherwise — of the profile stored in [folder]. */
    fun render(profile: Profile, folder: ProfileFolder, clips: List<Clip> = profile.clips): Report {
        if (clips.isEmpty()) throw RenderException("There is nothing to render; the profile has no clips yet")
        val drawing = Drawing(project.config.canvas, ProjectPalette.load(project), profile.scale)
        val cache = StageCache(folder.cacheFolder)
        val written = clips.map { clip ->
            val footage = ClipFootage(ffmpeg, cache, folder.sourceFile(clip), clip.trim)
            val pipeline = ClipPipeline(ClipMasking(footage, cache, clip), cache, drawing)
            val render = try {
                pipeline.handoff()
            } catch (e: IllegalArgumentException) {
                throw RenderException("${clip.id}: ${e.message}", e)
            }
            folder.handoffFile(clip).also { HandoffWriter.write(it, render) }
        }
        return Report(written)
    }
}
