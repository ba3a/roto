package com.roto.cli

import kotlin.system.exitProcess

private const val USAGE_EXIT_CODE = 2

private val usage = """
    Usage: roto <command> <profile>

    Commands:
      render    Re-render the draft handoff for every clip in a profile
      publish   Publish the polished art of every clip in a profile
""".trimIndent()

fun main(args: Array<String>) {
    System.err.println(usage)
    if (args.isNotEmpty()) {
        System.err.println()
        System.err.println("Command '${args.first()}' is not implemented yet.")
    }
    exitProcess(USAGE_EXIT_CODE)
}
