package com.roto.core.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FfprobeParserTest {

    @Test
    fun `a landscape video without rotation`() {
        val info = FfprobeParser.parse(probe(stream = """"width": 1920, "height": 1080, "avg_frame_rate": "30/1""""))

        assertEquals(VideoInfo(1920, 1080, 4_000, FrameRate(30, 1), rotation = 0), info)
    }

    @Test
    fun `a portrait phone video with a display matrix reports its upright size`() {
        val info = FfprobeParser.parse(
            probe(
                stream = """
                    "width": 1920, "height": 1080, "avg_frame_rate": "30000/1001", "duration": "5.005000",
                    "side_data_list": [
                        { "side_data_type": "Display Matrix", "displaymatrix": "...", "rotation": -90 }
                    ]
                """,
            ),
        )

        assertEquals(VideoInfo(1080, 1920, 5_005, FrameRate(30000, 1001), rotation = 90), info)
    }

    @Test
    fun `an older ffprobe's rotate tag is clockwise`() {
        val info = FfprobeParser.parse(
            probe(stream = """"width": 1280, "height": 720, "avg_frame_rate": "60/1", "tags": { "rotate": "270" }"""),
        )

        assertEquals(1280, info.height)
        assertEquals(270, info.rotation)
    }

    @Test
    fun `a fractional matrix rotation and an upside-down video`() {
        val info = FfprobeParser.parse(
            probe(
                """"width": 640, "height": 480, "avg_frame_rate": "25/1", "side_data_list": [{ "rotation": 180.0 }]""",
            ),
        )

        assertEquals(VideoInfo(640, 480, 4_000, FrameRate(25, 1), rotation = 180), info)
    }

    @Test
    fun `an unknown average rate falls back to the base rate, and duration to the container's`() {
        val info = FfprobeParser.parse(
            probe(stream = """"width": 640, "height": 480, "avg_frame_rate": "0/0", "r_frame_rate": "24/1""""),
        )

        assertEquals(FrameRate(24, 1), info.frameRate)
        assertEquals(4_000, info.durationMs)
    }

    @Test
    fun `duration rounds to the nearest millisecond`() {
        val stream = """"width": 640, "height": 480, "avg_frame_rate": "25/1", "duration": "1.2345""""

        assertEquals(1_235, FfprobeParser.parse(probe(stream)).durationMs)
    }

    @Test
    fun `output describing no usable video is rejected`() {
        assertFailsWith<IllegalArgumentException> { FfprobeParser.parse("""{ "streams": [], "format": {} }""") }
        assertFailsWith<IllegalArgumentException> { FfprobeParser.parse("not json") }
        assertFailsWith<IllegalArgumentException> {
            FfprobeParser.parse(probe(stream = """"width": 640, "height": 480, "avg_frame_rate": "0/0""""))
        }
        assertFailsWith<IllegalArgumentException> {
            FfprobeParser.parse(
                """{ "streams": [{ "width": 640, "height": 480, "avg_frame_rate": "25/1" }], "format": {} }""",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            FfprobeParser.parse(
                probe(
                    """"width": 640, "height": 480, "avg_frame_rate": "25/1", "side_data_list": [{ "rotation": 45 }]""",
                ),
            )
        }
    }

    /** ffprobe's output for one video stream, with the container reporting a 4 s duration. */
    private fun probe(stream: String): String =
        """
        {
            "streams": [{ "index": 0, "codec_name": "h264", "codec_type": "video", $stream }],
            "format": { "filename": "IMG_0412.MOV", "format_name": "mov,mp4,m4a,3gp,3g2,mj2", "duration": "4.000000" }
        }
        """
}
