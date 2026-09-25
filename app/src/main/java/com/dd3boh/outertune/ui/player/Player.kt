/*
 * Copyright (C) 2024 z-huang/InnerTune
 * Copyright (C) 2025 OuterTune Project
 *
 * SPDX-License-Identifier: GPL-3.0
 *
 * For any other attributions, refer to the git commit history
 */

package com.dd3boh.outertune.ui.player

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.media3.common.C
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.dd3boh.outertune.LocalMenuState
import com.dd3boh.outertune.LocalPlayerConnection
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.DEFAULT_PLAYER_BACKGROUND
import com.dd3boh.outertune.constants.DarkMode
import com.dd3boh.outertune.constants.DarkModeKey
import com.dd3boh.outertune.constants.KeepScreenOn
import com.dd3boh.outertune.constants.KeepScreenOnKey
import com.dd3boh.outertune.constants.PlayerBackgroundStyle
import com.dd3boh.outertune.constants.PlayerBackgroundStyleKey
import com.dd3boh.outertune.constants.PlayerHorizontalPadding
import com.dd3boh.outertune.constants.QueuePeekHeight
import com.dd3boh.outertune.constants.SeekIncrement
import com.dd3boh.outertune.constants.SeekIncrementKey
import com.dd3boh.outertune.constants.ShowLyricsKey
import com.dd3boh.outertune.constants.SwipeToSkipKey
import com.dd3boh.outertune.extensions.isPowerSaver
import com.dd3boh.outertune.extensions.metadata
import com.dd3boh.outertune.extensions.supportsWideScreen
import com.dd3boh.outertune.extensions.tabMode
import com.dd3boh.outertune.extensions.togglePlayPause
import com.dd3boh.outertune.extensions.toggleRepeatMode
import com.dd3boh.outertune.playback.PlayerConnection
import com.dd3boh.outertune.playback.QueueBoard
import com.dd3boh.outertune.ui.component.BottomSheet
import com.dd3boh.outertune.ui.component.BottomSheetState
import com.dd3boh.outertune.ui.component.PlayerSliderTrack
import com.dd3boh.outertune.ui.component.button.ResizableIconButton
import com.dd3boh.outertune.ui.component.collapsedAnchor
import com.dd3boh.outertune.ui.component.dismissedAnchor
import com.dd3boh.outertune.ui.component.rememberBottomSheetState
import com.dd3boh.outertune.ui.menu.PlayerMenu
import com.dd3boh.outertune.ui.theme.extractGradientColors
import com.dd3boh.outertune.ui.utils.SnapLayoutInfoProvider
import com.dd3boh.outertune.utils.coilCoroutine
import com.dd3boh.outertune.utils.makeTimeString
import com.dd3boh.outertune.utils.rememberEnumPreference
import com.dd3boh.outertune.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.max

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val TAG = "BottomSheetPlayer"
    Log.v(TAG, "PLR-1")

    val context = LocalContext.current
    val currentView = LocalView.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val queueBoard by playerConnection.service.queueBoard.collectAsState()

    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = DEFAULT_PLAYER_BACKGROUND
    )

    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val isSystemInDarkTheme = isSystemInDarkTheme()
    val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }

    val keepScreenOn by rememberEnumPreference(
        key = KeepScreenOnKey,
        defaultValue = KeepScreenOn.LYRICS
    )
    val showLyrics by rememberPreference(ShowLyricsKey, defaultValue = false)

    val qbInit by playerConnection.service.qbInit.collectAsState()

    LaunchedEffect(qbInit, queueBoard.masterQueues.toList()) {
        Log.d(TAG, "Queues changed. qbInit = $qbInit")
        if (qbInit && !queueBoard.masterQueues.isEmpty() && state.isDismissed) {
            Log.d(TAG, "Triggering sheet collapseSoft")
            state.collapseSoft()
        }
    }


    SharedCoverHost(
        state = state,
        modifier = modifier,
    ) {
    BottomSheet(
        state = state,
        background = {
            PlayerBackground(
                playerConnection = playerConnection,
                playerBackground = playerBackground,
                showLyrics = showLyrics,
                useDarkTheme = useDarkTheme,
            )
        },
        collapsedBackgroundColor = Color.Transparent,
        onDismiss = {
            playerConnection.softKillPlayer()
        },
        collapsedContent = {
            MiniPlayer()
        }
    ) {
        Log.v(TAG, "PLR-3.0")

        val isPlaying by playerConnection.isPlaying.collectAsState()

        DisposableEffect(isPlaying, keepScreenOn) {
            currentView.keepScreenOn = isPlaying && keepScreenOn == KeepScreenOn.PLAYER
            onDispose {
                currentView.keepScreenOn = false
            }
        }

        if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE && !context.tabMode() && context.supportsWideScreen()) {
            LandscapePlayer(state, navController, queueBoard)
        } else {
            PortraitPlayer(state, navController, queueBoard)
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PortraitPlayer(
    playerSheetState: BottomSheetState,
    navController: NavController,
    queueBoard: QueueBoard,
    enableQueueSheet: Boolean = true,
    windowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
) {
    val TAG = "BottomSheetPlayer"
    Log.v(TAG, "PLR-3.1b")

    val playerConnection = LocalPlayerConnection.current ?: return

    val dismissedBound = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    val queueSheetState = rememberBottomSheetState(
        dismissedBound = dismissedBound,
        expandedBound = playerSheetState.expandedBound,
        collapsedBound = dismissedBound + (QueuePeekHeight * 1.2f),
        initialAnchor = collapsedAnchor,
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
            .padding(bottom = queueSheetState.collapsedBound)
    ) {
        // Top row: title + artist on left, cast icon on right.
        // Staggers in mid-transition, sliding up from below.
        val titleProgress = ((playerSheetState.progress - 0.30f) / 0.35f).coerceIn(0f, 1f)
        PlayerTopBar(
            modifier = Modifier.graphicsLayer {
                alpha = titleProgress
                translationY = (1f - titleProgress) * 40.dp.toPx()
            }
        )

        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .nestedScroll(playerSheetState.preUpPostDownNestedScrollConnection)
        ) {
            Log.v(TAG, "PLR-3.2b")
            val mediaMetadata by playerConnection.mediaMetadata.collectAsState()


            val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
            val canSkipNext by playerConnection.canSkipNext.collectAsState()

            val swipeToSkip by rememberPreference(SwipeToSkipKey, defaultValue = false)
            val previousMediaMetadata = if (swipeToSkip && playerConnection.player.hasPreviousMediaItem()) {
                val previousIndex = playerConnection.player.previousMediaItemIndex
                playerConnection.player.getMediaItemAt(previousIndex).metadata
            } else null


            val nextMediaMetadata = if (swipeToSkip && playerConnection.player.hasNextMediaItem()) {
                val nextIndex = playerConnection.player.nextMediaItemIndex
                playerConnection.player.getMediaItemAt(nextIndex).metadata
            } else null

            val mediaItems = listOfNotNull(previousMediaMetadata, mediaMetadata, nextMediaMetadata)
            val currentMediaIndex = mediaItems.indexOf(mediaMetadata)


            var sliderPosition by remember {
                mutableStateOf<Long?>(null)
            }


            if (!swipeToSkip) {
                Thumbnail(
                    modifier = Modifier
//                                .width(horizontalLazyGridItemWidth)
                        .animateContentSize(),
                    sliderPositionProvider = { sliderPosition },
                    showLyricsOnClick = true,
                    customMediaMetadata = mediaMetadata
                )
            } else {
                val thumbnailLazyGridState = rememberLazyGridState()
                val currentItem by remember { derivedStateOf { thumbnailLazyGridState.firstVisibleItemIndex } }
                val itemScrollOffset by remember { derivedStateOf { thumbnailLazyGridState.firstVisibleItemScrollOffset } }

                LaunchedEffect(itemScrollOffset) {
                    if (!thumbnailLazyGridState.isScrollInProgress || itemScrollOffset != 0) return@LaunchedEffect

                    if (currentItem > currentMediaIndex)
                        playerConnection.player.seekToNext()
                    else if (currentItem < currentMediaIndex)
                        playerConnection.player.seekToPreviousMediaItem()
                }

                LaunchedEffect(mediaMetadata, canSkipPrevious, canSkipNext) {
                    // When the media item changes, scroll to it
                    val index = maxOf(0, currentMediaIndex)

                    // Only animate scroll when player expanded, otherwise animated scroll won't work
                    if (playerSheetState.isExpanded)
                        thumbnailLazyGridState.animateScrollToItem(index)
                    else
                        thumbnailLazyGridState.scrollToItem(index)
                }

                val horizontalLazyGridItemWidthFactor = 1f
                val thumbnailSnapLayoutInfoProvider = remember(thumbnailLazyGridState) {
                    SnapLayoutInfoProvider(
                        lazyGridState = thumbnailLazyGridState,
                        positionInLayout = { layoutSize, itemSize ->
                            (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                        }
                    )
                }
                val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                LazyHorizontalGrid(
                    state = thumbnailLazyGridState,
                    rows = GridCells.Fixed(1),
                    flingBehavior = rememberSnapFlingBehavior(thumbnailSnapLayoutInfoProvider),
                    userScrollEnabled = playerSheetState.isExpanded,
                    modifier = Modifier.padding(vertical = QueuePeekHeight / 2)
                ) {
                    items(
                        items = mediaItems,
                        key = { it.id }
                    ) {
                        Thumbnail(
                            modifier = Modifier
                                .width(horizontalLazyGridItemWidth)
                                .animateContentSize(),
                            sliderPositionProvider = { sliderPosition },
                            showLyricsOnClick = true,
                            customMediaMetadata = it
                        )
                    }
                }
            }
        }

        // Controls stagger in last, sliding up from below as the cover settles.
        val controlsProgress = ((playerSheetState.progress - 0.55f) / 0.35f).coerceIn(0f, 1f)
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = controlsProgress
                translationY = (1f - controlsProgress) * 60.dp.toPx()
            }
        ) {
            ControlsContent(playerSheetState, queueSheetState, navController, queueBoard)
        }


        Spacer(Modifier.height(24.dp))


    }

    if (enableQueueSheet) {
        QueueSheet(
            state = queueSheetState,
            playerBottomSheetState = playerSheetState,
            onTerminate = {
                playerSheetState.dismiss()
                queueBoard.detachedHead = false
            },
            navController = navController,
            windowInsets = windowInsets,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LandscapePlayer(
    playerSheetState: BottomSheetState,
    navController: NavController,
    queueBoard: QueueBoard,
    enableQueueSheet: Boolean = true,
    windowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
) {
    val TAG = "BottomSheetPlayer"

    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()


    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    val swipeToSkip by rememberPreference(SwipeToSkipKey, defaultValue = false)
    val previousMediaMetadata = if (swipeToSkip && playerConnection.player.hasPreviousMediaItem()) {
        val previousIndex = playerConnection.player.previousMediaItemIndex
        playerConnection.player.getMediaItemAt(previousIndex).metadata
    } else null

    val nextMediaMetadata = if (swipeToSkip && playerConnection.player.hasNextMediaItem()) {
        val nextIndex = playerConnection.player.nextMediaItemIndex
        playerConnection.player.getMediaItemAt(nextIndex).metadata
    } else null

    val mediaItems = listOfNotNull(previousMediaMetadata, mediaMetadata, nextMediaMetadata)
    val currentMediaIndex = mediaItems.indexOf(mediaMetadata)


    val showLyrics by rememberPreference(ShowLyricsKey, defaultValue = false)

    var sliderPosition by remember {
        mutableStateOf<Long?>(null)
    }

    val dismissedBound = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    val queueSheetState = rememberBottomSheetState(
        dismissedBound = dismissedBound,
        expandedBound = playerSheetState.expandedBound,
        collapsedBound = dismissedBound,
        initialAnchor = dismissedAnchor,
    )

    val vPadding = max(
        WindowInsets.safeDrawing.getTop(LocalDensity.current),
        WindowInsets.safeDrawing.getBottom(LocalDensity.current)
    )
    val vPaddingDp = with(LocalDensity.current) { vPadding.toDp() }
    val verticalInsets = WindowInsets(left = 0.dp, top = vPaddingDp, right = 0.dp, bottom = vPaddingDp)
    Row(
        modifier = Modifier
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).add(verticalInsets)
            )
            .fillMaxSize()
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .nestedScroll(playerSheetState.preUpPostDownNestedScrollConnection)
        ) {
            Log.v(TAG, "PLR-3.1a")
            if (!swipeToSkip) {
                Thumbnail(
                    sliderPositionProvider = { sliderPosition },
                    modifier = Modifier
//                                .width(horizontalLazyGridItemWidth)
                        .animateContentSize(),
                    showLyricsOnClick = true,
                    customMediaMetadata = mediaMetadata
                )
            } else {
                val thumbnailLazyGridState = rememberLazyGridState()
                val currentItem by remember { derivedStateOf { thumbnailLazyGridState.firstVisibleItemIndex } }
                val itemScrollOffset by remember { derivedStateOf { thumbnailLazyGridState.firstVisibleItemScrollOffset } }

                LaunchedEffect(itemScrollOffset) {
                    if (!thumbnailLazyGridState.isScrollInProgress || itemScrollOffset != 0) return@LaunchedEffect

                    if (currentItem > currentMediaIndex)
                        playerConnection.player.seekToNext()
                    else if (currentItem < currentMediaIndex)
                        playerConnection.player.seekToPreviousMediaItem()
                }

                LaunchedEffect(mediaMetadata, canSkipPrevious, canSkipNext) {
                    // When the media item changes, scroll to it
                    val index = maxOf(0, currentMediaIndex)

                    // Only animate scroll when player expanded, otherwise animated scroll won't work
                    if (playerSheetState.isExpanded)
                        thumbnailLazyGridState.animateScrollToItem(index)
                    else
                        thumbnailLazyGridState.scrollToItem(index)
                }

                val horizontalLazyGridItemWidthFactor = 1f
                val thumbnailSnapLayoutInfoProvider = remember(thumbnailLazyGridState) {
                    SnapLayoutInfoProvider(
                        lazyGridState = thumbnailLazyGridState,
                        positionInLayout = { layoutSize, itemSize ->
                            (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                        }
                    )
                }
                val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor


                LazyHorizontalGrid(
                    state = thumbnailLazyGridState,
                    rows = GridCells.Fixed(1),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    flingBehavior = rememberSnapFlingBehavior(thumbnailSnapLayoutInfoProvider),
                    userScrollEnabled = playerSheetState.isExpanded && swipeToSkip
                ) {
                    items(
                        items = mediaItems,
                        key = { it.id }
                    ) {
                        Thumbnail(
                            sliderPositionProvider = { sliderPosition },
                            modifier = Modifier
                                .width(horizontalLazyGridItemWidth)
                                .animateContentSize(),
                            showLyricsOnClick = true,
                            customMediaMetadata = it
                        )
                    }
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                // "percentage to half width", not "percentage of width"
                .weight(if (showLyrics) 0.65f else 1f, false)
                .animateContentSize()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
        ) {
            Spacer(Modifier.weight(1f))

            ControlsContent(playerSheetState, queueSheetState, navController, queueBoard, context.supportsWideScreen())

            Spacer(Modifier.weight(1f))
        }
    }

    if (enableQueueSheet) {
        QueueSheet(
            state = queueSheetState,
            playerBottomSheetState = playerSheetState,
            onTerminate = {
                playerSheetState.dismiss()
                queueBoard.detachedHead = false
            },
            navController = navController
        )
    }
}


@Composable
fun PlayerTopBar(modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mediaMetadata?.title ?: "",
                style = MaterialTheme.typography.headlineMedium,
                color = onPlayerBackgroundColor(),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(iterations = 1, initialDelayMillis = 3000)
            )
            Text(
                text = mediaMetadata?.artists?.joinToString { it.name } ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = onPlayerBackgroundColor().copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.Cast,
            contentDescription = null,
            tint = onPlayerBackgroundColor().copy(alpha = 0.8f),
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun onPlayerBackgroundColor(): androidx.compose.ui.graphics.Color {
    val playerConnection = LocalPlayerConnection.current ?: return androidx.compose.ui.graphics.Color.White
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val isSystemInDark = isSystemInDarkTheme()
    val useDark = remember(darkTheme, isSystemInDark) {
        if (darkTheme == DarkMode.AUTO) isSystemInDark else darkTheme == DarkMode.ON
    }
    val bg by rememberEnumPreference(PlayerBackgroundStyleKey, defaultValue = DEFAULT_PLAYER_BACKGROUND)
    return when (bg) {
        PlayerBackgroundStyle.FOLLOW_THEME -> MaterialTheme.colorScheme.onSurface
        else -> androidx.compose.ui.graphics.Color.White
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlsContent(
    playerSheetState: BottomSheetState,
    queueSheetState: BottomSheetState,
    navController: NavController,
    queueBoard: QueueBoard,
    showQueueHint: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val context = LocalContext.current
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()

    val playbackState by playerConnection.playbackState.collectAsState()
    var duration by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.duration)
    }
    var position by remember(playbackState) {
        mutableLongStateOf(playerConnection.player.currentPosition)
    }

    LaunchedEffect(playbackState) {
        if (playbackState == STATE_READY) {
            while (isActive) {
                delay(500)
                position = playerConnection.player.currentPosition
                duration = playerConnection.player.duration
            }
        }
    }

    var sliderPosition by remember { mutableStateOf<Long?>(null) }
    val iconColor = onPlayerBackgroundColor()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Progress slider
        Slider(
            value = (sliderPosition ?: position).toFloat(),
            valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
            onValueChange = { sliderPosition = it.toLong() },
            onValueChangeFinished = {
                sliderPosition?.let {
                    playerConnection.player.seekTo(it)
                    position = it
                }
                sliderPosition = null
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            },
            thumb = { Spacer(modifier = Modifier.size(0.dp)) },
            track = { sliderState ->
                PlayerSliderTrack(sliderState = sliderState, colors = SliderDefaults.colors())
            },
            modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
        )

        // Times
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerHorizontalPadding + 4.dp)
        ) {
            Text(
                text = makeTimeString(sliderPosition ?: position),
                style = MaterialTheme.typography.labelMedium,
                color = iconColor.copy(alpha = 0.7f),
            )
            Text(
                text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                style = MaterialTheme.typography.labelMedium,
                color = iconColor.copy(alpha = 0.7f),
            )
        }

        Spacer(Modifier.height(24.dp))

        // Main controls: prev / play / next (large plain icons)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PlayerHorizontalPadding)
        ) {
            ResizableIconButton(
                icon = Icons.Rounded.SkipPrevious,
                enabled = canSkipPrevious,
                modifier = Modifier.size(48.dp),
                color = iconColor,
                onClick = {
                    if (playerConnection.player.currentMediaItem == null) {
                        queueBoard.setCurrQueue()
                    }
                    playerConnection.player.seekToPrevious()
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
            )

            ResizableIconButton(
                icon = if (playbackState == STATE_ENDED) Icons.Rounded.Replay
                else if (isPlaying) Icons.Rounded.Pause
                else Icons.Rounded.PlayArrow,
                modifier = Modifier.size(72.dp),
                color = iconColor,
                onClick = {
                    if (playerConnection.player.currentMediaItem == null) {
                        queueBoard.setCurrQueue()
                        playerConnection.player.togglePlayPause()
                    } else if (playbackState == STATE_ENDED) {
                        playerConnection.player.seekTo(0, 0)
                        playerConnection.player.playWhenReady = true
                    } else {
                        playerConnection.player.togglePlayPause()
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                }
            )

            ResizableIconButton(
                icon = Icons.Rounded.SkipNext,
                enabled = canSkipNext,
                modifier = Modifier.size(48.dp),
                color = iconColor,
                onClick = {
                    if (playerConnection.player.currentMediaItem == null) {
                        queueBoard.setCurrQueue()
                    }
                    playerConnection.player.seekToNext()
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
            )
        }

        Spacer(Modifier.height(20.dp))

        // Secondary row: shuffle, timer, equalizer, queue, more (Salt Player style: 5 icons)
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            ResizableIconButton(
                icon = if (shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle_off,
                modifier = Modifier.size(28.dp),
                color = if (shuffleModeEnabled) iconColor else iconColor.copy(alpha = 0.6f),
                enabled = playerConnection.player.currentMediaItem != null,
                onClick = {
                    playerConnection.triggerShuffle()
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                }
            )

            ResizableIconButton(
                icon = Icons.Rounded.Schedule,
                modifier = Modifier.size(28.dp),
                color = iconColor.copy(alpha = 0.8f),
                onClick = {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = mediaMetadata,
                            navController = navController,
                            playerBottomSheetState = playerSheetState,
                            onDismiss = { menuState.dismiss() }
                        )
                    }
                }
            )

            ResizableIconButton(
                icon = Icons.Rounded.GraphicEq,
                modifier = Modifier.size(28.dp),
                color = iconColor.copy(alpha = 0.8f),
                onClick = {
                    val intent = android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL.let {
                        android.content.Intent(it).apply {
                            putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, playerConnection.player.audioSessionId)
                            putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                            putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
                        }
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
            )

            ResizableIconButton(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                modifier = Modifier.size(28.dp),
                color = iconColor.copy(alpha = 0.8f),
                onClick = {
                    queueSheetState.expandSoft()
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                }
            )

            ResizableIconButton(
                icon = Icons.Rounded.MoreVert,
                modifier = Modifier.size(28.dp),
                color = iconColor.copy(alpha = 0.8f),
                onClick = {
                    menuState.show {
                        PlayerMenu(
                            mediaMetadata = mediaMetadata,
                            navController = navController,
                            playerBottomSheetState = playerSheetState,
                            onDismiss = { menuState.dismiss() }
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun PlayerBackground(
    playerConnection: PlayerConnection,
    playerBackground: PlayerBackgroundStyle,
    showLyrics: Boolean,
    useDarkTheme: Boolean,
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(NavigationBarDefaults.Elevation))
            .fillMaxSize()
    ) {

        val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
        var gradientColors by remember {
            mutableStateOf<List<Color>>(emptyList())
        }


        // gradient colours
        LaunchedEffect(mediaMetadata, playerBackground) {
            if (playerBackground != PlayerBackgroundStyle.GRADIENT || context.isPowerSaver()) return@LaunchedEffect

            withContext(coilCoroutine) {
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(mediaMetadata?.getThumbnailModel(100, 100))
                        .allowHardware(false)
                        .build()
                )

                val bitmap = result.image?.toBitmap()?.extractGradientColors()
                bitmap?.let {
                    gradientColors = it
                }
            }
        }


        AnimatedContent(
            targetState = mediaMetadata,
            transitionSpec = {
                fadeIn(spring<Float>(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )).togetherWith(fadeOut(spring<Float>(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )))
            }
        ) { metadata ->
            if (playerBackground == PlayerBackgroundStyle.BLUR) {
                AsyncImage(
                    model = metadata?.getThumbnailModel(100, 100),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(100.dp)
                        .alpha(0.5f)
                )
            }
        }

        AnimatedContent(
            targetState = gradientColors,
            transitionSpec = {
                fadeIn(spring<Float>(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                )).togetherWith(fadeOut(spring<Float>(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )))
            }
        ) { colors ->
            if (playerBackground == PlayerBackgroundStyle.GRADIENT && colors.size >= 2) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    colors[0].copy(alpha = 0.9f),
                                    colors[1].copy(alpha = 0.7f),
                                    Color.Black.copy(alpha = 0.5f)
                                )
                            )
                        )
                )
            }
        }

        // Dark scrim for text readability
        if (playerBackground != PlayerBackgroundStyle.FOLLOW_THEME) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.0f),
                                Color.Black.copy(alpha = 0.3f)
                            )
                        )
                    )
            )
        }
    }
}