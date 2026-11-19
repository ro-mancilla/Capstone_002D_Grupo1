package com.f776.convention


enum class OS {
    WIN, LINUX, MACOS
}

fun determineOS(): OS {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("win") -> OS.WIN
        os.contains("mac") -> OS.MACOS
        else -> OS.LINUX
    }
}