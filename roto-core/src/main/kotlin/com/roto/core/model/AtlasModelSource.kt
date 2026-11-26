package com.roto.core.model

import com.roto.core.image.PngCodec
import com.roto.core.image.Raster
import com.roto.core.project.Canvas
import com.roto.core.template.PathTemplate
import com.roto.core.template.Template
import com.roto.core.template.TemplateVariable
import java.io.IOException
import java.nio.file.Path

/** The name pattern the import dialog starts with when the project's output template gives none. */
const val DEFAULT_ATLAS_PATTERN = "{action}_{facing:lower}_{frame:000}"

/** Frame duration in ms an action gets until the artist sets one. */
const val DEFAULT_ATLAS_DURATION_MS = 100

/**
 * The name pattern for [output]: its path segments that name an action, facing or frame, joined by
 * `_`, with the profile and the file extension left out. `raw-art/{profile}/{action}_{facing:lower}/{frame:000}.png`
 * gives `{action}_{facing:lower}_{frame:000}`. An output that does not give a pattern using all three
 * variables falls back to [DEFAULT_ATLAS_PATTERN].
 */
fun defaultAtlasPattern(output: PathTemplate): String {
    val wanted = setOf(TemplateVariable.ACTION, TemplateVariable.FACING, TemplateVariable.FRAME)
    val segments = output.source.split('/').map { it.substringBeforeLast('.').replace(PROFILE_PLACEHOLDER, "") }
    val parts = segments.filter { segment -> wanted.any { "{${it.key}" in segment } }
    val joined = parts.joinToString("_")
    val usable = runCatching { Template(joined).variables == wanted }.getOrDefault(false)
    return if (usable) joined else DEFAULT_ATLAS_PATTERN
}

private val PROFILE_PLACEHOLDER = Regex("\\{profile(:[^}]*)?}")

/**
 * A libGDX `.atlas` file (its page PNGs beside it) as animations. Each region is matched against the
 * name [pattern] for every action and facing of the project; the matches of one action and facing,
 * ordered by frame number, are one row. Regions that match nothing are skipped and listed in
 * [skipped]. An atlas carries no durations, so each action has the artist's duration and loop flag.
 * A region smaller than the canvas is placed on it by [ModelCanvas]; a larger one stops the import,
 * naming both sizes.
 *
 * @param file the `.atlas` file
 * @param canvas the project canvas
 * @param actions the project's actions
 * @param facings the project's facings
 * @param pattern a [Template] over `{action}`, `{facing}` and `{frame}`, all three required
 * @param durationMs each action's frame duration; an action not in the map gets [DEFAULT_ATLAS_DURATION_MS]
 * @param loops the actions that loop; any other does not
 */
class AtlasModelSource(
    private val file: Path,
    private val canvas: Canvas,
    private val actions: List<String>,
    private val facings: List<String>,
    private val pattern: Template,
    private val durationMs: Map<String, Int> = emptyMap(),
    private val loops: Set<String> = emptySet(),
) : ModelSource {

    private class Match(val page: AtlasPage, val region: AtlasRegion, val frame: Int)

    private class Group(val row: ModelRow, val action: String, val matches: List<Match>)

    init {
        require(pattern.variables == PATTERN_VARIABLES) {
            "Name pattern '$pattern' must use {action}, {facing} and {frame}, and nothing else"
        }
        durationMs.forEach { (action, ms) -> require(ms >= 1) { "A frame of $action lasts at least 1 ms, got $ms" } }
    }

    private val pages: List<AtlasPage> by lazy {
        val text = try {
            file.toFile().readText(Charsets.UTF_8)
        } catch (e: IOException) {
            throw ModelException("$file: ${e.message ?: e.javaClass.simpleName}", e)
        }
        AtlasParser.parse(text)
    }

    private val analysis: Pair<List<Group>, List<String>> by lazy { analyse() }

    private fun analyse(): Pair<List<Group>, List<String>> {
        val matchers = actions.flatMap { action ->
            facings.map { facing -> Triple(action, facing, pattern.frameMatcher(action, facing)) }
        }
        val byKey = LinkedHashMap<Pair<String, String>, MutableList<Match>>()
        val skipped = ArrayList<String>()
        for (page in pages) {
            for (region in page.regions) {
                val hit = matchers.firstNotNullOfOrNull { (action, facing, regex) ->
                    val frame = regex.matchEntire(region.name)?.groupValues?.get(1)?.toIntOrNull()
                    frame?.let { Triple(action, facing, it) }
                }
                if (hit == null) {
                    skipped += region.name
                    continue
                }
                ModelCanvas.requireFits(regionName(region), region.origWidth, region.origHeight, canvas)
                byKey.getOrPut(hit.first to hit.second) { ArrayList() } += Match(page, region, hit.third)
            }
        }
        val order = compareBy<Pair<String, String>>({ actions.indexOf(it.first) }, { facings.indexOf(it.second) })
        val groups = byKey.toSortedMap(order).map { (key, found) ->
            val ordered = found.sortedBy { it.frame }
            ordered.zipWithNext().firstOrNull { (a, b) -> a.frame == b.frame }?.let { (a, b) ->
                throw ModelException("Atlas regions \"${a.region.name}\" and \"${b.region.name}\" are the same frame")
            }
            val (action, facing) = key
            Group(ModelRow("${action}_$facing", action, facing, ordered.size), action, ordered)
        }
        return groups to skipped
    }

    /** The names of the regions that matched no action and facing, in file order. */
    val skipped: List<String> get() = analysis.second

    /** One row per action and facing that has regions, in the project's action then facing order. */
    override fun rows(): List<ModelRow> = analysis.first.map { it.row }

    /** The row's regions in frame order, each rebuilt at its original size and placed on the canvas. */
    override fun frames(row: ModelRow): List<Raster> {
        val pageRasters = HashMap<String, Raster>()
        return group(row).matches.map { match ->
            val page = pageRasters.getOrPut(match.page.name) {
                val png = file.resolveSibling(match.page.name)
                try {
                    PngCodec.read(png)
                } catch (e: IOException) {
                    throw ModelException("$png: ${e.message ?: e.javaClass.simpleName}", e)
                }
            }
            ModelCanvas.place(regionName(match.region), AtlasParser.rebuild(match.region, page), canvas)
        }
    }

    private fun regionName(region: AtlasRegion) = "Atlas region \"${region.name}\""

    /** The action's duration for every frame. */
    override fun durations(row: ModelRow): List<Int> {
        val group = group(row)
        return List(group.row.frameCount) { durationMs[group.action] ?: DEFAULT_ATLAS_DURATION_MS }
    }

    /** Whether the row's action is in the looping set. */
    override fun loop(row: ModelRow): Boolean = group(row).action in loops

    private fun group(row: ModelRow): Group =
        analysis.first.firstOrNull { it.row.id == row.id } ?: throw ModelException("The atlas has no row ${row.id}")
}

private val PATTERN_VARIABLES = setOf(TemplateVariable.ACTION, TemplateVariable.FACING, TemplateVariable.FRAME)
