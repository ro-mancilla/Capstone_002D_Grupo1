package com.f776.core.ui.color

import androidx.compose.ui.graphics.Color

/// Mathematics - Deep Violet
private const val mathContainer = 0xFF5E35B1.toInt()

// Language/Literature - Royal Blue
private const val languageContainer = 0xFF1565C0.toInt()

// Chemistry - Deep Teal
private const val chemistryContainer = 0xFF00695C.toInt()

// Physical Education - Forest Green
private const val peContainer = 0xFF2E7D32.toInt()

// History - Warm Amber
private const val historyContainer = 0xFFB45309.toInt()

// Biology - Deep Crimson
private const val biologyContainer = 0xFFB71C1C.toInt()

// Art - Deep Rose
private const val artContainer = 0xFFAD1457.toInt()

// Foreign Language - Indigo
private const val foreignLangContainer = 0xFF303F9F.toInt()

// Physics - Cyan
private const val physicsContainer = 0xFF0097A7.toInt()

// Music - Brown
private const val musicContainer = 0xFF5D4037.toInt()

val DefaultCourseColors = listOf(
    Color(mathContainer),
    Color(languageContainer),
    Color(chemistryContainer),
    Color(peContainer),
    Color(historyContainer),
    Color(biologyContainer),
    Color(artContainer),
    Color(foreignLangContainer),
    Color(physicsContainer),
    Color(musicContainer),
)

fun getDefaultCourseColor(index: Int): Color {
    return DefaultCourseColors[index % DefaultCourseColors.size]
}
