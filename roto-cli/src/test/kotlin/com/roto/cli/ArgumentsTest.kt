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

        assertEquals(
            ProfileInvocation(Command.RENDER, "worker", here, Path.of("/art/library").toAbsolutePath()),
            invocation,
        )
    }

    @Test
    fun `options in either form override the defaults, relative to the working folder`() {
        val invocation = Arguments.parse(
            listOf("--project", "sub/game", "publish", "worker", "--library=../library"),
            environment,
            here,
            noSaved,
        ) as ProfileInvocation

        assertEquals(Command.PUBLISH, invocation.command)
        assertEquals(here.resolve("sub/game"), invocation.project)
        assertEquals(here.resolve("../library").normalize(), invocation.library)
    }

    @Test
    fun `the library chosen in the app is the last resort, asked for only then`() {
        val saved = Path.of("/saved/library").toAbsolutePath()
        var asked = 0
        val savedLibrary = { saved.also { asked++ } }

        val parsed = Arguments.parse(listOf("render", "worker"), emptyMap(), here, savedLibrary)
        assertEquals(saved, (parsed as ProfileInvocation).library)
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
            listOf("render", "worker", "--preset", "blank") to "--preset is not an option of render",
            listOf("init") to "No folder given",
            listOf("init", "game", "--id", "demo") to "init needs --preset",
            listOf("init", "game", "--preset", "blank") to "init needs --id",
            listOf("init", "game", "--preset", "blank", "--id", "a/b") to "not a project id",
            listOf("export") to "No bundle file given",
            listOf("export", "mine.zip") to "does not end in .rotopreset",
        ).forEach { (args, reason) ->
            val failure = assertFailsWith<UsageException>("$args") { Arguments.parse(args, environment, here, noSaved) }
            assertContains(failure.message!!, reason)
        }
    }

    @Test
    fun `init and export read their folder, file and options`() {
        assertEquals(
            InitInvocation(here.resolve("new"), "blank", "demo"),
            Arguments.parse(listOf("init", "new", "--preset=blank", "--id", "demo"), emptyMap(), here, noSaved),
        )
        assertEquals(
            ExportInvocation(here.resolve("mine.rotopreset"), here.resolve("sub")),
            Arguments.parse(listOf("export", "mine.rotopreset", "--project", "sub"), emptyMap(), here, noSaved),
        )
    }

    @Test
    fun `help is asked for by either flag`() {
        assertTrue(Arguments.wantsHelp(listOf("render", "--help")))
        assertTrue(Arguments.wantsHelp(listOf("-h")))
        assertFalse(Arguments.wantsHelp(listOf("render", "worker")))
    }
}
