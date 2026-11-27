package com.roto.core.review

import com.roto.core.model.ModelMeasure
import com.roto.core.model.ModelRepository
import com.roto.core.palette.ProjectPalette
import com.roto.core.profile.Clip
import com.roto.core.project.Project

/** Builds the [LintContext] of a clip; the one place the CLI and the app both get it from. */
object LintContexts {
    /**
     * The context for [clip]: the project's palette and ramps, the clip's depth and loop settings, and,
     * when the clip names a model in [models], that model's measurement and durations. The draft always
     * has its outline, so the outline rule is on. [clipDurationsMs] is the duration of every frame of the
     * clip's handoff.
     */
    fun of(
        project: Project,
        clip: Clip,
        models: ModelRepository,
        clipDurationsMs: List<Int>,
        settings: LintSettings = LintSettings(),
    ): LintContext {
        val colours = ProjectPalette.load(project)
        val reference = clip.model?.let { key ->
            val model = models.load(key)
            ModelReference(ModelMeasure.measure(model.frames.map { it.raster }), model.frames.map { it.durationMs })
        }
        return LintContext(
            colours.palette,
            colours.ramps,
            outline = true,
            depth = clip.depth,
            loop = clip.loop,
            model = reference,
            clipDurationsMs = clipDurationsMs,
            settings = settings,
        )
    }
}
