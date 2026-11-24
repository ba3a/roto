package com.roto.app

import java.awt.Component
import java.io.File
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** Asks the artist for a folder or a file; null when they cancel. */
interface Pickers {
    /** Asks for a folder, starting at [start]. */
    fun folder(title: String, start: Path?): Path?

    /** Asks for a video file. */
    fun video(title: String): Path?

    /** Asks for a photo file. */
    fun photo(title: String): Path?

    /** Asks for a palette file, a `.gpl` or `.hex`. */
    fun palette(title: String): Path?

    /** Asks for a preset bundle, a `.rotopreset`. */
    fun presetBundle(title: String): Path?

    /** Asks where to save a preset bundle, suggesting [name]; the `.rotopreset` extension is added when missing. */
    fun savePresetBundle(title: String, name: String): Path?

    /** Asks for a program, starting at [start]. */
    fun program(title: String, start: Path?): Path?
}

/** The system choosers, shown over [parent]. */
class SwingPickers(private val parent: Component?) : Pickers {

    override fun folder(title: String, start: Path?): Path? {
        val chooser = JFileChooser(start?.toFile()).apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        return chosen(chooser)
    }

    override fun video(title: String): Path? =
        file(title, FileNameExtensionFilter("Video files", "mov", "mp4", "m4v", "mkv", "webm", "avi"))

    override fun photo(title: String): Path? =
        file(title, FileNameExtensionFilter("Photo files", "jpg", "jpeg", "png", "heic", "heif", "webp"))

    override fun palette(title: String): Path? =
        file(title, FileNameExtensionFilter("Palette files", "gpl", "hex"))

    override fun presetBundle(title: String): Path? =
        file(title, FileNameExtensionFilter("Preset bundles", "rotopreset"))

    override fun savePresetBundle(title: String, name: String): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter("Preset bundles", "rotopreset")
            selectedFile = File(name)
        }
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) return null
        val file = chooser.selectedFile
        return (if (file.name.endsWith(".rotopreset")) file else File(file.path + ".rotopreset")).toPath()
    }

    override fun program(title: String, start: Path?): Path? {
        val chooser = JFileChooser(start?.parent?.toFile()).apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.FILES_ONLY
        }
        return chosen(chooser)
    }

    private fun file(title: String, filter: FileNameExtensionFilter): Path? {
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = filter
        }
        return chosen(chooser)
    }

    private fun chosen(chooser: JFileChooser): Path? {
        val approved = chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
        return if (approved) chooser.selectedFile.toPath() else null
    }
}
