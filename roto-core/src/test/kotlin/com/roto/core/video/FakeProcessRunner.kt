package com.roto.core.video

import com.roto.core.process.ProcessResult
import com.roto.core.process.ProcessRunner

/** Stands in for `ffmpeg` and `ffprobe`: records every command and answers through [respond]. */
internal class FakeProcessRunner(private val respond: (List<String>) -> ProcessResult) : ProcessRunner {

    val commands = mutableListOf<List<String>>()

    override fun run(command: List<String>): ProcessResult {
        commands += command
        return respond(command)
    }

    companion object {
        fun success(stdout: String = ""): ProcessResult = success(stdout.toByteArray(Charsets.UTF_8))

        fun success(stdout: ByteArray): ProcessResult = ProcessResult(0, stdout, "")

        fun failure(exitCode: Int, stderr: String): ProcessResult = ProcessResult(exitCode, ByteArray(0), stderr)
    }
}
