package com.roto.core.profile

import com.roto.core.cycle.CycleSettings
import com.roto.core.framebox.ScaleMarks
import com.roto.core.framebox.ScaleSettings
import com.roto.core.mask.MaskSettings
import com.roto.core.track.RootSettings

/**
 * A profile with two walk facings (east with a marked empty-wall plate, its own markers, and opted
 * in to mirroring) and a non-looping idle from a subfolder, its mask tuned and performed on the
 * spot; scaled from marks on the east walk and squashed a little.
 */
internal fun sampleProfile() = Profile(
    ramps = listOf("skin", "suit", "visor"),
    scale = ScaleSettings(
        bodyHeight = 52,
        marks = ScaleMarks("walk_EAST", frame = 12, headTopY = 38, feetY = 451),
        squash = 0.9,
    ),
    clips = listOf(
        Clip(
            action = "walk",
            facing = "EAST",
            source = "IMG_0412.MOV",
            trim = TimeRange(1_200, 4_800),
            plate = PlateSource.EmptyRange(TimeRange(0, 1_000)),
            cycle = CycleSettings(repeatsPerCycle = 2),
            markers = listOf(3, 6, 9, 12, 15, 18, 21, 24, 27),
            mirror = true,
        ),
        Clip(action = "walk", facing = "NORTH", source = "IMG_0413.MOV", trim = TimeRange(0, 3_000)),
        Clip(
            action = "idle",
            facing = "SOUTH",
            source = "idle/IMG_0420.MOV",
            trim = TimeRange(500, 2_500),
            mask = MaskSettings(lightnessWeight = 0.5, high = 0.12, closeRadius = 3),
            root = RootSettings(stabilise = false, smoothing = 1),
            loop = false,
        ),
    ),
)
