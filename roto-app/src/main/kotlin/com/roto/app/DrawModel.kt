package com.roto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roto.core.cache.StageCache
import com.roto.core.cycle.KeyFrames
import com.roto.core.draft.DraftSettings
import com.roto.core.framebox.FrameBoxes
import com.roto.core.framebox.ScaleMarks
import com.roto.core.framebox.ScaleSettings
import com.roto.core.framebox.proposedBodyHeight
import com.roto.core.image.Mask
import com.roto.core.image.Raster
import com.roto.core.mask.BrushPoint
import com.roto.core.palette.ProjectPalette
import com.roto.core.profile.Clip
import com.roto.core.project.Project
import com.roto.core.render.ClipFootage
import com.roto.core.render.ClipMasking
import com.roto.core.render.ClipPipeline
import com.roto.core.render.Drawing
import com.roto.core.timeline.FilmstripBuilder
import com.roto.core.zone.Zone
import com.roto.core.zone.ZoneSample
import com.roto.core.zone.ZoneStroke
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path
import kotlin.math.abs

/** What a press on the picture does. */
sealed interface DrawTool {
    /** Marks the top of the head. */
    data object Head : DrawTool

    /** Marks the soles of the feet. */
    data object Feet : DrawTool

    /** Adds a sample to [zone]. */
    data class Sample(val zone: String) : DrawTool

    /** Starts a zone called [name], in [ramp], from the first sample. */
    data class NewZone(val name: String, val ramp: String) : DrawTool

    /** Paints figure into [zone] on a key frame. */
    data class Paint(val zone: String) : DrawTool
}

/**
 * What the draw screen shows: the [clip] and the profile's [scale] as edited, the [head] and [feet]
 * marked on this clip, the [frame] being looked at of [frameCount] with its [picture], the [strip], the
 * clip's [keyFrames] and the project's [ramps], the [boxes] and [drafts] once drawn, the [tool] in hand
 * with its brush and any [stroke] in progress, whether a draft is [drawing], and a [message].
 */
data class DrawState(
    val clip: Clip,
    val scale: ScaleSettings?,
    val head: Int? = null,
    val feet: Int? = null,
    val frame: Int = 0,
    val frameCount: Int = 0,
    val picture: Raster? = null,
    val strip: List<Raster>? = null,
    val keyFrames: KeyFrames? = null,
    val ramps: List<String> = emptyList(),
    val boxes: FrameBoxes? = null,
    val drafts: List<Raster> = emptyList(),
    val tool: DrawTool? = null,
    val brushRadius: Int = 6,
    val stroke: ZoneStroke? = null,
    val drawing: Boolean = false,
    val message: String? = null,
) {
    /** The draft of the key frame showing at [frame]. */
    val draft: Raster? get() = keyFrames?.keyFrameAt(frame)?.let { drafts.getOrNull(it) }

    /** Whether [frame] is a key frame, the only frames a zone brush can reach. */
    val onKeyFrame: Boolean get() = keyFrames?.frames?.contains(frame) == true

    /** The zone strokes already on the frame being looked at. */
    val frameFixes: List<ZoneStroke> get() = clip.zoning.fixes.filter { it.frame == frame }

    /** What is still missing before a draft can be drawn. */
    val missing: String?
        get() = when {
            scale == null -> "Mark the head and the feet on a standing frame."
            clip.zoning.zones.isEmpty() -> "Add a zone, then click the figure to sample it."
            else -> null
        }
}

/**
 * The draw screen's state and what can be done to it: mark the profile's scale, sample zones, paint
 * over them on key frames, and see the draft follow. A draft takes the stages over the whole clip, so
 * it runs behind the screen and is cached as usual. Every rule is `roto-core`'s.
 */
