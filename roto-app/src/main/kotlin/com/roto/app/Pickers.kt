package com.roto.app

import java.awt.Component
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** Asks the artist for a folder or a file; null when they cancel. */
interface Pickers {
    fun folder(title: String, start: Path?): Path?

    fun video(title: String): Path?

    fun photo(title: String): Path?

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
