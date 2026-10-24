/**
 * 튠즈 (Metrolist fork) — glue between the metal screens and Metrolist's player and database.
 * Only this file knows about Metrolist internals; the screens themselves take plain values.
 */

package com.metrolist.music.tunes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.constants.PauseSearchHistoryKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.tunes.ui.RingItem
import com.metrolist.music.tunes.ui.RingRackScreen
import com.metrolist.music.tunes.ui.SearchHit
import com.metrolist.music.tunes.ui.TunesPlayerScreen
import com.metrolist.music.tunes.ui.TrackItem
import com.metrolist.music.tunes.ui.TunesPlaylistScreen
import com.metrolist.music.tunes.ui.TunesSearchSheet
import com.metrolist.music.ui.menu.playlistDuplicatesBatched
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the search sheet shows for a result; null for results it cannot play. */
private fun YTItem.toSearchHit(): SearchHit? =
    when (this) {
        is SongItem -> SearchHit(id, title, artists.joinToString { it.name }, thumbnail)
        is AlbumItem -> SearchHit(id, title, artists.orEmpty().joinToString { it.name }, thumbnail)
        is PlaylistItem -> SearchHit(id, title, author?.name.orEmpty(), thumbnail)
        is ArtistItem -> if (shuffleEndpoint != null || radioEndpoint != null) SearchHit(id, title, "", thumbnail) else null
        else -> null
    }

private enum class TunesPage { Player, Rings, Playlist, Hidden }

/** A search result's cover on its way into the main ring. */
private data class Flight(
    val coverUrl: String?,
    val from: Rect,
)

/**
 * Full-screen metal UI shown over Metrolist's screens.
 * Player (main) → menu knob → playlist rings → menu knob → Metrolist's own screens.
 * Back at the root of Metrolist's screens brings the player back.
 * Swiping up on the player slides the search sheet over it.
 */
