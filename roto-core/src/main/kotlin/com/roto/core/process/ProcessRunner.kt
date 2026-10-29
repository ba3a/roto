package com.roto.core.process

import java.io.IOException

/**
 * Runs an external program to completion. The one seam between Roto and the programs it drives —
 * `ffmpeg`, `ffprobe`, a project's `afterPublish` command — so a test can stand in for the program.
 */
fun interface ProcessRunner {

    /**
     * Runs [command] (the program, then its arguments) with no input and waits for it to exit.
     * Throws an [IOException] when the program cannot be started, for example because it is not on
     * `PATH`; a program that starts and then fails is a [ProcessResult] with a non-zero exit code.
     */
    fun run(command: List<String>): ProcessResult
}

/** What a finished program left: its exit code, everything it wrote to stdout, and its stderr as text. */
class ProcessResult(val exitCode: Int, val stdout: ByteArray, val stderr: String)
