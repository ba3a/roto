package com.roto.core.preset

import com.roto.core.palette.PaletteCodec
import com.roto.core.palette.RampSetCodec
import com.roto.core.template.SafeName
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private const val PALETTE_FILE = "palette.hex"
private const val TEMPORARY_PREFIX = ".adding-"

/** One preset the [PresetLibrary] lists: [id] is its folder name, or its built-in id. */
sealed interface LibraryEntry {
    /** The id the preset is known by. */
    val id: String

    /** Whether the preset ships with Roto. */
    val builtIn: Boolean
}

/**
 * A preset that loaded.
 *
 * @property id the preset's id
 * @property builtIn whether it ships with Roto
 * @property preset the preset
 */
data class LibraryPreset(override val id: String, override val builtIn: Boolean, val preset: ProjectPreset) :
    LibraryEntry

/**
 * A user preset that could not be loaded; listing it keeps one damaged folder from hiding the rest.
 *
 * @property id the folder's name
 * @property message why it failed
 */
data class BrokenPreset(override val id: String, val message: String) : LibraryEntry {
    override val builtIn: Boolean get() = false
}

/**
 * The presets a project can start from: the [builtIn] ones, then the artist's own in [userFolder]
 * (`<config>/presets`), one `<id>/` folder each holding the three bundle files. Listing is sorted by id
 * within each group, so it is deterministic. Adding is all-or-nothing: the files are written to a
 * temporary folder that is then moved into place.
 *
 * @property builtIn the built-in presets by id
 * @property userFolder the folder the artist's presets live in; it need not exist yet
 */
class PresetLibrary(private val builtIn: Map<String, ProjectPreset>, private val userFolder: Path) {

    /** Built-in presets, then user presets, each group sorted by id. A damaged user preset is a [BrokenPreset]. */
    fun list(): List<LibraryEntry> {
        val shipped = builtIn.toSortedMap().map { (id, preset) -> LibraryPreset(id, true, preset) }
        return shipped + userIds().map(::loadUser)
    }

    /**
     * Adds [preset] as a user preset and returns its id: the name with every run of characters
     * [SafeName] does not allow turned into `-`. Throws [PresetException], adding nothing, when no
     * id can be made of the name, when the id is already a built-in or user preset (case aside), or
     * when writing fails.
     */
    fun add(preset: ProjectPreset): String {
        val id = idOf(preset.name)
        if (list().any { it.id.equals(id, ignoreCase = true) }) throw PresetException("A preset '$id' already exists")
        try {
            Files.createDirectories(userFolder)
            val temporary = Files.createTempDirectory(userFolder, TEMPORARY_PREFIX)
            try {
                write(preset, temporary)
                Files.move(temporary, userFolder.resolve(id), StandardCopyOption.ATOMIC_MOVE)
            } finally {
                deleteTree(temporary)
            }
        } catch (e: IOException) {
            throw PresetException("Could not add preset '$id': ${e.message}", e)
        }
        return id
    }

    /** Reads the bundle at [bundle] with [PresetBundle.read] and adds it as [add] does; a bad bundle adds nothing. */
    fun add(bundle: Path): String = add(PresetBundle.read(bundle))

    /** Removes the user preset [id]. A built-in or unknown id is a [PresetException]; nothing else is touched. */
    fun remove(id: String) {
        if (id !in userIds()) {
            val reason = if (id in builtIn) "is built in and cannot be removed" else "is not a user preset"
            throw PresetException("Preset '$id' $reason")
        }
        try {
            deleteTree(userFolder.resolve(id))
        } catch (e: IOException) {
            throw PresetException("Could not remove preset '$id': ${e.message}", e)
        }
    }

    private fun userIds(): List<String> {
        if (!Files.isDirectory(userFolder)) return emptyList()
        return Files.list(userFolder).use { folders ->
            folders.filter { Files.isDirectory(it) }.map { it.fileName.toString() }
                .filter { SafeName.isValid(it) }.toList()
        }.sorted()
    }

    private fun loadUser(id: String): LibraryEntry {
        val folder = userFolder.resolve(id)
        return try {
            val info = PresetCodec.read(folder.resolve(PresetCodec.fileName))
            val palette = PaletteCodec.read(folder.resolve(PALETTE_FILE))
            val ramps = RampSetCodec.read(folder.resolve(RampSetCodec.fileName))
            LibraryPreset(id, false, ProjectPreset.of(info, palette, ramps))
        } catch (e: IOException) {
            BrokenPreset(id, e.message.orEmpty())
        } catch (e: IllegalArgumentException) {
            BrokenPreset(id, e.message.orEmpty())
        }
    }

    private fun write(preset: ProjectPreset, folder: Path) {
        PresetCodec.write(preset.info, folder.resolve(PresetCodec.fileName))
        PaletteCodec.write(preset.palette, folder.resolve(PALETTE_FILE))
        RampSetCodec.write(preset.ramps, folder.resolve(RampSetCodec.fileName))
    }

    private fun idOf(name: String): String {
        val id = name.trim().replace(Regex("[^A-Za-z0-9_-]+"), "-").trim('-', '_')
        if (!SafeName.isValid(id)) throw PresetException("Preset name '$name' gives no usable id")
        return id
    }

    private fun deleteTree(folder: Path) {
        if (!Files.exists(folder)) return
        Files.walk(folder).use { tree -> tree.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
    }
}
