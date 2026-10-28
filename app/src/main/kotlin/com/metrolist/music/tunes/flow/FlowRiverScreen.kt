/**
 * 튠즈 흐름 · 물길: the next songs float toward you on a river drawn in perspective.
 * As a song plays, the next cover drifts closer; when it ends, it arrives in front of you.
 * Drag the far water left (calmer) or right (more intense) and the river bends; the songs
 * coming down it change. Flick the cover in front: left = not now, right = never again.
 * The water only moves while music plays.
 */

package com.metrolist.music.tunes.flow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.models.toMediaMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private class Card(val song: FlowSong?, val meta: MediaMetadata, val z: Float, val alpha: Float, val k: Int)

@Composable
fun FlowRiverScreen() {
    val context = LocalContext.current
    val pc = LocalPlayerConnection.current
    val db = LocalDatabase.current
    LaunchedEffect(pc, db) { FlowEngine.attach(context, pc, db) }

    val metaFlow = remember(pc) { pc?.mediaMetadata ?: MutableStateFlow<MediaMetadata?>(null) }
    val meta by metaFlow.collectAsState()
    val playingFlow = remember(pc) { pc?.isPlaying ?: MutableStateFlow(false) }
    val playing by playingFlow.collectAsState()

    val history by remember(db) { db.mostPlayedSongs(LocalDateTime.now().minusMonths(6), limit = 40) }
        .collectAsState(initial = emptyList())
    LaunchedEffect(history) { FlowEngine.prime(history.map { it.toMediaMetadata() }) }

    val pool by FlowEngine.pool.collectAsState()
    val plan by FlowEngine.plan.collectAsState()
    val progress by rememberProgress(pc, everyFrame = true)
    var bendTarget by remember { mutableFloatStateOf(0f) }
    val bend by animateFloatAsState(bendTarget, tween(700), label = "bend")
    var flick by remember { mutableFloatStateOf(0f) }
    var touched by remember { mutableStateOf(false) }

    // Keeps the covers gliding instead of jumping when the song changes
    val glide = remember { Animatable(0f) }
    val lastFraction = remember { floatArrayOf(0f) }
    LaunchedEffect(meta?.id) {
        FlowEngine.onCurrent(meta)
        glide.snapTo((1f - lastFraction[0]).coerceIn(0f, 1f))
        glide.animateTo(0f, tween(900))
    }
    lastFraction[0] = progress.fraction

    val isPlaying by rememberUpdatedState(playing)
    val flowT by produceState(0f) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L && isPlaying) value += (t - last) / 1_000_000_000f
                last = t
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(FlowColors.Water)) {
        val density = LocalDensity.current
        val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        val topPad = with(density) { insets.calculateTopPadding().toPx() }
        val bottomPad = with(density) { insets.calculateBottomPadding().toPx() }
        val w = with(density) { maxWidth.toPx() }
        val fullH = with(density) { maxHeight.toPx() }
        val h = fullH - topPad - bottomPad
        val wide = w > h * 1.1f
        val hor = topPad + h * (if (wide) 0.16f else 0.12f)
        val ky = h * 0.5f
        val kx = if (wide) h * 0.8f else w * 0.86f
        val base = 1.3f
        val gap = 2.6f
        val coverSize = if (wide) 0.6f else 0.78f
        fun xc(z: Float) = bend * (0.22f * max(0f, z - 1.4f) + 0.04f * max(0f, z - 1.4f).pow(2)) +
            0.32f * sin(z * 0.52f + 0.5f) * ((z - 1.2f) / 2f).coerceIn(0f, 1f)
        fun proj(z: Float, u: Float) = Offset(w / 2 + (xc(z) + u) * kx / z, hor + ky / z)

        val hue = 205f + (28f - 205f) * ((bend + 1f) / 2f)
        val sat = 0.10f + abs(bend) * 0.22f

        // --- water ---
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(0f to Color(0xFF090B0C), hor / size.height to Color(0xFF141A1C), 1f to Color(0xFF07090A)))
            val zs = generateSequence(0.42f) { it * 1.06f }.takeWhile { it < 34f }.toList()
            val river = Path().apply {
                zs.forEachIndexed { i, z -> val p = proj(z, -1.5f); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                zs.asReversed().forEach { z -> val p = proj(z, 1.5f); lineTo(p.x, p.y) }
                close()
            }
            drawPath(
                river,
                Brush.verticalGradient(
                    0f to Color.hsl(hue, sat, 0.16f), 1f to Color.hsl(hue, sat, 0.08f),
                    startY = hor, endY = size.height,
                ),
            )
            var u = -1.5f
            while (u <= 1.501f) {
                val edge = 1f - abs(u) / 1.6f
                val line = Path().apply {
                    zs.forEachIndexed { i, z -> val p = proj(z, u); if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                }
                drawPath(line, Color.hsl(hue, min(1f, sat + 0.08f), 0.8f, 0.04f + 0.09f * edge), style = Stroke(0.7.dp.toPx()))
                u += 0.15f
            }
            // Current marks drifting toward you
            val phase = (flowT * 0.55f) % 1f
            u = -1.35f
            while (u <= 1.36f) {
                val edge = 1f - abs(u) / 1.6f
                val shift = ((u * 7.3f) % 1f + 1f) % 1f
                for (j in 0 until 30) {
                    val z = 1.12f.pow(j + shift - phase) * 0.95f
                    if (z < 0.95f || z > 28f) continue
                    val a = proj(z, u)
                    val b = proj(z * 1.05f, u)
                    val alpha = 0.32f * edge * ((28f - z) / 14f).coerceIn(0f, 1f)
                    drawLine(Color.hsl(hue, min(1f, sat + 0.1f), 0.86f, alpha), a, b, 1.1.dp.toPx())
                }
                u += 0.3f
            }
            drawRect(
                Brush.verticalGradient(0f to Color(0x00141A1C), 0.35f to Color(0xD9141A1C), 1f to Color(0x00141A1C), startY = hor - 30f, endY = hor + 70f),
                topLeft = Offset(0f, hor - 30f), size = androidx.compose.ui.geometry.Size(size.width, 100f),
            )
        }

        // Steering: drag anywhere above the cover in front
        Box(
            Modifier
                .fillMaxWidth()
                .height(with(density) { (hor + ky / base - kx * coverSize / base).coerceAtLeast(0f).toDp() })
                .pointerInput(meta?.id) {
                    detectDragGestures(onDragStart = { touched = true }) { change, _ ->
                        change.consume()
                        val v = ((change.position.x - w / 2) / (w * 0.34f)).coerceIn(-1f, 1f)
                        val q = (v * 20).roundToInt() / 20f
                        if (q != bendTarget && meta != null) {
                            bendTarget = q
                            FlowEngine.steerEnergy(0.5f + 0.44f * q)
                        }
                    }
                },
        )

        // --- covers, far to near ---
        val m = meta
        val cards = mutableListOf<Card>()
        if (m != null) {
            val p = progress.fraction
            plan.take(5).forEachIndexed { i, id ->
                val s = pool[id] ?: return@forEachIndexed
                val z = base + (i + 1 - p + glide.value) * gap
                if (z < 30f) cards.add(Card(s, s.meta, z, ((30f - z) / 8f).coerceIn(0f, 1f), i + 1))
            }
            val zc = base + glide.value * gap - p * 0.38f
            cards.add(Card(pool[m.id], m, zc, 1f - ((p - 0.9f) / 0.1f).coerceIn(0f, 1f), 0))
        } else {
            pool.values.take(6).forEachIndexed { i, s ->
                cards.add(Card(s, s.meta, base + 0.6f + i * gap * 0.75f, 1f, i + 1))
            }
        }
        for (c in cards.sortedByDescending { it.z }) {
            val sizePx = coverSize * kx / c.z
            val at = proj(c.z, 0f)
            val sizeDp = with(density) { sizePx.toDp() }
            val left = (at.x - sizePx / 2).roundToInt()
            val top = (at.y - sizePx).roundToInt()
            // reflection
            Box(Modifier.offset { IntOffset(left, at.y.roundToInt()) }.graphicsLayer { alpha = c.alpha * 0.3f }) {
                AsyncImage(
                    model = c.meta.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop,
                    alignment = Alignment.BottomCenter,
                    modifier = Modifier.width(sizeDp).height(sizeDp * 0.62f).graphicsLayer { scaleY = -1f },
                )
                Box(
                    Modifier.width(sizeDp).height(sizeDp * 0.62f)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, FlowColors.Water))),
                )
            }
            if (c.k == 0) {
                FlickCover(
                    url = c.meta.thumbnailUrl,
                    size = sizeDp,
                    onFlick = { FlowEngine.skip(it) },
                    onDrag = { flick = it },
                    modifier = Modifier.offset { IntOffset(left, top) }.graphicsLayer { alpha = c.alpha },
                )
            } else {
                AsyncImage(
                    model = c.meta.thumbnailUrl, contentDescription = c.meta.title, contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .offset { IntOffset(left, top) }
                        .size(sizeDp)
                        .graphicsLayer { alpha = c.alpha }
                        .clip(RoundedCornerShape(2.dp))
                        .clickable {
                            if (m == null) FlowEngine.start(c.meta) else FlowEngine.playNow(c.meta.id)
                        },
                )
                if (c.k == 1 && c.alpha > 0.3f) {
                    Text(
                        (if (m == null) "" else "다음  ") + c.meta.title,
                        color = FlowColors.Foam.copy(alpha = 0.6f * c.alpha), fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .offset { IntOffset((at.x - 90.dp.toPx()).roundToInt(), (at.y + sizePx * 0.62f + 6.dp.toPx()).roundToInt()) }
                            .width(180.dp),
                    )
                }
            }
        }

        // --- banks, labels, now playing ---
        val bankY = with(density) { (hor - 26.dp.toPx()).toDp() }
        Text(
            "← 잔잔", color = FlowColors.Foam.copy(alpha = 0.38f + 0.62f * max(0f, -bend)), fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.offset(x = 22.dp, y = bankY),
        )
        Text(
            "강렬 →", color = FlowColors.Foam.copy(alpha = 0.38f + 0.62f * max(0f, bend)), fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.align(Alignment.TopEnd).offset(x = (-22).dp, y = bankY),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(insets)
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        ) {
            if (!touched) {
                Text(
                    "위쪽 물길을 좌우로 끌기 · 앞의 표지를 옆으로 튕기기",
                    color = FlowColors.Foam.copy(alpha = 0.42f), fontSize = 10.5.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
            }
            if (m != null) {
                val label = flickLabel(flick)
                Text(
                    label ?: m.title, color = FlowColors.Foam, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (label == null) {
                    Text(m.artists.joinToString { it.name }, color = FlowColors.Foam.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1)
                }
                Row(Modifier.padding(top = 8.dp)) {
                    Text(
                        "${fmtTime(progress.position)} / ${fmtTime(progress.duration)}",
                        color = FlowColors.Foam.copy(alpha = 0.5f), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    )
                }
            } else {
                Text("떠 있는 표지를 눌러 흐름을 시작하세요", color = FlowColors.Foam, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("최근 6개월 동안 많이 들은 곡이에요", color = FlowColors.Foam.copy(alpha = 0.6f), fontSize = 13.sp)
            }
        }
    }
}
