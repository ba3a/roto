package com.roto.core.project

import com.roto.core.template.RelativePath
import com.roto.core.template.TemplateValues
import java.nio.file.Path

/**
 * A consuming project: its [config] and the [root] folder holding `roto.json`. Turns the config's
 * relative paths and templates into files under [root]; every path stays inside it.
 */
class Project(root: Path, val config: ProjectConfig) {

    val root: Path = root.toAbsolutePath().normalize()

    /** The project's palette file. */
    val paletteFile: Path get() = RelativePath.resolve(root, config.palette, "palette")

    /** The project's named-ramp file. */
    val rampsFile: Path get() = RelativePath.resolve(root, config.ramps, "ramps")

    /** Where frame [frame] of [profile]'s [action] clip facing [facing] is published. */
    fun frameFile(profile: String, action: String, facing: String, frame: Int): Path {
        requireKnown(action, facing)
        return config.output.resolve(root, TemplateValues(profile, action, facing, frame))
    }

    /** Where the manifest of [profile]'s [action] clip facing [facing] is published. */
    fun manifestFile(profile: String, action: String, facing: String): Path {
        requireKnown(action, facing)
        return config.manifest.resolve(root, TemplateValues(profile, action, facing))
    }

    /** The `afterPublish` command for [profile], arguments expanded; empty when none is configured. */
    fun afterPublishCommand(profile: String): List<String> =
        config.afterPublish.map { it.expand(TemplateValues(profile = profile)) }

    private fun requireKnown(action: String, facing: String) {
        require(action in config.actions) { "Action '$action' is not one of ${config.actions}" }
        require(facing in config.facings) { "Facing '$facing' is not one of ${config.facings}" }
    }

    companion object {
        /** Opens the project whose `roto.json` sits in [root]. */
        fun open(root: Path): Project =
            Project(root, ProjectConfigCodec.read(root.resolve(ProjectConfigCodec.FILE_NAME)))
    }
}
