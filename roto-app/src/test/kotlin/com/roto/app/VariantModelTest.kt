package com.roto.app

import com.roto.core.palette.ProjectPalette
import com.roto.core.profile.ProfileCodec
import com.roto.core.project.Project
import com.roto.core.variant.RampSwapPair
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VariantModelTest {

    private lateinit var tempRoot: Path
    private lateinit var bench: ClipBench

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-variant")
        bench = ClipBench(tempRoot)
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    private fun model() = VariantModel(bench.repository, ProjectPalette.load(Project.open(bench.game)))

    /** A model with every gate holding. */
    private fun ready(): VariantModel = model().apply {
        chooseBase("hero")
        addSwap("skin", "suit")
        rename("hero_dark")
    }

    @Test
    fun `profiles with clips are the bases`() {
        assertEquals(listOf("hero"), model().bases)
    }

    @Test
    fun `every gate holding lets the variant be created`() {
        assertNull(ready().problem)
        assertTrue(ready().canCreate)
    }

    @Test
    fun `no base blocks creation`() {
        val variant = ready()
        val blank = model().apply {
            addSwap("skin", "suit")
            rename("x")
        }

        assertContains(assertNotNull(blank.problem), "Choose the profile")
        assertFalse(blank.canCreate)
        assertNull(blank.create())
        assertNotNull(variant.create())
    }

    @Test
    fun `no swap blocks creation`() {
        val variant = model().apply {
            chooseBase("hero")
            rename("x")
        }

        assertContains(assertNotNull(variant.problem), "at least one ramp swap")
        assertNull(variant.create())
        assertFalse(bench.repository.exists("x"))
    }

    @Test
    fun `a ramp swapped twice blocks creation`() {
        val variant = ready().apply { addSwap("skin", "skin") }

        assertContains(assertNotNull(variant.problem), "swapped twice")
        assertFalse(variant.canCreate)
    }

    @Test
    fun `a ramp missing from ramps json blocks creation`() {
        val variant = ready().apply { addSwap("hair", "suit") }

        assertContains(assertNotNull(variant.problem), "'hair' is not in ramps.json")
        assertFalse(variant.canCreate)
    }

    @Test
    fun `a bad name blocks creation`() {
        val variant = ready().apply { rename("../x") }

        assertContains(assertNotNull(variant.problem), "Name the variant")
        assertFalse(variant.canCreate)
    }

    @Test
    fun `a name in use blocks creation, ignoring case`() {
        val variant = ready().apply { rename("HERO") }

        assertContains(assertNotNull(variant.problem), "already exists")
        assertFalse(variant.canCreate)
    }

    @Test
    fun `the created profile decodes with its variant and no clips`() {
        val variant = ready().apply { chooseOutline(0xFF1E2A4A.toInt()) }

        assertEquals("hero_dark", variant.create())

        val stored = ProfileCodec.read(bench.repository.folder("hero_dark").profileFile)
        assertEquals(emptyList(), stored.clips)
        val of = assertNotNull(stored.variant)
        assertEquals("hero", of.base)
        assertEquals(listOf(RampSwapPair("skin", "suit")), of.swaps)
        assertEquals(0xFF1E2A4A.toInt(), of.outline)
    }

    @Test
    fun `a swap can be removed again`() {
        val variant = ready().apply { removeSwap(0) }

        assertEquals(emptyList(), variant.state.swaps)
        assertFalse(variant.canCreate)
    }
}
