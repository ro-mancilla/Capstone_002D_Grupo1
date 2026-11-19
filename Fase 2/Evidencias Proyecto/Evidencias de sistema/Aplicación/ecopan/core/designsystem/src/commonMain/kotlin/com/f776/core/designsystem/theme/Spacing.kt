package com.f776.core.designsystem.theme

import androidx.compose.ui.unit.dp

/** The 4dp spacing grid; `space4` is 16dp, `space0_5` is 2dp. Read directly as `EcopanSpacing`. */
object EcopanSpacing {
    val space0_5 = 2.dp
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 20.dp
    val space6 = 24.dp
    val space8 = 32.dp
    val space10 = 40.dp
    val space12 = 48.dp
    val space16 = 64.dp

    /** Minimum height of any control (`--tap-min`). */
    val controlMin = 44.dp

    /** Height of a text field or select. */
    val field = 48.dp

    /** Touch size of a till button: the baker and cashier press these with a floured thumb. */
    val touch = 56.dp

    /** Minimum height of a list row. */
    val rowMin = 56.dp

    val screenMargin = space4
    val cardGap = space3
    val chipGap = space2
    val sectionGap = space8

    /** Padding inside a card. */
    val cardPadding = space5
}
