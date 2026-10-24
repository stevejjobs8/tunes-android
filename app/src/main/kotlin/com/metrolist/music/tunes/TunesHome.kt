/**
 * 튠즈 (Metrolist fork) — glue between the metal screens and Metrolist's player and database.
 * Only this file knows about Metrolist internals; the screens themselves take plain values.
 */

package com.metrolist.music.tunes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.tunes.ui.RingItem
import com.metrolist.music.tunes.ui.RingRackScreen
import com.metrolist.music.tunes.ui.TunesPlayerScreen
import com.metrolist.music.ui.screens.Screens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class TunesPage { Player, Rings, Hidden }

/**
 * Full-screen metal UI shown over Metrolist's screens.
 * Player (main) → menu knob → playlist rings → menu knob → Metrolist's own screens.
 * Back at the root of Metrolist's screens brings the player back.
 * Swiping up on the player opens search, and the player returns once a new song starts.
 */
@Composable
fun TunesHomeOverlay(navController: NavController) {
    var page by rememberSaveable { mutableStateOf(TunesPage.Player) }
    var openedForSearch by rememberSaveable { mutableStateOf(false) }
    var mountedRingId by rememberSaveable { mutableStateOf<String?>(null) }

    val playerConnection = LocalPlayerConnection.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()

    val metadataFlow: StateFlow<MediaMetadata?> = playerConnection?.mediaMetadata ?: remember { MutableStateFlow(null) }
    val playingFlow: StateFlow<Boolean> = playerConnection?.isPlaying ?: remember { MutableStateFlow(false) }
    val metadata by metadataFlow.collectAsState()
    val isPlaying by playingFlow.collectAsState()
    val currentMetadata by rememberUpdatedState(metadata)

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playerConnection, isPlaying, page) {
        if (playerConnection == null || page != TunesPage.Player) return@LaunchedEffect
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
            page = TunesPage.Player
        }
    }

    val playlists by remember(database) { database.playlistsByCreateDateAsc() }.collectAsState(initial = emptyList())
    val rings =
        playlists.map {
            RingItem(
                id = it.id,
                name = it.playlist.name,
                songCount = it.songCount,
                coverUrl = it.thumbnails.firstOrNull(),
            )
        }
    val mountedId = mountedRingId?.takeIf { id -> rings.any { it.id == id } } ?: rings.firstOrNull()?.id

    val backStackEntry by navController.currentBackStackEntryAsState()
    val atRoot = backStackEntry != null && navController.previousBackStackEntry == null
    BackHandler(enabled = page == TunesPage.Rings) { page = TunesPage.Player }
    BackHandler(enabled = page == TunesPage.Hidden && atRoot) { page = TunesPage.Player }

    AnimatedContent(
        targetState = page,
        transitionSpec = { (fadeIn() + slideInVertically { it / 8 }) togetherWith (fadeOut() + slideOutVertically { it / 8 }) },
        label = "tunesPage",
    ) { shown ->
        when (shown) {
            TunesPage.Player ->
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
                    onOpenMenu = { page = TunesPage.Rings },
                    onOpenSearch = {
                        openedForSearch = true
                        page = TunesPage.Hidden
                        navController.navigate(Screens.Search.route) { launchSingleTop = true }
                    },
                )

            TunesPage.Rings ->
                RingRackScreen(
                    rings = rings,
                    mountedId = mountedId,
                    onMount = { mountedRingId = it },
                    onPlay = {
                        val ring = rings.firstOrNull { it.id == mountedId } ?: return@RingRackScreen
                        scope.launch {
                            val songs = database.playlistSongs(ring.id).first()
                            if (songs.isNotEmpty()) {
                                playerConnection?.playQueue(
                                    ListQueue(title = ring.name, items = songs.map { it.song.toMediaItem() }),
                                )
                                page = TunesPage.Player
                            }
                        }
                    },
                    onOpen = {
                        val id = mountedId ?: return@RingRackScreen
                        page = TunesPage.Hidden
                        navController.navigate("local_playlist/$id")
                    },
                    onBack = { page = TunesPage.Player },
                    onMore = { page = TunesPage.Hidden },
                )

            TunesPage.Hidden -> Unit
        }
    }
}
