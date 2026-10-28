/**
 * 튠즈 흐름: pieces shared by the pad and river screens.
 */

package com.metrolist.music.tunes.flow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sign

object FlowColors {
    val Paper = Color(0xFFE6E7E2)
    val Paper2 = Color(0xFFEEEFEA)
    val Ink = Color(0xFF141517)
    val Soft = Color(0x8F141517)
    val Faint = Color(0x17141517)
    val Line = Color(0x29141517)
    val Water = Color(0xFF0A0C0D)
    val Foam = Color(0xFFE8EBE9)
}

data class Progress(val position: Long, val duration: Long) {
    val fraction: Float get() = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
}

/** Current position; polls every frame when [everyFrame], else four times a second. */
@Composable
fun rememberProgress(pc: PlayerConnection?, everyFrame: Boolean = false): State<Progress> =
    produceState(Progress(0, 0), pc) {
        while (true) {
            value = runCatching {
                val p = pc!!.player
                Progress(p.currentPosition, p.duration.coerceAtLeast(0))
            }.getOrDefault(Progress(0, 0))
            if (everyFrame) withFrameNanos { } else delay(250)
        }
    }

/** The cover's main colour, used for the path and the progress marks. */
@Composable
fun rememberAccent(url: String?, fallback: Color): State<Color> {
    val context = LocalContext.current
    return produceState(fallback, url) {
        if (url == null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context).data(url).allowHardware(false).build(),
                )
                val bmp = result.image?.toBitmap() ?: return@runCatching fallback
                val p = Palette.from(bmp).generate()
                val rgb = p.getVibrantColor(p.getDominantColor(0))
                if (rgb == 0) fallback else Color(rgb)
            }.getOrDefault(fallback)
        }
    }
}

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/**
 * A cover you can flick: left = "not now", right = "never again".
 * [onDrag] reports the offset so the screen can show which reason will apply.
 */
@Composable
fun FlickCover(
    url: String?,
    size: Dp,
    onFlick: (SkipReason) -> Unit,
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { size.toPx() * 0.7f }
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                translationX = offset.value
                rotationZ = offset.value * 0.06f
                alpha = 1f - (abs(offset.value) / (threshold * 4f)).coerceIn(0f, 0.85f)
            }
            .pointerInput(url) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            val v = offset.value
                            if (abs(v) > threshold) {
                                offset.animateTo(sign(v) * threshold * 5f, tween(220))
                                onFlick(if (v < 0) SkipReason.LATER else SkipReason.NEVER)
                                offset.snapTo(0f)
                            } else {
                                offset.animateTo(0f, spring())
                            }
                            onDrag(0f)
                        }
                    },
                    onDragCancel = {
                        scope.launch { offset.animateTo(0f, spring()) }
                        onDrag(0f)
                    },
                ) { change, amount ->
                    change.consume()
                    scope.launch { offset.snapTo(offset.value + amount) }
                    onDrag(offset.value + amount)
                }
            },
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(2)),
        )
    }
}

/** Label for a flick in progress, or null when the cover is at rest. */
fun flickLabel(dx: Float): String? = when {
    dx < -24f -> "← 지금은 아님"
    dx > 24f -> "다시 안 틀기 →"
    else -> null
}
