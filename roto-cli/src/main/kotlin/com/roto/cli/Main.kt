package com.roto.cli

import kotlin.system.exitProcess

/** Runs the `roto` command with [args] and exits with its code. */
fun main(args: Array<String>) {
    exitProcess(Cli(System.out, System.err).run(args.toList()))
}
