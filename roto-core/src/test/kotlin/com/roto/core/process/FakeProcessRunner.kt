package com.roto.core.process

import java.nio.file.Path

/** Stands in for an external program: records every command and where it ran, and answers through [respond]. */
internal class FakeProcessRunner(private val respond: (List<String>) -> ProcessResult) : ProcessRunner {

    val commands = mutableListOf<List<String>>()
    val directories = mutableListOf<Path?>()

    override fun run(command: List<String>, workingDirectory: Path?): ProcessResult {
        commands += command
        directories += workingDirectory
        return respond(command)
    }

    companion object {
        fun success(stdout: String = ""): ProcessResult = success(stdout.toByteArray(Charsets.UTF_8))

        fun success(stdout: ByteArray): ProcessResult = ProcessResult(0, stdout, "")

        fun failure(exitCode: Int, stderr: String): ProcessResult = ProcessResult(exitCode, ByteArray(0), stderr)
    }
}
