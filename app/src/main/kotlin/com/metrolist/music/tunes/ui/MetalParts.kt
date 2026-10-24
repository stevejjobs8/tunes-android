/**
 * 튠즈 (Metrolist fork) — brushed/spun steel UI parts.
 * Plain parameters only, so these do not depend on Metrolist's player or database.
 */

package com.metrolist.music.tunes.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import kotlin.math.roundToInt

val TunesLamp = Color(0xFFE5484D)
val TunesLampOff = Color(0xFF3A3F44)
val EngravedText = Color(0xFF1E2226)
val EngravedSubText = Color(0xFF3E4449)

/** Text looks stamped into the plate: a 1px light edge under the glyphs. */
val EngravedShadow = Shadow(color = Color.White.copy(alpha = 0.7f), offset = Offset(0f, 1.5f), blurRadius = 0f)

fun TextStyle.engraved() = copy(shadow = EngravedShadow)

/** Brushed aluminum background. */
@Composable
fun MetalPlate(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        Image(
            painter = painterResource(R.drawable.tunes_plate),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        content()
    }
}

/** Round spun-steel button. Icon-only, so [contentDescription] is required. */
@Composable
fun MetalKnob(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    icon: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier =
            modifier
                .size(size)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .semantics {
                    this.contentDescription = contentDescription
                    role = Role.Button
                }.clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.tunes_knob),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        icon()
    }
}

@Composable
fun KnobIcon(iconRes: Int, size: Dp = 22.dp) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = EngravedText,
        modifier = Modifier.size(size),
    )
}

/** Small lamp set into the metal; glows when [on]. */
@Composable
fun MetalLamp(
    on: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 9.dp,
    color: Color = TunesLamp,
) {
    Box(
        modifier
            .size(size)
            .shadow(if (on) 6.dp else 0.dp, CircleShape, ambientColor = color, spotColor = color)
            .clip(CircleShape)
            .background(if (on) color else TunesLampOff),
    )
}

/**
 * Album cover set in a spun-steel ring. The cover turns by [rotationDeg]
 * (callers derive it from the playback position, so seeking turns it too).
 */
@Composable
fun SpinningCoverRing(
    coverUrl: String?,
    rotationDeg: Float,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    ringSize: Dp = 260.dp,
) {
    val haptic = LocalHapticFeedback.current
    val playIconAlpha by animateFloatAsState(if (isPlaying) 0f else 1f, tween(300), label = "playIcon")
    Box(modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .shadow(18.dp, CircleShape)
                .background(Color(0xFF8A9096), CircleShape),
        )
        Image(
            painter = painterResource(R.drawable.tunes_ring),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize(0.86f)
                .clip(CircleShape)
                .background(Color(0xFF16191C))
                .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize(0.836f)
                    .clip(CircleShape)
                    .semantics {
                        contentDescription = if (isPlaying) "일시정지" else "재생"
                        role = Role.Button
                    }.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = rotationDeg },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color(0xFF5A636D), Color(0xFF1A1D20)))),
                )
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0.78f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.5f),
                        ),
                    ),
            )
            Box(
                Modifier
                    .size(64.dp)
                    .alpha(playIconAlpha)
                    .clip(CircleShape)
                    .background(Color(0x8C0C0E10)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = Color(0xFFEEF1F4),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        MetalLamp(
            on = isPlaying,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 6.dp),
        )
    }
}

/** Grooved steel track with a knob thumb. [onScrub] while dragging, [onSeek] on release or tap. */
@Composable
fun MetalSeekBar(
    positionMs: Long,
    durationMs: Long,
    onScrub: (Long) -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    var widthPx by remember { mutableIntStateOf(0) }
    val thumb = 28.dp
    val thumbPx = with(LocalDensity.current) { thumb.toPx() }

    fun positionAt(x: Float): Long {
        val usable = (widthPx - thumbPx).coerceAtLeast(1f)
        return (((x - thumbPx / 2) / usable).coerceIn(0f, 1f) * durationMs).toLong()
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(32.dp)
                .onSizeChanged { widthPx = it.width }
                .pointerInput(durationMs, widthPx) {
                    detectTapGestures { onSeek(positionAt(it.x)) }
                }.pointerInput(durationMs, widthPx) {
                    var last = 0L
                    detectHorizontalDragGestures(
                        onDragStart = {
                            last = positionAt(it.x)
                            onScrub(last)
                        },
                        onDragEnd = { onSeek(last) },
                        onDragCancel = { onSeek(last) },
                    ) { change, _ ->
                        change.consume()
                        last = positionAt(change.position.x)
                        onScrub(last)
                    }
                }.semantics {
                    contentDescription = "재생 위치"
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(9.dp)
                .padding(horizontal = thumb / 2),
        ) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(color = Color.White.copy(alpha = 0.8f), topLeft = Offset(0f, 1.dp.toPx()), cornerRadius = r)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF2A2E33), Color(0xFF6E747A))),
                size = size.copy(height = size.height - 1.dp.toPx()),
                cornerRadius = r,
            )
        }
        Image(
            painter = painterResource(R.drawable.tunes_knob),
            contentDescription = null,
            modifier =
                Modifier
                    .offset { IntOffset(((widthPx - thumbPx) * fraction).roundToInt(), 0) }
                    .size(thumb)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape),
        )
    }
}

fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}