@Composable
fun TunesHomeOverlay(navController: NavController) {
    var page by rememberSaveable { mutableStateOf(TunesPage.Player) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var mountedRingId by rememberSaveable { mutableStateOf<String?>(null) }
    var openPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }

    val playerConnection = LocalPlayerConnection.current
    val database = LocalDatabase.current
    val syncUtils = LocalSyncUtils.current
    val context = LocalContext.current
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

    // Search
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<YTItem>>(emptyList()) }
    val songs = results.filterIsInstance<SongItem>()
    var searching by remember { mutableStateOf(false) }
    var searchNote by remember { mutableStateOf<String?>(null) }
    val hits = results.mapNotNull { it.toSearchHit() }
    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        scope.launch {
            searching = true
            searchNote = null
            // Same request as Metrolist's own search screen, signed in like it unless search history is paused.
            val signedIn = !context.dataStore.get(PauseSearchHistoryKey, false)
            val summary = YouTube.searchSummary(q, recordSearchHistory = signedIn)
            val items = summary.getOrNull()?.summaries?.flatMap { it.items }.orEmpty()
            // Songs and videos first, then albums, playlists and artists.
            results =
                items
                    .filter { it.toSearchHit() != null }
                    .distinctBy { it.id }
                    .sortedBy { if (it is SongItem) 0 else 1 }
            searchNote =
                when {
                    summary.isFailure -> "검색 실패: " + summary.exceptionOrNull()?.let { it.message ?: it::class.simpleName }
                    results.isEmpty() -> "결과 없음 (받은 항목 ${items.size}개: " + items.groupingBy { it::class.simpleName }.eachCount() + ")"
                    else -> null
                }
            summary.exceptionOrNull()?.let { android.util.Log.w("Tunes", "search failed", it) }
            searching = false
        }
    }

    // Cover flight from a search result into the main ring
    var origin by remember { mutableStateOf(Offset.Zero) }
    var ringBounds by remember { mutableStateOf(Rect.Zero) }
    var flight by remember { mutableStateOf<Flight?>(null) }
    val flightProgress = remember { Animatable(0f) }
    LaunchedEffect(flight) {
        if (flight == null) return@LaunchedEffect
        flightProgress.snapTo(0f)
        flightProgress.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
        flight = null
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val atRoot = backStackEntry != null && navController.previousBackStackEntry == null
    BackHandler(enabled = page == TunesPage.Rings) { page = TunesPage.Player }
    BackHandler(enabled = page == TunesPage.Playlist) { page = TunesPage.Rings }
    BackHandler(enabled = page == TunesPage.Hidden && atRoot) { page = TunesPage.Player }
    BackHandler(enabled = page == TunesPage.Player && searchOpen) { searchOpen = false }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
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
                        onOpenSearch = { searchOpen = true },
                        onRingBounds = { ringBounds = it },
                    )

                TunesPage.Rings ->
                    RingRackScreen(
                        rings = rings,
                        mountedId = mountedId,
                        onMount = { mountedRingId = it },
                        onPlay = {
                            val ring = rings.firstOrNull { it.id == mountedId } ?: return@RingRackScreen
                            scope.launch {
                                val tracks = database.playlistSongs(ring.id).first()
                                if (tracks.isNotEmpty()) {
                                    playerConnection?.playQueue(
                                        ListQueue(title = ring.name, items = tracks.map { it.song.toMediaItem() }),
                                    )
                                    page = TunesPage.Player
                                }
                            }
                        },
                        onOpen = {
                            openPlaylistId = mountedId ?: return@RingRackScreen
                            page = TunesPage.Playlist
                        },
                        onBack = { page = TunesPage.Player },
                        onMore = { page = TunesPage.Hidden },
                    )

                TunesPage.Playlist -> {
                    val id = openPlaylistId
                    val playlist = playlists.firstOrNull { it.id == id }
                    val entries by remember(id) { id?.let { database.playlistSongs(it) } ?: MutableStateFlow(emptyList<PlaylistSong>()) }
                        .collectAsState(initial = emptyList())
                    val tracks =
                        entries.map {
                            TrackItem(
                                id = it.song.id,
                                title = it.song.song.title,
                                artist = it.song.artists.joinToString { a -> a.name },
                                coverUrl = it.song.song.thumbnailUrl,
                                durationMs = it.song.song.duration * 1000L,
                            )
                        }
                    fun play(items: List<MediaItem>, start: Int) {
                        if (items.isEmpty()) return
                        playerConnection?.playQueue(ListQueue(title = playlist?.playlist?.name, items = items, startIndex = start))
                        page = TunesPage.Player
                    }
                    TunesPlaylistScreen(
                        name = playlist?.playlist?.name ?: "",
                        coverUrl = playlist?.thumbnails?.firstOrNull(),
                        tracks = tracks,
                        nowPlayingId = metadata?.id,
                        onBack = { page = TunesPage.Rings },
                        onPlayAll = { play(entries.map { it.song.toMediaItem() }, 0) },
                        onShuffle = { play(entries.map { it.song.toMediaItem() }.shuffled(), 0) },
                        onPlayTrack = { index -> play(entries.map { it.song.toMediaItem() }, index) },
                    )
                }

                TunesPage.Hidden -> Unit
            }
        }

        AnimatedVisibility(
            visible = searchOpen && page == TunesPage.Player,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            TunesSearchSheet(
                query = query,
                onQueryChange = { query = it },
                onSubmit = { runSearch() },
                hits = hits,
                searching = searching,
                note = searchNote,
                rings = rings,
                onPick = { hit, bounds ->
                    val item = results.firstOrNull { it.id == hit.id } ?: return@TunesSearchSheet
                    val queue =
                        when (item) {
                            is SongItem -> YouTubeQueue(WatchEndpoint(videoId = item.id), item.toMediaMetadata())
                            is AlbumItem -> YouTubeQueue(WatchEndpoint(playlistId = item.playlistId))
                            is PlaylistItem -> YouTubeQueue(item.playEndpoint ?: WatchEndpoint(playlistId = item.id.removePrefix("VL")))
                            is ArtistItem -> YouTubeQueue(item.shuffleEndpoint ?: item.radioEndpoint ?: return@TunesSearchSheet)
                            else -> return@TunesSearchSheet
                        }
                    playerConnection?.playQueue(queue)
                    flight = Flight(hit.coverUrl, bounds)
                    searchOpen = false
                },
                onDropOnRing = { hit, ring ->
                    val song = songs.firstOrNull { it.id == hit.id } ?: return@TunesSearchSheet
                    val playlist = playlists.firstOrNull { it.id == ring.id } ?: return@TunesSearchSheet
                    scope.launch(Dispatchers.IO) {
                        if (database.playlistDuplicatesBatched(playlist.id, listOf(song.id)).isNotEmpty()) return@launch
                        database.withTransaction { insert(song.toMediaMetadata()) }
                        database.addSongsToPlaylist(playlist, listOf(song.id to null))
                        playlist.playlist.browseId?.let { syncUtils.scheduleAddToPlaylist(it, playlist.id, listOf(song.id)) }
                    }
                },
                onClose = { searchOpen = false },
            )
        }

        flight?.let { f ->
            val t = flightProgress.value
            // Land on the cover inside the ring, not the ring's outer edge.
            val to = Rect(ringBounds.center, ringBounds.width * 0.418f)
            val rect = lerp(f.from, to, t)
            val lift = with(LocalDensity.current) { 90.dp.toPx() } * sin(PI * t).toFloat()
            val density = LocalDensity.current
            AsyncImage(
                model = f.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .offset { IntOffset((rect.left - origin.x).roundToInt(), (rect.top - origin.y - lift).roundToInt()) }
                        .size(with(density) { rect.width.toDp() })
                        .graphicsLayer {
                            rotationZ = 360f * t
                            alpha = if (t < 0.85f) 1f else (1f - t) / 0.15f
                        }.shadow(16.dp, CircleShape)
                        .clip(CircleShape),
            )
        }
    }
}
