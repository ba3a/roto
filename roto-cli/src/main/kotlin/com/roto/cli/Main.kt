package com.roto.cli

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(Cli(System.out, System.err).run(args.toList()))
}
