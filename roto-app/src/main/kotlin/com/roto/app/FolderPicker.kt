package com.roto.app

import java.awt.Component
import java.nio.file.Path
import javax.swing.JFileChooser

/** Asks the artist for a folder; null when they cancel. */
fun interface FolderPicker {
    fun pick(title: String, start: Path?): Path?
}

/** The system folder chooser, shown over [parent]. */
class SwingFolderPicker(private val parent: Component?) : FolderPicker {
    override fun pick(title: String, start: Path?): Path? {
        val chooser = JFileChooser(start?.toFile()).apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        val approved = chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION
        return if (approved) chooser.selectedFile.toPath() else null
    }
}
