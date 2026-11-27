package com.roto.core.variant

import com.roto.core.palette.Palette
import com.roto.core.palette.ProjectPalette
import com.roto.core.palette.Ramp
import com.roto.core.palette.RampSet
import com.roto.core.profile.Profile
import com.roto.core.profile.ProfileCodec
import com.roto.core.profile.ProfileException
import com.roto.core.profile.ProfileRepository
import com.roto.core.profile.sampleProfile
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VariantOfTest {

    private val dark = 0xFF202020.toInt()
    private val light = 0xFFE0E0E0.toInt()
    private val red = 0xFFAA2222.toInt()
    private val colours = ProjectPalette(
        Palette(listOf(dark, light, red)),
        RampSet(ramps = listOf(Ramp("skin", listOf(dark, light)), Ramp("skin_dark", listOf(dark, red)))),
    )
    private val variant = VariantOf("hero", listOf(RampSwapPair("skin", "skin_dark")), outline = red)

    private lateinit var library: Path
    private lateinit var repository: ProfileRepository

    @BeforeTest
    fun setUp() {
        library = createTempDirectory("roto-variant")
        repository = ProfileRepository(library, "faultory")
    }

    @AfterTest
    fun tearDown() {
        library.toFile().deleteRecursively()
    }

    private fun save(id: String, profile: Profile) {
        repository.create(id)
        repository.save(id, profile)
    }

    @Test
    fun `a variant with clips is refused naming the conflict`() {
        val clips = sampleProfile().clips
        val error = assertFailsWith<IllegalArgumentException> { Profile(clips = clips, variant = variant) }

        assertContains(error.message.orEmpty(), "variant of 'hero'")
        assertContains(error.message.orEmpty(), "own clips")
    }

    @Test
    fun `a swap source twice or a blank name is refused`() {
        assertFailsWith<IllegalArgumentException> {
            VariantOf("hero", listOf(RampSwapPair("a", "b"), RampSwapPair("a", "c")))
        }
        assertFailsWith<IllegalArgumentException> { RampSwapPair("", "b") }
        assertFailsWith<IllegalArgumentException> { VariantOf("../other", emptyList()) }
    }

    @Test
    fun `a variant loads when its base and ramps exist`() {
        save("hero", sampleProfile())
        save("alt", Profile(variant = variant))

        assertEquals(variant, repository.load("alt", colours).variant)
        assertEquals(variant, repository.load("alt").variant)
    }

    @Test
    fun `a missing base fails the load naming the field`() {
        save("alt", Profile(variant = variant))

        val error = assertFailsWith<ProfileException> { repository.load("alt") }

        assertContains(error.message.orEmpty(), "variant.base")
    }

    @Test
    fun `a missing ramp or outline fails the load naming the field`() {
        save("hero", sampleProfile())
        save("alt", Profile(variant = VariantOf("hero", listOf(RampSwapPair("skin", "ghost")))))
        save("out", Profile(variant = VariantOf("hero", emptyList(), outline = 0xFF123456.toInt())))

        val ramp = assertFailsWith<ProfileException> { repository.load("alt", colours) }
        val outline = assertFailsWith<ProfileException> { repository.load("out", colours) }
        assertContains(ramp.message.orEmpty(), "variant.swaps.to")
        assertContains(outline.message.orEmpty(), "variant.outline")
    }

    @Test
    fun `a variant of a variant is refused`() {
        save("hero", sampleProfile())
        save("alt", Profile(variant = variant))
        save("alt2", Profile(variant = VariantOf("alt", emptyList())))

        val error = assertFailsWith<ProfileException> { repository.load("alt2") }

        assertContains(error.message.orEmpty(), "itself a variant")
    }

    @Test
    fun `the codec round trips a variant and rejects unknown keys`() {
        val profile = Profile(variant = variant)
        val file = library.resolve("profile.json")

        ProfileCodec.write(profile, file)
        assertEquals(profile, ProfileCodec.read(file))

        file.toFile().writeText(file.toFile().readText().replace("\"swaps\"", "\"swapz\""))
        assertFailsWith<ProfileException> { ProfileCodec.read(file) }
    }
}
