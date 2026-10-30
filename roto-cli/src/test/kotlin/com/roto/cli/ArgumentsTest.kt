package com.roto.cli

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArgumentsTest {

    private val here = Path.of("/work/game").toAbsolutePath()
    private val environment = mapOf(Arguments.LIBRARY_VARIABLE to "/art/library")
    private val noSaved = { null }

    @Test
    fun `command and profile, the project here and the library from the environment`() {
        val invocation = Arguments.parse(listOf("render", "worker"), environment, here, noSaved)

        assertEquals(Invocation(Command.RENDER, "worker", here, Path.of("/art/library").toAbsolutePath()), invocation)
    }

    @Test
    fun `options in either form override the defaults, relative to the working folder`() {
        val invocation = Arguments.parse(
            listOf("--project", "sub/game", "publish", "worker", "--library=../library"),
            environment,
            here,
            noSaved,
        )

        assertEquals(Command.PUBLISH, invocation.command)
        assertEquals(here.resolve("sub/game"), invocation.project)
        assertEquals(here.resolve("../library").normalize(), invocation.library)
    }

    @Test
    fun `the library chosen in the app is the last resort, asked for only then`() {
        val saved = Path.of("/saved/library").toAbsolutePath()
        var asked = 0
        val savedLibrary = { saved.also { asked++ } }

        assertEquals(saved, Arguments.parse(listOf("render", "worker"), emptyMap(), here, savedLibrary).library)
        assertEquals(1, asked)
        Arguments.parse(listOf("render", "worker"), environment, here, savedLibrary)
        Arguments.parse(listOf("render", "worker", "--library", "x"), emptyMap(), here, savedLibrary)
        assertEquals(1, asked)
    }

    @Test
    fun `without a library option, variable or saved choice there is no library`() {
        val failure = assertFailsWith<UsageException> {
            Arguments.parse(listOf("render", "worker"), emptyMap(), here, noSaved)
        }

        assertContains(failure.message!!, Arguments.LIBRARY_VARIABLE)
        assertContains(failure.message!!, "in the app")
    }

    @Test
    fun `malformed command lines are usage errors naming the problem`() {
        mapOf(
            listOf<String>() to "No command",
            listOf("draw", "worker") to "Unknown command 'draw'",
            listOf("render") to "No profile",
            listOf("render", "worker", "extra") to "Unexpected 'extra'",
            listOf("render", "worker", "--out", "x") to "Unknown option --out",
            listOf("render", "worker", "--project") to "--project needs a folder",
            listOf("render", "worker", "--project=") to "--project needs a folder",
            listOf("render", "worker", "--library", "a", "--library", "b") to "--library is given twice",
            listOf("render", "../worker") to "not a profile name",
        ).forEach { (args, reason) ->
            val failure = assertFailsWith<UsageException>("$args") { Arguments.parse(args, environment, here, noSaved) }
            assertContains(failure.message!!, reason)
        }
    }

    @Test
    fun `help is asked for by either flag`() {
        assertTrue(Arguments.wantsHelp(listOf("render", "--help")))
        assertTrue(Arguments.wantsHelp(listOf("-h")))
        assertFalse(Arguments.wantsHelp(listOf("render", "worker")))
    }
}
