package com.roto.core.preset

import com.roto.core.palette.ProjectPalette
import com.roto.core.project.Project

/** The inverse of [ProjectScaffolder]: turns an open project back into a [ProjectPreset]. */
object ProjectExporter {

    /**
     * The preset for [project], called [name] and described by [description]. It holds the project's
     * palette and ramps, and leaves out the id, the folder and `afterPublish`. The output paths must
     * match a named [OutputLayout]; otherwise this throws [PresetException]. A palette or ramps file
     * that fails to load propagates unchanged.
     */
    fun export(project: Project, name: String, description: String): ProjectPreset {
        val config = project.config
        val recognised = OutputLayout.recognise(config.output, config.manifest)
            ?: throw PresetException("These output paths are custom; choose a layout to export")
        val colours = ProjectPalette.load(project)
        return try {
            ProjectPreset(
                name, description, config.facings, config.mirrorPairs, config.actions, config.canvas,
                recognised.layout, recognised.folder, colours.palette, colours.ramps,
            )
        } catch (e: IllegalArgumentException) {
            throw PresetException(e.message ?: "Invalid preset", e)
        }
    }
}
