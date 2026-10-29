package com.roto.core.draft

import com.roto.core.shade.DraftColours
import kotlin.test.Test
import kotlin.test.assertEquals

class DraftStepsTest {

    /** Zone 1 shades 2 (dark), 3, 4 (light); zone 2 shades 6, 7. */
    private val colours = DraftColours(listOf(listOf(2, 3, 4), listOf(6, 7)))

    @Test
    fun `each block takes its majority zone, then that zone's majority colour, at half coverage or more`() {
        val shaded = grid(
            "3334..47",
            "3334..77",
            "2.......",
            "3.......",
        )
        val zones = grid(
            "1111..12",
            "1111..22",
            "1.......",
            "1.......",
        )

        val draft = MajorityReduce.reduce(shaded, zones, factor = 2)

        // Block (1,0) ties 3 and 4, block (0,1) ties 2 and 3 at exactly half coverage: the lower label wins.
        // Block (3,0) is mostly zone 2, so the zone-1 4 in it does not count.
        assertEquals(grid("33.7", "2...").drawing(), draft.colours.drawing())
        assertEquals(grid("11.2", "1...").drawing(), draft.zones.drawing())
    }

    @Test
    fun `an orphan takes its neighbours' majority, a line keeps its pixels`() {
        val draft = Draft(
            colours = grid(
                "33333",
                "34333",
                "33333",
                "3.334",
                "33343",
            ),
            zones = grid(
                "11111",
                "11111",
                "11111",
                "1.111",
                "11111",
            ),
        )

        val cleaned = OrphanCleanup.clean(draft)

        // The lone 4 and the lone hole go; the diagonal 4, 4 pair stays.
        assertEquals(grid("33333", "33333", "33333", "33334", "33343").drawing(), cleaned.colours.drawing())
        assertEquals(1, cleaned.zones[1, 3])
    }

    @Test
    fun `the outline runs inside the silhouette in the project colour or each ramp's darkest`() {
        val draft = Draft(
            colours = grid(
                "......",
                ".3377.",
                ".3377.",
                ".3377.",
            ),
            zones = grid(
                "......",
                ".1122.",
                ".1122.",
                ".1122.",
            ),
        )

        assertEquals(
            grid("......", ".2266.", ".2376.", ".2266.").drawing(),
            Outline.draw(draft, colours.zones, outline = null).colours.drawing(),
        )
        assertEquals(
            grid("......", ".9999.", ".9379.", ".9999.").drawing(),
            Outline.draw(draft, colours.zones, outline = 9).colours.drawing(),
        )
    }

    @Test
    fun `dither checkers the darker side of an edge between adjacent steps of one zone`() {
        val draft = Draft(
            colours = grid(
                "222333",
                "222333",
                "222444",
                "222666",
            ),
            zones = grid(
                "111111",
                "111111",
                "111111",
                "111222",
            ),
        )

        val dithered = RampDither.dither(draft, colours.zones)

        // At even Bayer places, a 2 beside a 3 and a 3 beside a 4 lighten; a 2 beside a 4 skips a step.
        assertEquals(grid("223333", "222434", "222444", "222666").drawing(), dithered.colours.drawing())
    }

    @Test
    fun `finishing reduces, cleans, optionally dithers, and outlines last`() {
        val shaded = grid(
            "33333333",
            "33333333",
            "33333333",
            "33333333",
            "33333333",
            "33333333",
        )
        val zones = grid(
            "11111111",
            "11111111",
            "11111111",
            "11111111",
            "11111111",
            "11111111",
        )

        val finished = DraftFinisher.finish(shaded, zones, factor = 2, colours, DraftSettings())

        assertEquals(grid("2222", "2332", "2222").drawing(), finished.colours.drawing())
    }
}
