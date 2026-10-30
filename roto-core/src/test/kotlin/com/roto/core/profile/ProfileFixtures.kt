package com.roto.core.profile

import com.roto.core.cycle.CycleSettings
import com.roto.core.draft.DraftSettings
import com.roto.core.framebox.ScaleMarks
import com.roto.core.framebox.ScaleSettings
import com.roto.core.mask.BrushMode
import com.roto.core.mask.BrushPoint
import com.roto.core.mask.BrushStroke
import com.roto.core.mask.MaskSettings
import com.roto.core.shade.ShadeSettings
import com.roto.core.track.RootSettings
import com.roto.core.zone.Zone
import com.roto.core.zone.ZoneSample
import com.roto.core.zone.ZoneSettings
import com.roto.core.zone.ZoneStroke

/**
 * A profile with two walk facings (east with a marked empty-wall plate, its own markers, and opted
 * in to mirroring), a non-looping idle from a subfolder, its mask tuned and performed on the spot, and
 * a still; scaled from marks on the east walk and squashed a little.
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
            fixes = listOf(BrushStroke(6, BrushMode.ERASE, 4, listOf(BrushPoint(100, 300), BrushPoint(120, 310)))),
            zoning = ZoneSettings(
                zones = listOf(
                    Zone("face", ramp = "skin", samples = listOf(ZoneSample(frame = 3, x = 210, y = 60))),
                    Zone("jacket", ramp = "suit", samples = listOf(ZoneSample(3, 205, 180), ZoneSample(9, 230, 190))),
                    Zone("trousers", ramp = "suit", samples = listOf(ZoneSample(3, 200, 320))),
                ),
                fixes = listOf(ZoneStroke(3, "jacket", 5, listOf(BrushPoint(200, 300)))),
            ),
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
            shading = ShadeSettings(lowPercentile = 2.0, highPercentile = 98.0),
            draft = DraftSettings(dither = true),
            loop = false,
        ),
        Clip.still("idle", "EAST", "photos/pose.jpg", "photos/wall.jpg"),
    ),
)
