package com.roto.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roto.core.ase.AseCodec
import com.roto.core.palette.HexColor
import com.roto.core.palette.ProjectPalette
import com.roto.core.profile.Profile
import com.roto.core.profile.ProfileRepository
import com.roto.core.review.Flipbook
import com.roto.core.review.FlipbookFrame
import com.roto.core.template.SafeName
import com.roto.core.variant.RampSwap
import com.roto.core.variant.RampSwapPair
import com.roto.core.variant.VariantOf
import java.io.IOException
import java.nio.file.Files

/**
 * What the variant screen shows: the [base] chosen, the [name] of the new profile, the [swaps] and
 * [outline] chosen, and a [message] about the last thing that went wrong.
 *
 * @property base the id of the profile to recolour
 * @property name the id of the variant to create
 * @property swaps the ramp swaps, in the order added
 * @property outline the palette colour the outline becomes, or null to keep it
 * @property message the last thing that went wrong
 */
data class VariantState(
    val base: String? = null,
    val name: String = "",
    val swaps: List<RampSwapPair> = emptyList(),
    val outline: Int? = null,
    val message: String? = null,
)

/**
 * The variant screen's state and what can be done to it: choose a base profile and the ramp swaps, see a
 * clip of the base recoloured, and create the variant profile, which has no clips of its own. [problem]
 * names what still stops the creation; every rule is `roto-core`'s ([VariantOf], [RampSwap],
 * [ProfileRepository]).
 *
 * @property colours the project's palette and ramps
 */
class VariantModel(private val repository: ProfileRepository, val colours: ProjectPalette) {
    /** The ids of the profiles that can be recoloured: readable, with clips, and not variants themselves. */
    val bases: List<String> = repository.ids().filter { id ->
        runCatching { repository.load(id) }.getOrNull()?.let { it.variant == null && it.clips.isNotEmpty() } == true
    }

    /** The names of the project's ramps, to swap from and to. */
    val rampNames: List<String> get() = colours.ramps.ramps.map { it.name }

    /** The palette colours the outline may become. */
    val outlineChoices: List<Int> get() = colours.palette.colors

    /** The player of the base's first rendered clip with the swaps applied. */
    val player = FlipbookModel()

    /** What the screen shows. */
    var state by mutableStateOf(VariantState())
        private set

    /** Makes profile [id] the base. */
    fun chooseBase(id: String) {
        state = state.copy(base = id, message = null)
    }

    /** Sets the name of the variant to [name]. */
    fun rename(name: String) {
        state = state.copy(name = name, message = null)
    }

    /** Adds the swap of ramp [from] for ramp [to]; [problem] says whether the list is acceptable. */
    fun addSwap(from: String, to: String) {
        state = state.copy(swaps = state.swaps + RampSwapPair(from, to), message = null)
    }

    /** Removes the swap at [index]. */
    fun removeSwap(index: Int) {
        state = state.copy(swaps = state.swaps.filterIndexed { i, _ -> i != index }, message = null)
    }

    /** Makes [colour], a palette colour or null to keep the outline, the colour the outline becomes. */
    fun chooseOutline(colour: Int?) {
        state = state.copy(outline = colour, message = null)
    }

    /** What still stops the creation, or null when every gate holds: base, swaps, ramps and name. */
    val problem: String?
        get() {
            val name = state.name.trim()
            return when {
                state.base == null -> "Choose the profile to recolour."
                state.swaps.isEmpty() -> "Add at least one ramp swap."
                else -> swapsProblem() ?: when {
                    !SafeName.isValid(name) -> "Name the variant: use letters, digits, '-' and '_'."
                    repository.ids().any { it.equals(name, ignoreCase = true) } ->
                        "A profile called $name already exists."
                    else -> null
                }
            }
        }

    /** Whether the variant can be created. */
    val canCreate: Boolean get() = problem == null

    private fun swapsProblem(): String? {
        state.swaps.groupBy { it.from }.values.firstOrNull { it.size > 1 }?.let {
            return "Ramp '${it.first().from}' is swapped twice."
        }
        return state.swaps.flatMap { listOf(it.from, it.to) }.firstOrNull { it !in rampNames }
            ?.let { "Ramp '$it' is not in ramps.json." }
    }

    /**
     * Creates the variant profile and returns its id; null, with a message, when a gate fails or the
     * profile cannot be written.
     */
    fun create(): String? {
        val base = state.base
        val refusal = problem
        if (refusal != null || base == null) {
            state = state.copy(message = refusal)
            return null
        }
        val name = state.name.trim()
        return try {
            val variant = VariantOf(base, state.swaps, state.outline)
            variant.check(colours, repository.load(base))
            repository.create(name)
            repository.save(name, Profile(variant = variant))
            name
        } catch (e: IOException) {
            state = state.copy(message = "The variant could not be created: ${e.message}")
            null
        } catch (e: IllegalArgumentException) {
            state = state.copy(message = "The variant could not be created: ${e.message}")
            null
        }
    }

    /**
     * Reads the base's first rendered clip again and shows it with the swaps applied. A base with no
     * rendered clip, or swaps that are not acceptable yet, turn the player off with the reason.
     */
    suspend fun refreshPreview() {
        val base = state.base
        val swaps = state.swaps
        val outline = state.outline
        player.open {
            require(base != null) { "Choose the profile to recolour." }
            val folder = repository.folder(base)
            val clip = repository.load(base).clips.firstOrNull { Files.isRegularFile(folder.handoffFile(it)) }
            requireNotNull(clip) { "'$base' has no rendered handoff to preview." }
            swapsProblem()?.let { throw IllegalArgumentException(it) }
            val book = Flipbook.of(AseCodec.read(folder.handoffFile(clip)), clip.id, clip.loop)
            val swap = RampSwap(colours.ramps, swaps, outline)
            Flipbook(book.frames.map { FlipbookFrame(swap.apply(it.image), it.durationMs, it.source) }, book.loop)
        }
    }

    /** Names for the Outline choice. */
    companion object {
        /** The Outline choice that keeps the base's outline. */
        const val KEEP_OUTLINE = "Keep"

        /** The colour [argb] as the Outline choice names it. */
        fun outlineName(argb: Int?): String = argb?.let(HexColor::format) ?: KEEP_OUTLINE
    }
}
