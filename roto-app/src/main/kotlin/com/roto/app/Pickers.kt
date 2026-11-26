package com.roto.app

import java.awt.Component
import java.io.File
import java.nio.file.Path
import kotlin.io.path.isDirectory
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** The kinds of chooser the app opens; each starts where the artist last chose something of its kind. */
enum class PickerCategory {
    /** A project folder to open. */
    PROJECT,

    /** The folder a new project is made in. */
    NEW_PROJECT,

    /** The library folder. */
    LIBRARY,

    /** The folder published frames go under. */
    OUTPUT,

    /** A folder of model frames. */
    FRAMES,

    /** The editor program. */
    EDITOR,

    /** A video. */
    VIDEO,

    /** A photo. */
    PHOTO,

    /** A model's animation file. */
    MODEL,

    /** A palette file. */
    PALETTE,

    /** A preset bundle to import. */
    PRESET,

    /** Where a preset bundle is saved. */
    PRESET_EXPORT,
}

/** Where each kind of chooser last was, kept across runs. */
interface PickerMemory {
    /** The last path chosen in [category], or null when there is none. */
    fun last(category: PickerCategory): Path?

    /** Remembers [path] as the last one chosen in [category]. */
    fun remember(category: PickerCategory, path: Path)
}

/** Asks the artist for a folder or a file; null when they cancel. */
interface Pickers {
    /** Asks for a folder; starts at the last one chosen for [category], else at [fallback]. */
    fun folder(title: String, category: PickerCategory, fallback: Path? = null): Path?

    /** Asks for a video file. */
    fun video(title: String): Path?

    /** Asks for a photo file. */
    fun photo(title: String): Path?

    /** Asks for a model source file: an `.ase`, `.aseprite` or `.atlas`. */
    fun modelFile(title: String): Path?

    /** Asks for a palette file, a `.gpl` or `.hex`. */
    fun palette(title: String): Path?

    /** Asks for a preset bundle, a `.rotopreset`. */
    fun presetBundle(title: String): Path?

    /** Asks where to save a preset bundle, suggesting [name]; the `.rotopreset` extension is added when missing. */
    fun savePresetBundle(title: String, name: String): Path?

    /** Asks for a program; starts where the last one was chosen, else at [fallback]. */
    fun program(title: String, fallback: Path? = null): Path?
}

/** The system choosers, shown over [parent]; each starts where [memory] says its kind last was. */
class SwingPickers(private val parent: Component?, private val memory: PickerMemory) : Pickers {

    override fun folder(title: String, category: PickerCategory, fallback: Path?): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
            // Open beside the folder and select it, so Open returns it and not a folder inside it.
            val start = (memory.last(category) ?: fallback)?.takeIf { it.isDirectory() }
            val parentFolder = start?.parent
            if (start != null && parentFolder != null) {
                currentDirectory = parentFolder.toFile()
                selectedFile = start.toFile()
            } else if (start != null) {
                currentDirectory = start.toFile()
            }
        }
        return chosen(chooser, category)
    }

    override fun video(title: String): Path? =
        file(
            title,
            PickerCategory.VIDEO,
            FileNameExtensionFilter("Video files", "mov", "mp4", "m4v", "mkv", "webm", "avi"),
        )

    override fun photo(title: String): Path? =
        file(
            title,
            PickerCategory.PHOTO,
            FileNameExtensionFilter("Photo files", "jpg", "jpeg", "png", "heic", "heif", "webp"),
        )

    override fun modelFile(title: String): Path? =
        file(title, PickerCategory.MODEL, FileNameExtensionFilter("Animation files", "ase", "aseprite", "atlas"))

    override fun palette(title: String): Path? =
        file(title, PickerCategory.PALETTE, FileNameExtensionFilter("Palette files", "gpl", "hex"))

    override fun presetBundle(title: String): Path? =
        file(title, PickerCategory.PRESET, FileNameExtensionFilter("Preset bundles", "rotopreset"))

    override fun savePresetBundle(title: String, name: String): Path? {
        val category = PickerCategory.PRESET_EXPORT
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter("Preset bundles", "rotopreset")
            val folder = startFolder(category)
            if (folder != null) currentDirectory = folder.toFile()
            selectedFile = if (folder != null) folder.resolve(name).toFile() else File(name)
        }
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return null
        val file = chooser.selectedFile
        val path = (if (file.name.endsWith(".rotopreset")) file else File(file.path + ".rotopreset")).toPath()
        memory.remember(category, path)
        return path
    }

    override fun program(title: String, fallback: Path?): Path? {
        val category = PickerCategory.EDITOR
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.FILES_ONLY
            val start = startFolder(category) ?: fallback?.parent?.takeIf { it.isDirectory() }
            if (start != null) currentDirectory = start.toFile()
        }
        return chosen(chooser, category)
    }

    private fun file(title: String, category: PickerCategory, filter: FileNameExtensionFilter): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = filter
            startFolder(category)?.let { currentDirectory = it.toFile() }
        }
        return chosen(chooser, category)
    }

    /** The folder holding the last file chosen in [category], when it is still there. */
    private fun startFolder(category: PickerCategory): Path? =
        memory.last(category)?.parent?.takeIf { it.isDirectory() }

    private fun chosen(chooser: JFileChooser, category: PickerCategory): Path? {
        val approved = chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
        if (!approved) return null
        return chooser.selectedFile.toPath().also { memory.remember(category, it) }
    }
}
