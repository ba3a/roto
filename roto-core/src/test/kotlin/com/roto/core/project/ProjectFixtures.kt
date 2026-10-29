package com.roto.core.project

import com.roto.core.template.PathTemplate
import com.roto.core.template.Template

/** The `roto.json` the spec gives as its example, which `src/test/resources/project/roto.json` holds too. */
internal fun sampleConfig() = ProjectConfig(
    palette = "palette/faultory.gpl",
    ramps = "palette/ramps.json",
    facings = listOf("NORTH", "EAST", "SOUTH", "WEST"),
    mirrorPairs = listOf(MirrorPair("EAST", "WEST")),
    actions = listOf("idle", "walk", "working", "carry"),
    canvas = Canvas(width = 40, height = 60, groundRow = 59),
    output = PathTemplate("raw-art/{profile}/{action}_{facing:lower}/{frame:000}.png"),
    manifest = PathTemplate("raw-art/{profile}/{action}_{facing:lower}/clip.json"),
    afterPublish = listOf(Template("./gradlew.bat"), Template(":editor:syncSkin"), Template("--args={profile}")),
)
