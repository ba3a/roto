package com.roto.core.process

import kotlin.concurrent.thread

/**
 * Runs programs as operating-system processes. Stdout and stderr are drained at the same time, so a
 * program that fills one pipe while Roto reads the other can never stall. If the calling thread is
 * interrupted while waiting, the process is killed rather than left running.
 */
object SystemProcessRunner : ProcessRunner {

    override fun run(command: List<String>): ProcessResult {
        require(command.isNotEmpty()) { "A command needs at least the program to run" }
        val process = ProcessBuilder(command).start()
        try {
            process.outputStream.close()
            var stderr = ByteArray(0)
            val stderrReader = thread(isDaemon = true, name = "stderr of ${command.first()}") {
                stderr = process.errorStream.readAllBytes()
            }
            val stdout = process.inputStream.readAllBytes()
            val exitCode = process.waitFor()
            stderrReader.join()
            return ProcessResult(exitCode, stdout, stderr.toString(Charsets.UTF_8))
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
