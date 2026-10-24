/**
 * 튠즈 (Metrolist fork) — brushed-steel color scheme for every Material screen
 * that has not been rebuilt as a metal screen yet, so nothing looks like a stock app.
 */

package com.metrolist.music.tunes.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val Steel100 = Color(0xFFDDE1E4)
private val Steel200 = Color(0xFFD0D4D8)
private val Steel300 = Color(0xFFC4C9CD)
private val Steel400 = Color(0xFFB4BABF)
private val Steel500 = Color(0xFF8A9096)
private val Steel700 = Color(0xFF5A636D)
private val Steel800 = Color(0xFF3E4449)

val TunesSteelColors: ColorScheme =
    lightColorScheme(
        primary = Steel800,
        onPrimary = Color(0xFFEEF1F4),
        primaryContainer = Steel400,
        onPrimaryContainer = EngravedText,
        inversePrimary = Steel300,
        secondary = Steel700,
        onSecondary = Color(0xFFEEF1F4),
        secondaryContainer = Steel400,
        onSecondaryContainer = EngravedText,
        tertiary = TunesLamp,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE9B9BB),
        onTertiaryContainer = Color(0xFF410E10),
        background = Steel300,
        onBackground = EngravedText,
        surface = Steel300,
        onSurface = EngravedText,
        surfaceVariant = Steel400,
        onSurfaceVariant = EngravedSubText,
        surfaceTint = Steel700,
        inverseSurface = Color(0xFF2A2E33),
        inverseOnSurface = Steel200,
        outline = Steel500,
        outlineVariant = Steel400,
        surfaceBright = Steel100,
        surfaceDim = Steel400,
        surfaceContainerLowest = Steel100,
        surfaceContainerLow = Steel200,
        surfaceContainer = Steel300,
        surfaceContainerHigh = Steel400,
        surfaceContainerHighest = Color(0xFFA9AFB4),
        scrim = Color.Black,
    )
