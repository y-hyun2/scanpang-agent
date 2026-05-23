package com.scanpang.app.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangType
import kotlinx.coroutines.delay
import kotlin.math.exp

/**
 * [loadingStartedAt] 이 제공되면 경과 시간 기반 progress bar 표시.
 * 요청 시작 시각(millis) → 1 - e^(-t/2500) * 90% 곡선으로 증가.
 * null 이면 기존 bouncing dots fallback.
 */
@Composable
fun PlaceLoadingScreen(
    modifier: Modifier = Modifier,
    loadingStartedAt: Long? = null,
) {
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(loadingStartedAt) {
        if (loadingStartedAt == null) return@LaunchedEffect
        while (true) {
            val elapsed = System.currentTimeMillis() - loadingStartedAt
            progress = (1.0 - exp(-elapsed.toDouble() / 2500.0)).toFloat() * 0.9f
            delay(50L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScanPangColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (loadingStartedAt != null) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = ScanPangType.body15Medium,
                    color = ScanPangColors.Primary,
                )
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .width(160.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = ScanPangColors.Primary,
                    trackColor = ScanPangColors.PrimarySoft,
                )
            } else {
                BouncingDots()
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "정보를 불러오는 중입니다",
                style = ScanPangType.body15Medium,
                color = ScanPangColors.OnSurfaceMuted,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "조금만 기다리면 바로 표시돼요",
                style = ScanPangType.caption12,
                color = ScanPangColors.OnSurfacePlaceholder,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

@Composable
private fun BouncingDots() {
    val transition = rememberInfiniteTransition(label = "dots")

    fun spec(delay: Int) = infiniteRepeatable(
        animation = tween<Float>(durationMillis = 600, delayMillis = delay, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse,
    )
    val a1 by transition.animateFloat(0.25f, 1f, spec(0),   label = "d1")
    val a2 by transition.animateFloat(0.25f, 1f, spec(160), label = "d2")
    val a3 by transition.animateFloat(0.25f, 1f, spec(320), label = "d3")

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(a1, a2, a3).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(ScanPangColors.Primary),
            )
        }
    }
}
