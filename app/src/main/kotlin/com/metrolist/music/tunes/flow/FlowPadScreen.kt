/**
 * 튠즈 흐름 · 패드: every candidate song sits on a grid (up = intense, right = never heard).
 * Drag the crosshair and the path from the current song to it is redrawn; the numbered
 * songs on that path play next. Tap a cover on the grid to play it now.
 */

package com.metrolist.music.tunes.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun FlowPadScreen() {
    val context = LocalContext.current
    val pc = LocalPlayerConnection.current
    val db = LocalDatabase.current
    LaunchedEffect(pc, db) { FlowEngine.attach(context, pc, db) }

    val metaFlow = remember(pc) { pc?.mediaMetadata ?: MutableStateFlow<MediaMetadata?>(null) }
    val meta by metaFlow.collectAsState()
    LaunchedEffect(meta?.id) { FlowEngine.onCurrent(meta) }

    val history by remember(db) { db.mostPlayedSongs(LocalDateTime.now().minusMonths(6), limit = 40) }
        .collectAsState(initial = emptyList())
    LaunchedEffect(history) { FlowEngine.prime(history.map { it.toMediaMetadata() }) }

    val pool by FlowEngine.pool.collectAsState()
    val plan by FlowEngine.plan.collectAsState()
    val target by FlowEngine.target.collectAsState()
    val loading by FlowEngine.loading.collectAsState()
    val progress by rememberProgress(pc)
    val accent by rememberAccent(meta?.thumbnailUrl, Color(0xFFB4532A))
    var flick by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier
            .fillMaxSize()
            .background(FlowColors.Paper)
            .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val m = meta
        if (m != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                FlickCover(
                    url = m.thumbnailUrl,
                    size = 60.dp,
                    onFlick = { FlowEngine.skip(it) },
                    onDrag = { flick = it },
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val label = flickLabel(flick)
                    if (label != null) {
                        Text(label, color = FlowColors.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    } else {
                        Text(m.title, color = FlowColors.Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(m.artists.joinToString { it.name }, color = FlowColors.Soft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(
                    "${fmtTime(progress.position)}\n${fmtTime(progress.duration)}",
                    color = FlowColors.Soft, fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp,
                )
            }
            Ruler(progress.fraction, accent)
        } else {
            Text(
                "지도에서 시작할 곡을 누르세요",
                color = FlowColors.Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "점은 최근 6개월 동안 많이 들은 곡이에요. 위로 갈수록 강렬하고, 오른쪽일수록 덜 들은 곡이에요.",
                color = FlowColors.Soft, fontSize = 13.sp, lineHeight = 19.sp,
            )
        }

        FlowPad(
            pool = pool,
            currentId = m?.id,
            plan = if (m != null) plan else emptyList(),
            target = if (m != null) target ?: FlowEngine.currentSong()?.let { FlowTarget(it.energy, it.novelty) } else null,
            accent = accent,
            progress = progress.fraction,
            onSteer = { if (m != null) FlowEngine.steer(it) },
            onTap = { id -> if (m == null) pool[id]?.let { FlowEngine.start(it.meta) } else FlowEngine.playNow(id) },
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).align(Alignment.CenterHorizontally),
        )

        if (m != null) {
            if (loading && plan.isEmpty()) {
                Text("다음 곡 후보를 모으는 중…", color = FlowColors.Soft, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            Column {
                plan.take(4).forEachIndexed { i, id ->
                    val s = pool[id] ?: return@forEachIndexed
                    NextRow(i + 1, s)
                }
            }
        }
    }
}

@Composable
private fun Ruler(fraction: Float, accent: Color) {
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val step = 7.dp.toPx()
        var x = 0f
        val filled = size.width * fraction
        while (x < size.width) {
            drawLine(
                if (x <= filled) accent else FlowColors.Ink.copy(alpha = 0.18f),
                Offset(x, 0f), Offset(x, size.height - 1f), strokeWidth = 1f,
            )
            x += step
        }
        drawLine(FlowColors.Ink.copy(alpha = 0.22f), Offset(0f, size.height), Offset(size.width, size.height), 1f)
        drawLine(accent, Offset(filled, -3.dp.toPx()), Offset(filled, size.height), 2.dp.toPx())
    }
}

@Composable
private fun NextRow(k: Int, s: FlowSong) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Text("$k", color = FlowColors.Soft, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(18.dp))
        AsyncImage(
            model = s.meta.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(s.meta.title, color = FlowColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.meta.artists.joinToString { it.name }, color = FlowColors.Soft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (s.novelty > 0.55f) {
            Text(
                "처음", color = FlowColors.Soft, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                modifier = Modifier.border(1.dp, FlowColors.Soft, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        EnergyBars(s.energy, FlowColors.Ink)
    }
}

@Composable
fun EnergyBars(energy: Float, color: Color) {
    val on = (energy * 5).roundToInt().coerceIn(1, 5)
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(12.dp)) {
        for (i in 0 until 5) {
            Box(Modifier.width(2.dp).height((4 + i * 2).dp).background(color.copy(alpha = if (i < on) 0.85f else 0.2f)))
        }
    }
}

@Composable
private fun FlowPad(
    pool: Map<String, FlowSong>,
    currentId: String?,
    plan: List<String>,
    target: FlowTarget?,
    accent: Color,
    progress: Float,
    onSteer: (FlowTarget) -> Unit,
    onTap: (String) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(
        modifier
            .aspectRatio(1f)
            .background(FlowColors.Paper2)
            .border(1.dp, FlowColors.Line),
    ) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val margin = w * 0.07f
        fun pos(e: Float, n: Float) = Offset(margin + (w - 2 * margin) * n, margin + (w - 2 * margin) * (1 - e))
        fun toTarget(o: Offset) = FlowTarget(1 - (o.y - margin) / (w - 2 * margin), (o.x - margin) / (w - 2 * margin))
        val order = plan.withIndex().associate { it.value to it.index + 1 }
        val cur = currentId?.let { pool[it] }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(pool, currentId) {
                    detectTapGestures { o ->
                        val hit = pool.values.minByOrNull { val p = pos(it.energy, it.novelty); hypot(p.x - o.x, p.y - o.y) }
                        val p = hit?.let { pos(it.energy, it.novelty) }
                        if (hit != null && p != null && hypot(p.x - o.x, p.y - o.y) < 22.dp.toPx()) onTap(hit.meta.id)
                        else onSteer(toTarget(o))
                    }
                }
                .pointerInput(currentId) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onSteer(toTarget(change.position))
                    }
                },
        ) {
            for (i in 1 until 20) {
                val v = i * size.width / 20
                val a = if (i % 5 == 0) 0.13f else 0.05f
                val sw = if (i == 10) 1.dp.toPx() else 0.7f
                drawLine(FlowColors.Ink.copy(alpha = a), Offset(v, 0f), Offset(v, size.height), sw)
                drawLine(FlowColors.Ink.copy(alpha = a), Offset(0f, v), Offset(size.width, v), sw)
            }
            if (cur != null) {
                val pts = listOf(pos(cur.energy, cur.novelty)) + plan.mapNotNull { id -> pool[id]?.let { pos(it.energy, it.novelty) } }
                if (pts.size > 1) {
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 0 until pts.size - 1) {
                            val a = pts.getOrElse(i - 1) { pts[i] }
                            val b = pts[i]
                            val c = pts[i + 1]
                            val d = pts.getOrElse(i + 2) { c }
                            cubicTo(
                                b.x + (c.x - a.x) / 6, b.y + (c.y - a.y) / 6,
                                c.x - (d.x - b.x) / 6, c.y - (d.y - b.y) / 6,
                                c.x, c.y,
                            )
                        }
                    }
                    drawPath(path, accent, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
                }
                if (target != null) {
                    val last = pts.last()
                    drawLine(
                        FlowColors.Ink.copy(alpha = 0.45f), last, pos(target.energy, target.novelty), 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
                    )
                }
                val c = pos(cur.energy, cur.novelty)
                val r = 25.dp.toPx()
                drawArc(
                    accent, -90f, 360f * progress, false,
                    topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        val faded = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.25f) }) }
        // Faint songs first, so the numbered path sits on top
        val ordered = pool.values.sortedBy { if (it.meta.id == currentId) 2 else if (it.meta.id in order) 1 else 0 }
        for (s in ordered) {
            val isCur = s.meta.id == currentId
            val k = order[s.meta.id]
            val sz = when {
                isCur -> 36.dp
                k != null -> 24.dp
                currentId == null -> 22.dp
                else -> 15.dp
            }
            val p = pos(s.energy, s.novelty)
            val half = with(density) { sz.toPx() / 2 }
            Box(Modifier.offset { IntOffset((p.x - half).roundToInt(), (p.y - half).roundToInt()) }) {
                AsyncImage(
                    model = s.meta.thumbnailUrl,
                    contentDescription = s.meta.title,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (isCur || k != null || currentId == null) null else faded,
                    modifier = Modifier
                        .size(sz)
                        .graphicsLayer { alpha = if (isCur || k != null || currentId == null) 1f else 0.38f }
                        .then(if (isCur) Modifier.border(2.dp, FlowColors.Paper2) else Modifier)
                        .clip(RoundedCornerShape(2.dp)),
                )
                if (k != null) {
                    Box(
                        Modifier
                            .offset(x = sz - 8.dp, y = (-7).dp)
                            .size(15.dp)
                            .background(FlowColors.Ink, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("$k", color = FlowColors.Paper2, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        if (target != null) {
            val t = pos(target.energy, target.novelty)
            val half = with(density) { 17.dp.toPx() }
            Canvas(
                Modifier
                    .offset { IntOffset((t.x - half).roundToInt(), (t.y - half).roundToInt()) }
                    .size(34.dp),
            ) {
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(FlowColors.Paper2.copy(alpha = 0.55f), size.width / 2)
                drawCircle(FlowColors.Ink, size.width / 2 - 1f, style = Stroke(1.5.dp.toPx()))
                drawLine(FlowColors.Ink, Offset(c.x, -6.dp.toPx()), Offset(c.x, size.height + 6.dp.toPx()), 1.5.dp.toPx())
                drawLine(FlowColors.Ink, Offset(-6.dp.toPx(), c.y), Offset(size.width + 6.dp.toPx(), c.y), 1.5.dp.toPx())
            }
        }

        AxisLabel("↑ 강렬", Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
        AxisLabel("↓ 잔잔", Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
        AxisLabel("← 익숙", Modifier.align(Alignment.CenterStart).padding(start = 8.dp))
        AxisLabel("처음 →", Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
    }
}

@Composable
private fun AxisLabel(text: String, modifier: Modifier) {
    Text(text, color = FlowColors.Soft, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = modifier)
}
