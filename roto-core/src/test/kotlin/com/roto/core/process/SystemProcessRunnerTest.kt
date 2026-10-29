package com.roto.core.process

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/** Runs the JVM executing these tests, the one program known to be present on every machine. */
class SystemProcessRunnerTest {

    private val java = ProcessHandle.current().info().command().orElseThrow()

    @Test
    fun `stdout and the exit code come back`() {
        val result = SystemProcessRunner.run(listOf(java, "--version"))

        assertEquals(0, result.exitCode)
        assertContains(result.stdout.toString(Charsets.UTF_8), Runtime.version().feature().toString())
    }

    @Test
    fun `a program that fails reports its exit code and stderr`() {
        val result = SystemProcessRunner.run(listOf(java, "-cp", "", "com.roto.NoSuchMain"))

        assertNotEquals(0, result.exitCode)
        assertContains(result.stderr, "com.roto.NoSuchMain")
    }

    @Test
    fun `a program that is not there cannot be started`() {
        assertFailsWith<IOException> { SystemProcessRunner.run(listOf("roto-no-such-program")) }
        assertFailsWith<IllegalArgumentException> { SystemProcessRunner.run(emptyList()) }
    }
}
