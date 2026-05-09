package com.scanpang.app.components.ar

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.hufs.arnavigation_com.CompassView

/**
 * AR 길안내용 점선 나침반 — 사용자가 폰을 내려다볼 때만 표시되며,
 * 다음 턴 방향을 향해 회전한 3D 화살표 + 점선을 그림.
 *
 * 시각적 렌더링은 기존 [CompassView] (Custom View)를 [AndroidView]로 래핑.
 * 각도 변화를 부드럽게 보이도록 `animateFloatAsState`로 보간.
 */
@Composable
fun ArNavCompass(
    /** 다음 턴 방향과 현재 heading의 각도차(deg). 양수=오른쪽, 음수=왼쪽. */
    angleDiffDeg: Float,
    modifier: Modifier = Modifier,
) {
    // 각도 점프를 부드럽게 — 150ms tween + FastOutSlowIn 이징으로 빠르고 자연스러운 회전
    val animatedAngle by animateFloatAsState(
        targetValue = angleDiffDeg,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "compassAngle",
    )

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context -> CompassView(context) },
        update = { view -> view.angleDiff = animatedAngle },
    )
}
