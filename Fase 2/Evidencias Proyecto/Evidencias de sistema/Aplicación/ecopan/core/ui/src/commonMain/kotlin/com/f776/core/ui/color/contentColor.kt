package com.f776.core.ui.color

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.ktx.from


fun Color.contentColor(desiredRatio: Double = 7.0): Color {
    // 1) get the background tone in HCT
    val bgTone = Hct.fromInt(this.toArgb()).tone

    // 2) compute a target foreground tone that meets the contrast target
    val fgTone = DynamicColor.foregroundTone(bgTone, desiredRatio)

    // 3) pick a palette to produce the ARGB for that tone. For example:
    //    use a tonal palette derived from the background (or from another seed hue you prefer).
    val palette = TonalPalette.from(this) // or TonalPalette.fromInt(bg.toArgb())

    // 4) get an HCT color at the chosen tone and convert to ARGB / Compose Color
    val fgHct = palette.getHct(fgTone)      // returns an Hct for that tone
    val fgArgb = fgHct.toInt()              // convert Hct -> ARGB (library utility)
    return Color(fgArgb)
}