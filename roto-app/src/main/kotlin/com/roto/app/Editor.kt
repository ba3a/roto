package com.roto.app

import com.roto.core.process.ProcessRunner
import java.awt.Desktop
import java.io.IOException
import java.nio.file.Path

/** The program the artist paints in. */
fun interface Editor {

    /** Opens [file], or throws an [IOException] saying why it cannot. */
    fun open(file: Path)
}

/** The [program] the artist chose: it runs through [runner], so [open] returns once they close it. */
class ChosenEditor(private val program: Path, private val runner: ProcessRunner) : Editor {

    override fun open(file: Path) {
        val result = runner.run(listOf(program.toString(), file.toString()), file.parent)
        if (result.exitCode != 0) throw IOException("${program.fileName} stopped with exit code ${result.exitCode}")
    }
}

/** Whichever program the system opens the file with, for an artist who has chosen none. */
object SystemEditor : Editor {

    override fun open(file: Path) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            throw IOException(noProgram(file))
        }
        try {
            Desktop.getDesktop().open(file.toFile())
        } catch (e: IOException) {
            throw IOException(noProgram(file), e)
        }
    }

    private fun noProgram(file: Path) =
        "This system has no program for ${file.fileName}; choose your editor on the home screen"
}
