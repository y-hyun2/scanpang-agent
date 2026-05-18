package com.scanpang.app.screens.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.scanpang.app.R
import kotlinx.coroutines.delay

@Composable
fun YouScanSplashScreen(
    modifier: Modifier = Modifier,
    replayKey: Int = 0,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        YouScanAnimatedLogo(replayKey = replayKey)
    }
}

@Composable
fun YouScanAnimatedLogo(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    replayKey: Int = 0,
) {
    val reveal = remember { Animatable(0f) }

    LaunchedEffect(replayKey) {
        reveal.snapTo(0f)
        delay(300)
        reveal.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 1500,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        YouScanLogo(size = size)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLaser(reveal = reveal.value)
        }
    }
}

@Composable
fun YouScanLogo(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
) {
    AsyncImage(
        model = R.drawable.logo_youscan,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
    )
}

@Composable
fun YouScanLogoReplayExample() {
    var replayKey by remember { mutableIntStateOf(0) }

    YouScanSplashScreen(replayKey = replayKey)
}

private fun DrawScope.drawLaser(
    reveal: Float,
) {
    val alpha = when {
        reveal <= 0f -> 0f
        reveal < 0.1f -> reveal / 0.1f
        reveal > 0.9f -> (1f - reveal) / 0.1f
        else -> 1f
    }.coerceIn(0f, 1f)

    if (alpha <= 0f) return

    val y = size.height * reveal
    val start = Offset(size.width * 0.125f, y)
    val end = Offset(size.width * 0.875f, y)

    drawLine(
        color = Color.White.copy(alpha = 0.18f * alpha),
        start = start,
        end = end,
        strokeWidth = size.width * 0.15f,
        cap = StrokeCap.Round,
    )

    drawLine(
        color = Color.White.copy(alpha = 0.32f * alpha),
        start = start,
        end = end,
        strokeWidth = size.width * 0.0875f,
        cap = StrokeCap.Round,
    )

    drawLine(
        color = Color.White.copy(alpha = 0.65f * alpha),
        start = start,
        end = end,
        strokeWidth = size.width * 0.05f,
        cap = StrokeCap.Round,
    )

    drawLine(
        color = Color.White.copy(alpha = alpha),
        start = start,
        end = end,
        strokeWidth = size.width * 0.01875f,
        cap = StrokeCap.Round,
    )
}
