package com.roto.core.publish

import com.roto.core.ase.AseCodec
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** How far a clip's handoff has come. Only a [finished] clip can be published. */
sealed interface ArtProgress {

    /** Whether every frame holds art, so the clip can be published. */
    val finished: Boolean get() = false

    /** Nothing has been rendered for the clip yet. */
    data object NoHandoff : ArtProgress

    /**
     * The handoff holds art on [painted] of its [total] frames.
     *
     * @property painted how many frames hold art
     * @property total how many frames the handoff has
     */
    data class Painted(val painted: Int, val total: Int) : ArtProgress {
        override val finished: Boolean get() = painted == total
    }

    /**
     * The handoff is there but cannot be read, for this [reason].
     *
     * @property reason why the file cannot be read
     */
    data class Unreadable(val reason: String) : ArtProgress

    /** Reading a clip's progress from its handoff. */
    companion object {

        /** The progress of [clip], whose handoff is [file]. */
        fun of(file: Path, clip: String): ArtProgress {
            if (!Files.exists(file)) return NoHandoff
            return try {
                val sprite = AseCodec.read(file)
                Painted(ArtFrames.paintedCount(sprite, clip), sprite.frames.size)
            } catch (e: IOException) {
                Unreadable(e.message ?: "The handoff of $clip cannot be read")
            } catch (e: IllegalArgumentException) {
                Unreadable(e.message ?: "The handoff of $clip cannot be read")
            }
        }
    }
}
