/*
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * Shared-element "一镜到底" cover transition.
 *
 * The mini cover and the full cover both live inside the BottomSheet's
 * sliding container, so during a drag their window-space bounds move together
 * (they share the same panel offset). We draw one overlay cover that
 * interpolates between the two LIVE rectangles — position, size, corner radius
 * and elevation — driven by BottomSheetState.progress. Because both
 * endpoints carry the same panel offset, the overlay rides the panel naturally
 * while the cover grows from mini to full, giving the continuous hero-cover
 * flight without a separate navigation-based shared-element library.
 */

package com.dd3boh.outertune.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.ui.component.BottomSheetState
import kotlin.math.roundToInt

@Stable
class SharedCoverState {
    /** Live window bounds of the mini cover, set by onGloballyPositioned. */
    var miniRect by mutableStateOf<Rect?>(null)

    /** Live window bounds of the full cover, set by onGloballyPositioned. */
    var fullRect by mutableStateOf<Rect?>(null)

    /** 0f = collapsed, 1f = expanded. */
    var progress by mutableFloatStateOf(0f)
}

val LocalSharedCoverState = compositionLocalOf<SharedCoverState?> { null }

@Composable
fun SharedCoverHost(
    state: BottomSheetState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val sharedCoverState = remember { SharedCoverState() }
    SideEffect {
        sharedCoverState.progress = state.progress
    }

    Box(modifier = modifier) {
        CompositionLocalProvider(LocalSharedCoverState provides sharedCoverState) {
            content()
        }
        SharedCoverOverlay(sharedCoverState = sharedCoverState)
    }
}

@Composable
private fun SharedCoverOverlay(sharedCoverState: SharedCoverState) {
    val mini = sharedCoverState.miniRect ?: return
    val full = sharedCoverState.fullRect ?: return
    val raw = sharedCoverState.progress
    if (raw <= 0.01f || raw >= 0.99f) return

    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val density = LocalDensity.current

    // Ease-out quad: burst out of the mini slot, decelerate into the full slot.
    val p = 1f - (1f - raw) * (1f - raw)

    val miniCornerPx = with(density) { 8.dp.toPx() }
    val fullCornerPx = with(density) { 24.dp.toPx() }
    val miniElevPx = with(density) { 0.dp.toPx() }
    val fullElevPx = with(density) { 24.dp.toPx() }

    val left = lerp(mini.left, full.left, p)
    val top = lerp(mini.top, full.top, p)
    val sizePx = lerp(mini.width, full.width, p)
    val cornerPx = lerp(miniCornerPx, fullCornerPx, p)
    val elevPx = lerp(miniElevPx, fullElevPx, p)

    val cornerDp = with(density) { cornerPx.toDp() }
    val elevDp = with(density) { elevPx.toDp() }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = mediaMetadata?.getThumbnailModel(),
            contentDescription = null,
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(with(density) { sizePx.toDp() })
                .shadow(
                    elevation = elevDp,
                    shape = RoundedCornerShape(cornerDp),
                    clip = false,
                )
                .clip(RoundedCornerShape(cornerDp))
        )
    }
}
