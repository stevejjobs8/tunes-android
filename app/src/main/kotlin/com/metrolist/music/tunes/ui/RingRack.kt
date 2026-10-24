/**
 * 튠즈 (Metrolist fork) — playlists as steel rings.
 * The active ring sits in a mount at the top; tapping a stored ring flies it up,
 * spins it into the mount, and drops the previous ring into the freed hollow.
 */

package com.metrolist.music.tunes.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import kotlinx.coroutines.delay

data class RingItem(
    val id: String,
    val name: String,
    val songCount: Int,
    val coverUrl: String?,
)

/** A steel ring with a cover set into it, sized by its caller. */
@Composable
fun PlaylistRing(
    coverUrl: String?,
    lampOn: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.tunes_ring),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize(0.84f)
                .clip(CircleShape)
                .background(Color(0xFF16191C))
                .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
        )
        Box(
            Modifier
                .fillMaxSize(0.8f)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF5A636D), Color(0xFF1A1D20)))),
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(0.75f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f))),
            )
        }
        MetalLamp(
            on = lampOn,
            size = 7.dp,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 2.dp),
        )
    }
}

/** A hollow pressed into the plate where a ring can rest. */
@Composable
private fun Hollow(modifier: Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    0f to Color.Black.copy(alpha = 0.06f),
                    0.85f to Color.Black.copy(alpha = 0.12f),
                    1f to Color.Black.copy(alpha = 0.32f),
                ),
            ).border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
    )
}

@Composable
fun RingRackScreen(
    rings: List<RingItem>,
    mountedId: String?,
    onMount: (String) -> Unit,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    onBack: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val stored = rings.filter { it.id != mountedId }.take(MAX_STORED)
    // Slot of each stored ring; the ring that leaves the mount takes the newcomer's slot.
    val slotOf = remember { mutableStateMapOf<String, Int>() }
    var spins by remember { mutableIntStateOf(0) }
    LaunchedEffect(stored.map { it.id }) {
        val used = slotOf.filterKeys { id -> stored.any { it.id == id } }.values.toMutableSet()
        slotOf.keys.retainAll(stored.map { it.id }.toSet())
        stored.forEach { ring ->
            if (ring.id !in slotOf) {
                val free = (0 until MAX_STORED).first { it !in used }
                slotOf[ring.id] = free
                used += free
            }
        }
    }
    LaunchedEffect(spins) {
        if (spins > 0) {
            delay(520)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    val mounted = rings.firstOrNull { it.id == mountedId }

    MetalPlate(modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            val w = maxWidth
            val mountSize = 232.dp
            val mountX = (w - mountSize) / 2
            val mountY = 84.dp
            val slotSize = 100.dp
            val rackTop = mountY + mountSize + 150.dp
            fun slotPos(slot: Int): Pair<Dp, Dp> {
                val col = slot % 3
                val row = slot / 3
                val x = w * ((col * 2 + 1) / 6f) - slotSize / 2
                return x to rackTop + (slotSize + 34.dp) * row
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetalKnob(onClick = onBack, contentDescription = "재생 화면으로", size = 52.dp) {
                    KnobIcon(R.drawable.arrow_back, size = 20.dp)
                }
                MetalKnob(onClick = onMore, contentDescription = "보관함과 설정", size = 52.dp) {
                    KnobIcon(R.drawable.tunes_menu, size = 20.dp)
                }
            }

            Hollow(
                Modifier
                    .offset(mountX - 4.dp, mountY - 4.dp)
                    .size(mountSize + 8.dp),
            )
            (0 until MAX_STORED).forEach { slot ->
                val (x, y) = slotPos(slot)
                Hollow(
                    Modifier
                        .offset(x - 3.dp, y - 3.dp)
                        .size(slotSize + 6.dp),
                )
            }

            Text(
                text = mounted?.name ?: "",
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = EngravedText).engraved(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .offset(y = mountY + mountSize + 16.dp),
            )
            Text(
                text = mounted?.let { "${it.songCount}곡" } ?: "",
                style = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp, color = EngravedSubText).engraved(),
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .offset(y = mountY + mountSize + 46.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .offset(y = mountY + mountSize + 72.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            ) {
                MetalKnob(onClick = onPlay, contentDescription = "끼운 링 재생", size = 56.dp) {
                    KnobIcon(R.drawable.play)
                }
                MetalKnob(onClick = onOpen, contentDescription = "링 안의 곡 보기", size = 56.dp) {
                    KnobIcon(R.drawable.queue_music)
                }
            }

            rings.forEach { ring ->
                val isMounted = ring.id == mountedId
                val slot = slotOf[ring.id]
                if (!isMounted && slot == null) return@forEach
                key(ring.id) {
                val (sx, sy) = if (slot != null) slotPos(slot) else mountX to mountY
                val bouncy = spring<Dp>(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)
                val x by animateDpAsState(if (isMounted) mountX else sx, bouncy, label = "ringX")
                val y by animateDpAsState(if (isMounted) mountY else sy, bouncy, label = "ringY")
                val size by animateDpAsState(if (isMounted) mountSize else slotSize, bouncy, label = "ringSize")
                val turn by animateFloatAsState(if (isMounted) spins * 360f else 0f, tween(900), label = "ringTurn")
                PlaylistRing(
                    coverUrl = ring.coverUrl,
                    lampOn = isMounted,
                    modifier =
                        Modifier
                            .offset(x, y)
                            .size(size)
                            .graphicsLayer { rotationZ = turn }
                            .shadow(if (isMounted) 16.dp else 6.dp, CircleShape)
                            .clip(CircleShape)
                            .semantics {
                                contentDescription = if (isMounted) "끼워진 재생목록: ${ring.name}" else "재생목록 링 끼우기: ${ring.name}"
                                role = Role.Button
                            }.clickable(enabled = !isMounted) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val leaving = mountedId
                                val freed = slotOf.remove(ring.id)
                                if (leaving != null && freed != null) slotOf[leaving] = freed
                                spins++
                                onMount(ring.id)
                            },
                )
                }
            }
        }
    }
}

private const val MAX_STORED = 6
