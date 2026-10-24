/**
 * 튠즈 (Metrolist fork) — cold metal look.
 * Kept in its own file so upstream merges touch as little as possible.
 */

package com.metrolist.music.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.metrolist.music.constants.DarkModeKey
import com.metrolist.music.constants.DynamicThemeKey
import com.metrolist.music.constants.SelectedThemeColorKey
import com.metrolist.music.constants.SlimNavBarKey
import com.metrolist.music.ui.screens.settings.DarkMode
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

// Cold brushed-steel blue. Picked in settings like any other palette.
val TunesSteelColor = Color(0xFF8A9BAE)

private val TunesDefaultsAppliedKey = booleanPreferencesKey("tunesDefaultsApplied")

/**
 * On first launch, start in the metal look: dark mode, steel color (not album-art colors),
 * icon-only navigation bar. Runs once, so the user can still change all of these in settings.
 */
fun Context.applyTunesDefaultsOnce() = runBlocking(Dispatchers.IO) {
    if (dataStore.data.first()[TunesDefaultsAppliedKey] == true) return@runBlocking
    runCatching {
        dataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                this[DarkModeKey] = DarkMode.ON.name
                this[DynamicThemeKey] = false
                this[SelectedThemeColorKey] = TunesSteelColor.toArgb()
                this[SlimNavBarKey] = true
                this[TunesDefaultsAppliedKey] = true
            }
        }
    }
}

/** Graphite surfaces with machined steel edges, used with the steel palette in dark mode. */
fun ColorScheme.tunesMetal(): ColorScheme = copy(
    primary = Color(0xFFC9D4DF),
    onPrimary = Color(0xFF14191F),
    primaryContainer = Color(0xFF2E3742),
    onPrimaryContainer = Color(0xFFDCE5EE),
    secondary = Color(0xFF9AA7B4),
    onSecondary = Color(0xFF14191F),
    secondaryContainer = Color(0xFF29313A),
    onSecondaryContainer = Color(0xFFD3DCE5),
    tertiary = Color(0xFF7FD3E8),
    onTertiary = Color(0xFF00313B),
    background = Color(0xFF0B0D10),
    onBackground = Color(0xFFE3E7EB),
    surface = Color(0xFF0B0D10),
    onSurface = Color(0xFFE3E7EB),
    surfaceVariant = Color(0xFF22282F),
    onSurfaceVariant = Color(0xFF9BA5AF),
    surfaceDim = Color(0xFF0B0D10),
    surfaceBright = Color(0xFF2A3038),
    surfaceContainerLowest = Color(0xFF07090B),
    surfaceContainerLow = Color(0xFF111418),
    surfaceContainer = Color(0xFF15191E),
    surfaceContainerHigh = Color(0xFF1B2026),
    surfaceContainerHighest = Color(0xFF232931),
    surfaceTint = Color(0xFF8A9BAE),
    outline = Color(0xFF4A535D),
    outlineVariant = Color(0xFF2A3139),
)
