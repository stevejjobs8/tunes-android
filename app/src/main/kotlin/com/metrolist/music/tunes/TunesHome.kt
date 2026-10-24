/**
 * 튠즈 (Metrolist fork) — glue between the metal player screen and Metrolist's player.
 * Only this file knows about Metrolist internals; the screen itself takes plain values.
 */

package com.metrolist.music.tunes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.tunes.ui.TunesPlayerScreen
import com.metrolist.music.ui.screens.Screens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive

/**
 * Full-screen metal player shown over Metrolist's UI. The menu knob reveals the
 * original screens (library, settings…); back at their root brings the player back.
 * Swiping up opens search, and the player returns once a new song starts.
 */
@Composable
fun TunesHomeOverlay(navController: NavController) {
    var visible by rememberSaveable { mutableStateOf(true) }
    var openedForSearch by rememberSaveable { mutableStateOf(false) }

    val playerConnection = LocalPlayerConnection.current
    val metadataFlow: StateFlow<MediaMetadata?> = playerConnection?.mediaMetadata ?: remember { MutableStateFlow(null) }
    val playingFlow: StateFlow<Boolean> = playerConnection?.isPlaying ?: remember { MutableStateFlow(false) }
    val metadata by metadataFlow.collectAsState()
    val isPlaying by playingFlow.collectAsState()
    val currentMetadata by rememberUpdatedState(metadata)

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playerConnection, isPlaying, visible) {
        if (playerConnection == null || !visible) return@LaunchedEffect
        while (isActive) {
            runCatching {
                val player = playerConnection.player
                positionMs = player.currentPosition
                durationMs = player.duration.takeIf { it > 0 } ?: ((currentMetadata?.duration ?: 0) * 1000L)
            }
            delay(if (isPlaying) 16L else 400L)
        }
    }

    LaunchedEffect(metadata?.id) {
        if (openedForSearch && metadata != null) {
            openedForSearch = false
            visible = true
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val atRoot = backStackEntry != null && navController.previousBackStackEntry == null
    BackHandler(enabled = !visible && atRoot) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 6 },
        exit = fadeOut() + slideOutVertically { it / 6 },
    ) {
        TunesPlayerScreen(
            coverUrl = metadata?.thumbnailUrl,
            title = metadata?.title,
            artist = metadata?.artists?.joinToString { it.name },
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            onTogglePlay = { playerConnection?.togglePlayPause() },
            onSeek = { ms ->
                positionMs = ms
                playerConnection?.seekTo(ms)
            },
            onOpenMenu = { visible = false },
            onOpenSearch = {
                openedForSearch = true
                visible = false
                navController.navigate(Screens.Search.route) { launchSingleTop = true }
            },
        )
    }
}
