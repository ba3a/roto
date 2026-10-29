package com.roto.core.profile

/**
 * A profile with two walk facings (east with a marked empty-wall plate and opted in to mirroring)
 * and a non-looping idle from a subfolder.
 */
internal fun sampleProfile() = Profile(
    ramps = listOf("skin", "suit", "visor"),
    clips = listOf(
        Clip(
            action = "walk",
            facing = "EAST",
            source = "IMG_0412.MOV",
            trim = TimeRange(1_200, 4_800),
            plate = PlateSource.EmptyRange(TimeRange(0, 1_000)),
            mirror = true,
        ),
        Clip(action = "walk", facing = "NORTH", source = "IMG_0413.MOV", trim = TimeRange(0, 3_000)),
        Clip(
            action = "idle",
            facing = "SOUTH",
            source = "idle/IMG_0420.MOV",
            trim = TimeRange(500, 2_500),
            loop = false,
        ),
    ),
)
