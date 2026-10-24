/**
 * 튠즈 (Metrolist fork) — inside a playlist ring: the ring at the top, songs stamped into the plate below.
 * The song that is playing sits pressed into the plate with its lamp lit.
 */

package com.metrolist.music.tunes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R

data class TrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val durationMs: Long,
)

@Composable
fun TunesPlaylistScreen(
    name: String,
    coverUrl: String?,
    tracks: List<TrackItem>,
    nowPlayingId: String?,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onPlayTrack: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalMs = tracks.sumOf { it.durationMs.coerceAtLeast(0) }
    MetalPlate(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    MetalKnob(onClick = onBack, contentDescription = "뒤로", size = 52.dp) {
                        KnobIcon(R.drawable.arrow_back, size = 20.dp)
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    PlaylistRing(
                        coverUrl = coverUrl,
                        lampOn = tracks.any { it.id == nowPlayingId },
                        modifier =
                            Modifier
                                .size(180.dp)
                                .shadow(14.dp, CircleShape),
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        name,
                        style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = EngravedText).engraved(),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Text(
                        "${tracks.size}곡 · ${formatLong(totalMs)}",
                        style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, color = EngravedSubText).engraved(),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(
                        Modifier.padding(top = 18.dp, bottom = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        MetalKnob(onClick = onPlayAll, contentDescription = "전체 재생", size = 56.dp) {
                            KnobIcon(R.drawable.play)
                        }
                        MetalKnob(onClick = onShuffle, contentDescription = "섞어서 재생", size = 56.dp) {
                            KnobIcon(R.drawable.shuffle)
                        }
                    }
                }
            }
            itemsIndexed(tracks, key = { i, t -> "$i:${t.id}" }) { index, track ->
                TrackRow(track = track, playing = track.id == nowPlayingId, onClick = { onPlayTrack(index) })
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun TrackRow(
    track: TrackItem,
    playing: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .clip(shape)
            .then(
                if (playing) {
                    // Pressed into the plate
                    Modifier
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Black.copy(alpha = 0.04f))))
                        .border(1.dp, Color.White.copy(alpha = 0.6f), shape)
                } else {
                    Modifier
                },
            ).semantics {
                contentDescription = "${track.title}, ${track.artist}"
                role = Role.Button
            }.clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistRing(
            coverUrl = track.coverUrl,
            lampOn = false,
            modifier =
                Modifier
                    .size(48.dp)
                    .shadow(4.dp, CircleShape),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = EngravedText).engraved(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                style = TextStyle(fontSize = 12.sp, color = EngravedSubText).engraved(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            formatTime(track.durationMs),
            style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, color = EngravedSubText).engraved(),
        )
        Spacer(Modifier.width(10.dp))
        MetalLamp(on = playing, size = 8.dp)
    }
}

private fun formatLong(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else formatTime(ms)
}
