package com.roto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roto.core.cache.StageCache
import com.roto.core.cycle.KeyFrames
import com.roto.core.draft.MAX_HOLD
import com.roto.core.framebox.FrameBoxes
import com.roto.core.framebox.ScaleMarks
import com.roto.core.framebox.ScaleSettings
import com.roto.core.framebox.proposedBodyHeight
import com.roto.core.image.Mask
import com.roto.core.image.Raster
import com.roto.core.mask.BrushPoint
import com.roto.core.model.ModelColours
import com.roto.core.model.ModelException
import com.roto.core.model.ModelMeasure
import com.roto.core.model.ModelRepository
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
import kotlin.math.abs

/** What a press on the picture does. */
sealed interface DrawTool {
    /** Marks the top of the head. */
    data object Head : DrawTool

    /** Marks the soles of the feet. */
    data object Feet : DrawTool

    /**
     * Adds a sample to [zone].
     *
     * @property zone the name of the zone sampled
     */
    data class Sample(val zone: String) : DrawTool

    /**
     * Starts a zone called [name], in [ramp], from the first sample.
     *
     * @property name the new zone's name
     * @property ramp the ramp it is shaded with
     */
    data class NewZone(val name: String, val ramp: String) : DrawTool

    /**
     * Paints figure into [zone] on a key frame.
     *
     * @property zone the name of the zone painted
     */
    data class Paint(val zone: String) : DrawTool
}

/**
 * What the draw screen shows: the [clip] and the profile's [scale] as edited, the [head] and [feet]
 * marked on this clip, the [frame] being looked at of [frameCount] with its [picture], the [strip], the
 * clip's [keyFrames] and the project's [ramps], the [boxes] and [drafts] once drawn, the [tool] in hand
 * with its brush and any [stroke] in progress, whether a draft is [drawing], and a [message].
 *
 * @property clip the clip being drawn, with the artist's edits
 * @property scale the profile's scale, or null until marked
 * @property head the marked head-top row
 * @property feet the marked feet row
 * @property frame the frame being looked at
 * @property frameCount how many frames the clip has
 * @property picture the frame being looked at, once decoded
 * @property strip thumbnails across the clip
 * @property keyFrames the clip's key frames
 * @property ramps the names of the project's ramps
 * @property models the keys of the profile's models for the clip's action
 * @property usage the ramps and steps the clip's pose guide model uses, once read
 * @property groundRow the feet row of the model matched for size, which the draft shows as the ground line
 * @property boxes where the canvas sits on each frame, once drawn
 * @property drafts a draft per key frame, painted in palette colours
 * @property tool what a press on the picture does
 * @property brushRadius the zone brush radius in working-frame pixels
 * @property stroke the stroke in progress
 * @property drawing whether a draft is being drawn
 * @property message the last thing that went wrong
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
    val models: List<String> = emptyList(),
    val usage: ModelColours? = null,
    val groundRow: Int? = null,
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

    /** The names of the ramps the pose guide model uses, which a zone's ramp picker marks. */
    val usedRamps: Set<String> get() = usage?.ramps?.map { it.ramp }?.toSet().orEmpty()

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
    private val target: ClipTarget,
    private val project: Project,
) {
    private val folder = target.repository.folder(target.profileId)
    private val cache = StageCache(folder.cacheFolder)
    private val footage = ClipFootage(target.ffmpeg, cache, folder, clip)
    private val drafting = Mutex()
    private var palette: ProjectPalette? = null
    private var masks: List<Mask> = emptyList()

    /** What the screen shows. */
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
        showUsage()
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

    /** Shows frame [frame] of the clip. */
    fun selectFrame(frame: Int) {
        val frames = footage.frames.get()
        val index = frame.coerceIn(0, (frames.size - 1).coerceAtLeast(0))
        state = state.copy(frame = index, picture = frames.getOrNull(index), stroke = null)
    }

    /** Puts [tool] in hand, or none. */
    fun useTool(tool: DrawTool?) {
        state = state.copy(tool = tool, message = null)
    }

    /** Sets the figure's height on the canvas, in canvas pixels. */
    fun setBodyHeight(height: Int) = changeScale { it.copy(bodyHeight = height) }

    /**
     * Lists the profile's models for the clip's action, for "Match size of". A model that cannot be
     * read is left out, so the offer never shows one.
     */
    suspend fun loadModels() {
        val action = state.clip.action
        val models = working { ModelRepository(folder).keysFor(action) } ?: return
        state = state.copy(models = models)
    }

    /**
     * Sets the body height from the height measured on the model called [key], clamped to the canvas,
     * and shows the model's feet row as the ground line. Squash and marks stay as they are. A model that
     * fails, or holds no figure, changes nothing and says why.
     */
    suspend fun matchSize(key: String) {
        if (state.scale == null) {
            state = state.copy(message = "Mark the head and the feet first")
            return
        }
        val measured = working {
            try {
                Result.success(ModelMeasure.measure(ModelRepository(folder).load(key).frames.map { it.raster }))
            } catch (e: ModelException) {
                Result.failure(e)
            }
        } ?: return
        val measurement = measured.getOrElse {
            state = state.copy(models = state.models - key, message = it.message)
            return
        }
        val height = measurement.bodyHeight
        val feet = measurement.feetRow
        if (height == null || feet == null) {
            state = state.copy(message = "Model $key holds no figure to measure")
            return
        }
        setBodyHeight(height.coerceIn(1, tallestBody))
        state = state.copy(groundRow = feet, message = null)
    }

    /**
     * Sets the model whose frames the handoff shows as its pose guide, or none for `null`, and shows
     * the ramps and steps it uses. No zone is made or changed.
     */
    suspend fun chooseModel(key: String?) {
        edit { it.copy(model = key) }
        showUsage()
    }

    /** Reads the colour usage of the clip's model; a model that cannot be read is left out and says why. */
    private suspend fun showUsage() {
        val key = state.clip.model
        val rampSet = palette?.ramps
        if (key == null || rampSet == null) {
            state = state.copy(usage = null)
            return
        }
        val read = working {
            try {
                val frames = ModelRepository(folder).load(key).frames.map { it.raster }
                Result.success(ModelMeasure.colours(frames, rampSet))
            } catch (e: ModelException) {
                Result.failure(e)
            }
        } ?: return
        state = read.fold(
            { state.copy(usage = it) },
            { state.copy(usage = null, models = state.models - key, message = it.message) },
        )
    }

    /** Sets the vertical squash of the figure. */
    fun setSquash(squash: Double) = changeScale { it.copy(squash = squash) }

    /** Sets whether the clip's figure is kept one size as it walks toward or away from the camera. */
    fun setFollowDepth(follow: Boolean) = edit { it.copy(depth = it.depth.copy(follow = follow)) }

    /** Sets the zone brush radius in working-frame pixels. */
    fun setBrushRadius(radius: Int) {
        state = state.copy(brushRadius = radius)
    }

    /** Sets whether the draft is dithered. */
    fun setDither(dither: Boolean) = edit { it.copy(draft = it.draft.copy(dither = dither)) }

    /** Sets how many extra samples a key frame's draft holds to the previous one, 0 to [MAX_HOLD]. */
    fun setHold(hold: Int) = edit { it.copy(draft = it.draft.copy(hold = hold)) }

    /** Shades [zone] with [ramp]. */
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
