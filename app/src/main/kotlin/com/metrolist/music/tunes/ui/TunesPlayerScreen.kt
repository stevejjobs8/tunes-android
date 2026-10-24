/**
 * 튠즈 (Metrolist fork) — main screen: the spinning cover in a steel ring.
 * Stateless: the caller supplies playback state and callbacks.
 */

package com.metrolist.music.tunes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
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

/** About 33 rpm, so the cover turns like a record at the speed of playback. */
private const val DEGREES_PER_SECOND = 198f

@Composable
fun TunesPlayerScreen(
    coverUrl: String?,
    title: String?,
    artist: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenMenu: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    val shownMs = scrubMs ?: positionMs
    val rotation = (shownMs / 1000f * DEGREES_PER_SECOND) % 360f
    val openSearch by rememberUpdatedState(onOpenSearch)

    MetalPlate(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -80.dp.toPx()) openSearch() },
                    ) { _, dy -> total += dy }
                },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.8f))
            SpinningCoverRing(
                coverUrl = coverUrl,
                rotationDeg = rotation,
                isPlaying = isPlaying,
                onToggle = onTogglePlay,
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = title ?: "",
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = EngravedText).engraved(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist ?: "",
                style = TextStyle(fontSize = 14.sp, color = EngravedSubText).engraved(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(28.dp))
            MetalSeekBar(
                positionMs = shownMs,
                durationMs = durationMs,
                onScrub = { scrubMs = it },
                onSeek = {
                    scrubMs = null
                    onSeek(it)
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val timeStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, color = EngravedSubText).engraved()
                Text(formatTime(shownMs), style = timeStyle)
                Text(formatTime(durationMs), style = timeStyle)
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
            ) {
                MetalKnob(
                    onClick = onOpenMenu,
                    contentDescription = "메뉴",
                    modifier = Modifier.align(Alignment.CenterStart),
                ) { KnobIcon(R.drawable.tunes_menu, size = 20.dp) }
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(width = 100.dp, height = 56.dp)
                            .semantics {
                                contentDescription = "검색 열기"
                                role = Role.Button
                            }.clickable(onClick = onOpenSearch),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.expand_less),
                        contentDescription = null,
                        tint = EngravedSubText,
                        modifier = Modifier.size(24.dp),
                    )
                    Box(
                        Modifier
                            .width(44.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF4A5056)),
                    )
                }
            }
        }
    }
}
