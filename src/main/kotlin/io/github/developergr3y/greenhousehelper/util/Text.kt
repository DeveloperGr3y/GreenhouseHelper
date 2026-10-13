package io.github.developergr3y.greenhousehelper.util

private val formatting = Regex("§.")

/** "§aGarden§r" -> "Garden". */
fun String.stripFormatting() = replace(formatting, "")
