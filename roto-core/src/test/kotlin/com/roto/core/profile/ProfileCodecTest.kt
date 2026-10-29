package com.roto.core.profile

import com.roto.core.cycle.CycleSettings
import com.roto.core.mask.MaskSettings
import com.roto.core.shade.ShadeSettings
import com.roto.core.track.RootSettings
import com.roto.core.zone.ZoneSettings
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ProfileCodecTest {

    private lateinit var tempRoot: Path

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-profile-codec")
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `reading what was written gives back an equal profile`() {
        assertEquals(sampleProfile(), read(encode(sampleProfile())))
        assertEquals(Profile(), read(encode(Profile())))
    }

    @Test
    fun `round trip through a file`() {
        val file = tempRoot.resolve("worker_float_tech").resolve(ProfileCodec.fileName)

        ProfileCodec.write(sampleProfile(), file)

        assertEquals(sampleProfile(), ProfileCodec.read(file))
    }

    @Test
    fun `writing is byte-stable and keeps the field names`() {
        val text = encode(sampleProfile())

        assertEquals(text, encode(read(text)))
        assertFalse('\r' in text)
        listOf("ramps", "clips", "action", "facing", "source", "trim", "startMs", "endMs", "plate", "type", "range")
            .plus(listOf("scale", "bodyHeight", "marks", "clip", "frame", "headTopY", "feetY", "squash"))
            .plus(listOf("mask", "lightnessWeight", "low", "high", "openRadius", "closeRadius"))
            .plus(listOf("root", "stabilise", "hipBandTop", "hipBandBottom", "smoothing"))
            .plus(listOf("cycle", "minPeriodMs", "maxPeriodMs", "repeatsPerCycle", "contactBand", "keyFrames"))
            .plus(listOf("markers", "zoning", "zones", "name", "ramp", "samples", "x", "y"))
            .plus(listOf("shading", "lowPercentile", "highPercentile", "loop", "mirror"))
            .forEach { assertContains(text, "\"$it\"") }
        assertContains(text, "\"type\": \"emptyRange\"")
        assertContains(text, "\"type\": \"clipMedian\"")
        // The id is derived from action and facing, so it is not stored.
        assertFalse("\"id\"" in text)
    }

    @Test
    fun `optional fields take their defaults`() {
        val profile = read(
            """
            {
              "clips": [
                { "action": "walk", "facing": "EAST", "source": "a.mov", "trim": { "startMs": 0, "endMs": 10 } }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(emptyList(), profile.ramps)
        assertEquals(true, profile.clips.single().loop)
        assertEquals(false, profile.clips.single().mirror)
        assertEquals(PlateSource.ClipMedian, profile.clips.single().plate)
        assertEquals(MaskSettings(), profile.clips.single().mask)
        assertEquals(RootSettings(), profile.clips.single().root)
        assertEquals(CycleSettings(), profile.clips.single().cycle)
        assertEquals(null, profile.clips.single().markers)
        assertEquals(ZoneSettings(), profile.clips.single().zoning)
        assertEquals(ShadeSettings(), profile.clips.single().shading)
        assertEquals(Profile(), read("{}"))
    }

    @Test
    fun `unknown keys, malformed JSON and invalid values are profile errors`() {
        val valid = encode(sampleProfile())

        val error = assertFailsWith<ProfileException> { read(valid.replace("\"mirror\"", "\"mirrored\"")) }
        assertContains(error.message.orEmpty(), "mirrored")
        assertFailsWith<ProfileException> { read(valid.dropLast(4)) }
        assertFailsWith<ProfileException> { read(valid.replace("\"endMs\": 4800", "\"endMs\": 1000")) }
        assertFailsWith<ProfileException> { read(valid.replace("IMG_0413.MOV", "../IMG_0413.MOV")) }
        assertFailsWith<ProfileException> { read(valid.replace("\"NORTH\"", "\"EAST\"")) }
    }

    private fun read(text: String): Profile = ProfileCodec.read(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))

    private fun encode(profile: Profile): String =
        ByteArrayOutputStream().also { ProfileCodec.write(profile, it) }.toString(Charsets.UTF_8)
}
