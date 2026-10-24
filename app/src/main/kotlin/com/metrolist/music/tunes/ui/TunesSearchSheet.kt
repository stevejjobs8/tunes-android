/**
 * 튠즈 (Metrolist fork) — search as an aluminum sheet that slides up over the player.
 * Tapping a result hands its cover's position to the caller, which flies it into the ring.
 * Long-press a result and drag it onto one of the playlist rings in the tray to add it there.
 */

package com.metrolist.music.tunes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.metrolist.music.R
import kotlin.math.roundToInt

data class SearchHit(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String?,
)

@Composable
fun TunesSearchSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    hits: List<SearchHit>,
    searching: Boolean,
    rings: List<RingItem>,
    onPick: (hit: SearchHit, coverBounds: Rect) -> Unit,
    onDropOnRing: (hit: SearchHit, ring: RingItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { focus.requestFocus() } }

    var sheetOrigin by remember { mutableStateOf(Offset.Zero) }
    // While a result is held: what is held, and where the finger is, in root coordinates.
    var held by remember { mutableStateOf<SearchHit?>(null) }
    var fingerAt by remember { mutableStateOf(Offset.Zero) }
    val ringBounds = remember { mutableStateMapOf<String, Rect>() }
    val hovered = held?.let { rings.firstOrNull { ring -> ringBounds[ring.id]?.contains(fingerAt) == true } }
    LaunchedEffect(hovered?.id) { if (hovered != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    val close by rememberUpdatedState(onClose)

    MetalPlate(
        modifier
            .fillMaxSize()
            .shadow(24.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            // Keep touches on the sheet from reaching the player underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .onGloballyPositioned { sheetOrigin = it.positionInRoot() },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Handle: tap or pull down to put the sheet away.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .semantics {
                        contentDescription = "검색 닫기"
                        role = Role.Button
                    }.clickable(onClick = onClose)
                    .pointerInput(Unit) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total > 60.dp.toPx()) close() },
                        ) { _, dy -> total += dy }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(44.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF4A5056)),
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EngravedField(
                    value = query,
                    onValueChange = onQueryChange,
                    onSubmit = {
                        keyboard?.hide()
                        onSubmit()
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .focusRequester(focus),
                )
                MetalKnob(
                    onClick = {
                        keyboard?.hide()
                        onSubmit()
                    },
                    contentDescription = "검색",
                    size = 52.dp,
                ) { KnobIcon(R.drawable.search, size = 22.dp) }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(hits, key = { it.id }) { hit ->
                        HitRow(
                            hit = hit,
                            dimmed = held?.id == hit.id,
                            onPick = { bounds ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                keyboard?.hide()
                                onPick(hit, bounds)
                            },
                            onHold = { at ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                keyboard?.hide()
                                held = hit
                                fingerAt = at
                            },
                            onMove = { fingerAt = it },
                            onRelease = {
                                val target = hovered
                                val hit0 = held
                                held = null
                                if (target != null && hit0 != null) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDropOnRing(hit0, target)
                                }
                            },
                        )
                    }
                }
                if (searching) {
                    MetalLamp(on = true, modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
                }
            }

            // Ring tray: appears while a result is held, as drop targets.
            AnimatedVisibility(
                visible = held != null && rings.isNotEmpty(),
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    rings.forEach { ring ->
                        val over = hovered?.id == ring.id
                        val scale by animateFloatAsState(if (over) 1.18f else 1f, label = "trayRing")
                        PlaylistRing(
                            coverUrl = ring.coverUrl,
                            lampOn = over,
                            modifier =
                                Modifier
                                    .size(72.dp)
                                    .onGloballyPositioned { ringBounds[ring.id] = it.boundsInRoot() }
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }.shadow(if (over) 12.dp else 5.dp, CircleShape)
                                    .semantics { contentDescription = "재생목록 링: ${ring.name}" },
                        )
                    }
                }
            }
        }

        // The held cover follows the finger.
        held?.let { hit ->
            val half = with(LocalDensity.current) { 32.dp.toPx() }
            PlaylistRing(
                coverUrl = hit.coverUrl,
                lampOn = false,
                modifier =
                    Modifier
                        .offset {
                            IntOffset(
                                (fingerAt.x - sheetOrigin.x - half).roundToInt(),
                                (fingerAt.y - sheetOrigin.y - half * 2.4f).roundToInt(),
                            )
                        }.size(64.dp)
                        .shadow(14.dp, CircleShape),
            )
        }
    }
}

@Composable
private fun EngravedField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF8E959B), Color(0xFFC9CED2))))
            .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(26.dp))
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium, color = EngravedText).engraved(),
            cursorBrush = SolidColor(TunesLamp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "검색어" },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HitRow(
    hit: SearchHit,
    dimmed: Boolean,
    onPick: (Rect) -> Unit,
    onHold: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onRelease: () -> Unit,
) {
    var coverBounds by remember { mutableStateOf(Rect.Zero) }
    var rowOrigin by remember { mutableStateOf(Offset.Zero) }
    Row(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowOrigin = it.positionInRoot() }
            .pointerInput(hit.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onHold(rowOrigin + it) },
                    onDragEnd = onRelease,
                    onDragCancel = onRelease,
                ) { change, _ ->
                    change.consume()
                    onMove(rowOrigin + change.position)
                }
            }.semantics {
                contentDescription = "${hit.title}, ${hit.artist} 재생"
                role = Role.Button
            }.combinedClickable(onLongClick = {}) { onPick(coverBounds) }
            .graphicsLayer { alpha = if (dimmed) 0.35f else 1f }
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistRing(
            coverUrl = hit.coverUrl,
            lampOn = false,
            modifier =
                Modifier
                    .size(60.dp)
                    .shadow(5.dp, CircleShape)
                    .onGloballyPositioned { coverBounds = it.boundsInRoot() },
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                hit.title,
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = EngravedText).engraved(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                hit.artist,
                style = TextStyle(fontSize = 13.sp, color = EngravedSubText).engraved(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