class DrawModel(
    clip: Clip,
    scale: ScaleSettings?,
    source: Path,
    private val target: ClipTarget,
    private val project: Project,
) {
    private val cache = StageCache(target.repository.folder(target.profileId).cacheFolder)
    private val footage = ClipFootage(target.ffmpeg, cache, source, clip.trim)
    private val drafting = Mutex()
    private var palette: ProjectPalette? = null
    private var masks: List<Mask> = emptyList()

    var state by mutableStateOf(initialState(clip, scale))
        private set

    /** What the filmstrip drags. */
    val strip = Strip()

    /** What a press on the picture does, by the [DrawState.tool] in hand. */
    val picture = Picture()

    /** The tallest a figure may be drawn: the whole canvas. */
    val tallestBody: Int get() = project.config.canvas.height

    /** Reads the clip, its masks and key frames, and the project's ramps. */
    suspend fun open() {
        val opened = working {
            val masking = ClipMasking(footage, cache, state.clip)
            val keyFrames = masking.keyFrames
            palette = ProjectPalette.load(project)
            masks = masking.fixedMasks.get()
            keyFrames to footage.frames.get()
        } ?: return
        val (keyFrames, frames) = opened
        val thumbnails = FilmstripBuilder.centres(frames.size.toLong(), minOf(FilmstripBuilder.FRAMES, frames.size))
            .map { frames[it.toInt()] }
        state = state.copy(
            frameCount = frames.size,
            strip = thumbnails,
            keyFrames = keyFrames,
            ramps = palette?.ramps?.ramps.orEmpty().map { it.name },
        )
        selectFrame(state.head?.let { state.scale?.marks?.frame } ?: keyFrames.frames.first())
    }

    /** Draws what can be drawn yet: the frame boxes once there is a scale, the drafts once there are zones too. */
    suspend fun refreshDraft() {
        val scale = state.scale ?: return
        val clip = state.clip
        val ready = clip.zoning.zones.isNotEmpty()
        val projectPalette = palette ?: return
        state = state.copy(drawing = true)
        val drawn = drafting.withLock {
            working {
                val drawing = Drawing(project.config.canvas, projectPalette, scale)
                val pipeline = ClipPipeline(ClipMasking(footage, cache, clip), cache, drawing)
                val drafts = if (ready) pipeline.drafts.get().map { projectPalette.palette.paint(it) } else emptyList()
                pipeline.boxes.get() to drafts
            }
        }
        state = state.copy(drawing = false)
        if (drawn != null) state = state.copy(boxes = drawn.first, drafts = drawn.second)
    }

    /** Writes the clip and the profile's scale into `profile.json`, with every ramp the zones use. */
    suspend fun save() {
        val clip = state.clip
        val scale = state.scale
        working {
            val repository = target.repository
            val profile = repository.load(target.profileId)
            val ramps = (profile.ramps + clip.zoning.zones.map { it.ramp }).distinct()
            repository.save(target.profileId, profile.copy(ramps = ramps, scale = scale).withClip(clip))
        }
    }

    fun selectFrame(frame: Int) {
        val frames = footage.frames.get()
        val index = frame.coerceIn(0, (frames.size - 1).coerceAtLeast(0))
        state = state.copy(frame = index, picture = frames.getOrNull(index), stroke = null)
    }

    fun useTool(tool: DrawTool?) {
        state = state.copy(tool = tool, message = null)
    }

    fun setBodyHeight(height: Int) = changeScale { it.copy(bodyHeight = height) }

    fun setSquash(squash: Double) = changeScale { it.copy(squash = squash) }

    fun setBrushRadius(radius: Int) {
        state = state.copy(brushRadius = radius)
    }

    fun setDither(dither: Boolean) = edit { it.copy(draft = DraftSettings(dither)) }

    fun chooseRamp(zone: String, ramp: String) = editZones { zones ->
        zones.map { if (it.name == zone) it.copy(ramp = ramp) else it }
    }

    /** Takes back [zone]'s last sample, and the zone with it if that was its only one. */
    fun removeSample(zone: String) {
        val samples = state.clip.zoning.zones.firstOrNull { it.name == zone }?.samples ?: return
        if (samples.size == 1) {
            removeZone(zone)
        } else {
            editZones { zones -> zones.map { if (it.name == zone) it.copy(samples = samples.dropLast(1)) else it } }
        }
    }

    /** Removes [zone], the strokes painted into it going too. */
    fun removeZone(zone: String) {
        val tool = state.tool
        val inHand = tool == DrawTool.Sample(zone) || tool == DrawTool.Paint(zone)
        edit { clip ->
            val zoning = clip.zoning
            clip.copy(
                zoning = zoning.copy(
                    zones = zoning.zones.filter { it.name != zone },
                    fixes = zoning.fixes.filter { it.zone != zone },
                ),
            )
        }
        if (inHand) state = state.copy(tool = null)
    }

    /** Takes back the last zone stroke on the frame being looked at. */
    fun undoStroke() {
        val last = state.frameFixes.lastOrNull() ?: return
        val index = state.clip.zoning.fixes.lastIndexOf(last)
        editFixes { fixes -> fixes.filterIndexed { position, _ -> position != index } }
    }

    /** The filmstrip: the playhead, drawn to a key frame when it comes near one. */
    inner class Strip : StripGestures {
        private var reach = 0L

        override fun press(at: Long, reach: Long) {
            this.reach = reach
            drag(at)
        }

        override fun drag(at: Long) {
            val nearest = state.keyFrames?.frames?.minByOrNull { abs(it - at) }
            selectFrame(nearest?.takeIf { abs(it - at) <= reach } ?: at.toInt())
        }
    }

    /** The picture, under the [DrawState.tool] in hand. */
    inner class Picture : PictureGestures {

        override fun press(x: Int, y: Int) {
            when (val tool = state.tool) {
                DrawTool.Head -> mark(head = y.coerceAtLeast(0), feet = state.feet)
                DrawTool.Feet -> mark(head = state.head, feet = y.coerceAtLeast(0))
                is DrawTool.Sample -> addSample(tool.zone, x, y)
                is DrawTool.NewZone -> startZone(tool, x, y)
                is DrawTool.Paint -> startStroke(tool.zone, x, y)
                null -> Unit
            }
        }

        override fun extend(x: Int, y: Int) {
            val stroke = state.stroke ?: return
            state = state.copy(stroke = stroke.copy(points = stroke.points + point(x, y)))
        }

        override fun release() {
            val stroke = state.stroke ?: return
            editFixes { it + stroke }
            state = state.copy(stroke = null)
        }
    }

    private fun mark(head: Int?, feet: Int?) {
        if (head != null && feet != null && head >= feet) {
            state = state.copy(message = "The head has to be above the feet")
            return
        }
        state = state.copy(head = head, feet = feet, message = null)
        if (head == null || feet == null) return
        val marks = ScaleMarks(state.clip.id, state.frame, head, feet)
        val first = { ScaleSettings(proposedBodyHeight(project.config.canvas), marks) }
        state = state.copy(scale = state.scale?.copy(marks = marks) ?: first())
    }

    private fun addSample(zone: String, x: Int, y: Int) {
        val sample = sample(x, y) ?: return
        editZones { zones -> zones.map { if (it.name == zone) it.copy(samples = it.samples + sample) else it } }
    }

    private fun startZone(tool: DrawTool.NewZone, x: Int, y: Int) {
        if (state.clip.zoning.zones.any { it.name == tool.name }) {
            state = state.copy(message = "There is already a zone called ${tool.name}")
            return
        }
        val sample = sample(x, y) ?: return
        editZones { it + Zone(tool.name, tool.ramp, listOf(sample)) }
        state = state.copy(tool = DrawTool.Sample(tool.name))
    }

    /** A sample at ([x], [y]) of the frame being looked at, if that is figure: a sample of the wall means nothing. */
    private fun sample(x: Int, y: Int): ZoneSample? {
        val mask = masks.getOrNull(state.frame)
        if (mask == null || !mask.contains(x, y) || !mask[x, y]) {
            state = state.copy(message = "That is outside the figure; click on it")
            return null
        }
        state = state.copy(message = null)
        return ZoneSample(state.frame, x, y)
    }

    private fun startStroke(zone: String, x: Int, y: Int) {
        if (!state.onKeyFrame) {
            state = state.copy(message = "Zones are painted on key frames only")
            return
        }
        val stroke = ZoneStroke(state.frame, zone, state.brushRadius, listOf(point(x, y)))
        state = state.copy(stroke = stroke, message = null)
    }

    private fun point(x: Int, y: Int) = BrushPoint(x.coerceAtLeast(0), y.coerceAtLeast(0))

    private fun changeScale(change: (ScaleSettings) -> ScaleSettings) {
        state = state.copy(scale = state.scale?.let(change))
    }

    private fun edit(change: (Clip) -> Clip) {
        state = state.copy(clip = change(state.clip))
    }

    private fun editZones(change: (List<Zone>) -> List<Zone>) =
        edit { it.copy(zoning = it.zoning.copy(zones = change(it.zoning.zones))) }

    private fun editFixes(change: (List<ZoneStroke>) -> List<ZoneStroke>) =
        edit { it.copy(zoning = it.zoning.copy(fixes = change(it.zoning.fixes))) }

    private suspend fun <T : Any> working(work: () -> T): T? =
        offUiThread({ state = state.copy(message = it) }, work)

    private companion object {
        /** The marks already made on [clip], if the profile's scale was marked on it. */
        fun initialState(clip: Clip, scale: ScaleSettings?): DrawState {
            val marks = scale?.marks?.takeIf { it.clip == clip.id }
            return DrawState(clip, scale, head = marks?.headTopY, feet = marks?.feetY)
        }
    }
}
