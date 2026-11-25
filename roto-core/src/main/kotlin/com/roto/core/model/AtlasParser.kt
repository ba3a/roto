package com.roto.core.model

import com.roto.core.image.Raster

/**
 * One region of a libGDX atlas page: where its crop sits on the page and where that crop sits in the
 * original frame.
 *
 * @property name the region's name
 * @property index the region's `index` (-1 when the atlas gives none)
 * @property x the crop's left edge on the page, in pixels from the page's left
 * @property y the crop's top edge on the page, in pixels from the page's top
 * @property width the crop's width
 * @property height the crop's height
 * @property origWidth the untrimmed frame's width
 * @property origHeight the untrimmed frame's height
 * @property offsetX the crop's left edge in the untrimmed frame
 * @property offsetY the gap between the crop's bottom edge and the untrimmed frame's bottom edge
 */
data class AtlasRegion(
    val name: String,
    val index: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val origWidth: Int,
    val origHeight: Int,
    val offsetX: Int,
    val offsetY: Int,
)

/**
 * One page of an atlas: its PNG file name and the regions on it.
 *
 * @property name the page's PNG file name, as written in the atlas
 * @property regions the regions on the page, in file order
 */
data class AtlasPage(val name: String, val regions: List<AtlasRegion>)

/**
 * Reads the libGDX TexturePacker text format and rebuilds frames from it. Pure over the atlas text and
 * page rasters; the caller loads the page PNGs.
 *
 * A region that is rotated on its page is refused, naming the region: TexturePacker does not rotate by
 * default.
 */
object AtlasParser {

    /** Parses [text] into pages and regions; a malformed file, or a rotated region, fails with a [ModelException]. */
    fun parse(text: String): List<AtlasPage> {
        val pages = ArrayList<AtlasPage>()
        var pageName: String? = null
        val regions = ArrayList<AtlasRegion>()
        var current: RegionBuilder? = null
        var expectPage = true

        fun closeRegion() {
            current?.let { regions += it.build() }
            current = null
        }

        fun closePage() {
            closeRegion()
            pageName?.let { pages += AtlasPage(it, regions.toList()) }
            pageName = null
            regions.clear()
        }

        val lines = text.lines()
        for ((i, raw) in lines.withIndex()) {
            val lineNo = i + 1
            val line = raw.trim()
            if (line.isEmpty()) {
                closePage()
                expectPage = true
                continue
            }
            val colon = line.indexOf(':')
            if (colon < 0) {
                if (expectPage) {
                    closePage()
                    pageName = line
                    expectPage = false
                } else {
                    closeRegion()
                    current = RegionBuilder(line)
                }
                continue
            }
            val key = line.substring(0, colon).trim()
            val value = line.substring(colon + 1).trim()
            val region = current
            if (region != null) {
                region.set(key, value, lineNo)
            } else if (pageName == null) {
                throw ModelException("Atlas line $lineNo: \"$line\" is outside any page")
            }
        }
        closePage()
        if (pages.isEmpty()) throw ModelException("Atlas has no pages")
        return pages
    }

    /**
     * The frame of [region] at its original size, cut from [page]: the crop is placed [AtlasRegion.offsetX]
     * from the left and [AtlasRegion.offsetY] up from the bottom (libGDX measures the offset from the
     * bottom-left corner), the rest is transparent.
     */
    fun rebuild(region: AtlasRegion, page: Raster): Raster {
        if (region.x + region.width > page.width || region.y + region.height > page.height) {
            throw ModelException(
                "Atlas region \"${region.name}\" reaches outside its ${page.width}x${page.height} page",
            )
        }
        val top = region.origHeight - region.offsetY - region.height
        val fitsAcross = region.offsetX >= 0 && region.offsetX + region.width <= region.origWidth
        if (!fitsAcross || top < 0 || region.offsetY < 0) {
            throw ModelException(
                "Atlas region \"${region.name}\" does not fit in its original " +
                    "${region.origWidth}x${region.origHeight} frame",
            )
        }
        val frame = Raster(region.origWidth, region.origHeight)
        for (dy in 0 until region.height) {
            for (dx in 0 until region.width) {
                frame[region.offsetX + dx, top + dy] = page[region.x + dx, region.y + dy]
            }
        }
        return frame
    }
}

private class RegionBuilder(private val name: String) {
    private var rotate = false
    private var xy: Pair<Int, Int>? = null
    private var size: Pair<Int, Int>? = null
    private var orig: Pair<Int, Int>? = null
    private var offset: Pair<Int, Int> = 0 to 0
    private var index = -1

    fun set(key: String, value: String, lineNo: Int) {
        when (key) {
            "rotate" -> rotate = value != "false" && value != "0"
            "xy" -> xy = pair(key, value, lineNo)
            "size" -> size = pair(key, value, lineNo)
            "orig" -> orig = pair(key, value, lineNo)
            "offset" -> offset = pair(key, value, lineNo)
            "index" -> index = value.toIntOrNull()
                ?: throw ModelException("Atlas line $lineNo: region \"$name\" has index \"$value\", not a number")
        }
    }

    fun build(): AtlasRegion {
        if (rotate) throw ModelException("Atlas region \"$name\" is rotated; Roto does not read rotated regions")
        val (x, y) = xy ?: missing("xy")
        val (w, h) = size ?: missing("size")
        val (ow, oh) = orig ?: (w to h)
        val (ox, oy) = offset
        if (minOf(w, h, ow, oh) < 1) {
            throw ModelException("Atlas region \"$name\" has a size below 1 pixel")
        }
        return AtlasRegion(name, index, x, y, w, h, ow, oh, ox, oy)
    }

    private fun missing(key: String): Nothing = throw ModelException("Atlas region \"$name\" has no $key")

    private fun pair(key: String, value: String, lineNo: Int): Pair<Int, Int> {
        val parts = value.split(',').map { it.trim().toIntOrNull() }
        if (parts.size != 2 || parts.any { it == null }) {
            throw ModelException("Atlas line $lineNo: region \"$name\" has $key \"$value\", expected two integers")
        }
        return parts[0]!! to parts[1]!!
    }
}
