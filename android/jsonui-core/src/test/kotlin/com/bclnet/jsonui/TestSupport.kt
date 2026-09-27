package com.bclnet.jsonui

import java.io.File

object Samples {
    /** The shared samples directory at the repository root (tests run with the module directory as cwd). */
    val directory: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "samples") }
        .first { it.isDirectory }

    fun load(name: String): JsonDocument = JsonDocument.parse(File(directory, name).readText())
}
