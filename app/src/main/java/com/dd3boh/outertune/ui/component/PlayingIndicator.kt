package com.dd3boh.outertune.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dd3boh.outertune.R
import com.dd3boh.outertune.constants.ThumbnailCornerRadius
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random


@Composable
fun PlayingIndicator(
    color: Color,
    modifier: Modifier = Modifier,
    bars: Int = 3,
    barWidth: Dp = 4.dp,
    cornerRadius: Dp = ThumbnailCornerRadius,
    isPlaying: Boolean = true
) {
    val animatables = remember {
        List(bars) {
            Animatable(0.1f)
        }
    }

    LaunchedEffect(isPlaying) {
        animatables.forEachIndexed { index, animatable ->
            launch {
                // Stagger the bars for a more organic wave effect
                delay(index * 120L)
                while (true) {
                    if (isPlaying) {
                        animatable.animateTo(
                            targetValue = Random.nextFloat() * 0.85f + 0.15f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            )
                        )
                        delay(Random.nextLong(200, 500))
                    } else {
                        animatable.animateTo(
                            targetValue = 0.15f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                        delay(100)
                    }
                }
            }
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(barWidth * 1.5f),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
    ) {
        animatables.forEach { animatable ->
            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(barWidth)
            ) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x = 0f, y = size.height * (1 - animatable.value)),
                    size = size.copy(height = animatable.value * size.height),
                    cornerRadius = CornerRadius(cornerRadius.toPx())
                )
            }
        }
    }
}

@Composable
fun PlayingIndicatorBox(
    modifier: Modifier = Modifier,
    isActive: Boolean,
    playWhenReady: Boolean,
    color: Color = Color.White,
) {
    AnimatedVisibility(
        visible = isActive,
        enter = fadeIn(spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )),
        exit = fadeOut(spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
        ) {
            if (playWhenReady) {
                PlayingIndicator(
                    color = color,
                    modifier = Modifier.height(24.dp)
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = color
                )
            }
        }
    }
}
