package com.roto.core.plate

import com.roto.core.cache.CacheKey
import com.roto.core.cache.StageCache
import com.roto.core.cache.StageOutput
import com.roto.core.image.Argb
import com.roto.core.image.Raster
import com.roto.core.profile.PlateSource
import com.roto.core.profile.TimeRange
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CleanPlateBuilderTest {

    private lateinit var tempRoot: Path
    private lateinit var builder: CleanPlateBuilder

    private val wall = Raster.of(6, 3) { x, y -> Argb.of(90 + x * 20, 70 + y * 30, 50) }
    private val figure = Argb.of(220, 40, 40)

    /** A figure two pixels wide crossing the wall, two pixels a frame. */
    private val walk = (0 until 3).map { step ->
        Raster.of(6, 3) { x, y -> if (x in step * 2 until step * 2 + 2) figure else wall[x, y] }
    }

    /** The empty wall with a little sensor noise. */
    private val emptyWall = listOf(-2, 0, 2).map { noise ->
        Raster.of(6, 3) { x, y -> Argb.of(90 + x * 20 + noise, 70 + y * 30, 50) }
    }

    private var frameLoads = 0
    private val extractedRanges = mutableListOf<TimeRange>()

    private val footage = object : PlateFootage {
        override val frames: StageOutput<List<Raster>>
            get() = StageOutput(CacheKey.of("frames", 1) { put("clip", "walk_EAST") }) {
                frameLoads++
                walk
            }

        override fun range(range: TimeRange): StageOutput<List<Raster>> {
            val key = CacheKey.of("frames", 1) { put("range", range, TimeRange.serializer()) }
            return StageOutput(key) {
                frameLoads++
                extractedRanges += range
                emptyWall
            }
        }

        override fun photo(source: String): StageOutput<List<Raster>> =
            StageOutput(CacheKey.of("still", 1) { put("source", source) }) {
                frameLoads++
                listOf(wall)
            }
    }

    @BeforeTest
    fun setUp() {
        tempRoot = createTempDirectory("roto-plate")
        builder = CleanPlateBuilder(StageCache(tempRoot.resolve("cache")))
    }

    @AfterTest
    fun tearDown() {
        tempRoot.toFile().deleteRecursively()
    }

    @Test
    fun `the clip median plate is the wall behind a walk across`() {
        val plate = builder.plate(PlateSource.ClipMedian, footage)

        assertEquals(wall, plate.get())
        assertEquals(emptyList(), extractedRanges)
    }

    @Test
    fun `an empty range plate is the median of that range's frames`() {
        val plate = builder.plate(PlateSource.EmptyRange(TimeRange(0, 2_000)), footage)

        assertEquals(wall, plate.get())
        assertEquals(listOf(TimeRange(0, 2_000)), extractedRanges)
    }

    @Test
    fun `a photo plate is the photo, and a clip's own frames are not touched`() {
        val plate = builder.plate(PlateSource.Photo("wall.jpg"), footage)

        assertEquals(wall, plate.get())
        assertEquals(1, frameLoads)
        assertNotEquals(builder.plate(PlateSource.Photo("other.jpg"), footage).key, plate.key)
    }

    @Test
    fun `a cached plate loads no frames`() {
        builder.plate(PlateSource.ClipMedian, footage).get()
        frameLoads = 0

        val again = builder.plate(PlateSource.ClipMedian, footage).get()

        assertEquals(wall, again)
        assertEquals(0, frameLoads)
    }

    @Test
    fun `the plate is keyed by the frames it is the median of`() {
        val clipMedian = builder.plate(PlateSource.ClipMedian, footage).key
        val range = builder.plate(PlateSource.EmptyRange(TimeRange(0, 2_000)), footage).key
        val otherRange = builder.plate(PlateSource.EmptyRange(TimeRange(0, 2_500)), footage).key

        assertEquals(CleanPlateBuilder.STAGE, clipMedian.stage)
        assertNotEquals(clipMedian, range)
        assertNotEquals(range, otherRange)
        assertEquals(range, builder.plate(PlateSource.EmptyRange(TimeRange(0, 2_000)), footage).key)
    }
}
