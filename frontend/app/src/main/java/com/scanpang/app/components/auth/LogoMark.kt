package com.scanpang.app.components.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangTheme

/**
 * 스캔 뷰파인더(4개 코너) + 중앙 위치 핀으로 구성된 ScanPang 로고.
 *
 * 디자인 기준 120dp. 모든 부속 요소가 비례 스케일되도록 [size] 만 노출한다.
 * Figma 노드 `Component/LogoMark/Default` (514:5534) 의 좌표·반경을 그대로 환산.
 *
 * 성능 주의: Canvas drawScope 내부에서 [Path] / [Brush] 를 새로 할당하면
 * 매 프레임 GC 압력이 발생해 RenderThread 가 포화될 수 있다. 모든 정적
 * 도형·브러시는 [size]·색·density 를 key 로 하는 [remember] 캐시로 만들어
 * draw 단계에선 그리기만 수행한다.
 */
@Composable
fun LogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    cornerColor: Color = ScanPangColors.Primary,
    pinColor: Color = ScanPangColors.Primary,
    glowColor: Color = ScanPangColors.PrimarySoft,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }

    val cornerStroke = remember(sizePx) { Stroke(width = sizePx * (3.5f / 120f)) }
    val cornerPaths = remember(sizePx) { computeCornerPaths(sizePx) }
    val pinPath = remember(sizePx) { computePinPath(sizePx) }

    val glowRadius = sizePx * (32f / 120f)
    val glowCenter = remember(sizePx) { Offset(sizePx / 2f, sizePx / 2f) }
    val glowBrush = remember(sizePx, glowColor) {
        Brush.radialGradient(
            colors = listOf(glowColor.copy(alpha = 0.55f), glowColor.copy(alpha = 0f)),
            center = Offset(sizePx / 2f, sizePx / 2f),
            radius = sizePx * (32f / 120f),
        )
    }

    val pinWidth = sizePx * (28f / 120f)
    val pinTop = sizePx * (38f / 120f)
    val dotRadius = sizePx * (5f / 120f)
    val dotCenter = remember(sizePx) {
        Offset(sizePx / 2f, pinTop + pinWidth / 2f)
    }

    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.size(size)) {
            cornerPaths.forEach { drawPath(it, color = cornerColor, style = cornerStroke) }
            drawCircle(brush = glowBrush, radius = glowRadius, center = glowCenter)
            drawPath(pinPath, color = pinColor)
            drawCircle(color = Color.White, radius = dotRadius, center = dotCenter)
        }
    }
}

private fun computeCornerPaths(s: Float): List<Path> {
    val cornerStroke = s * (3.5f / 120f)
    val cornerSize = s * (28f / 120f)
    val arm = cornerSize * 0.6f
    val halfStroke = cornerStroke / 2f

    fun cornerPath(originX: Float, originY: Float, signX: Int, signY: Int): Path {
        val p = Path()
        val sx = originX + halfStroke * signX
        val sy = originY + halfStroke * signY
        p.moveTo(sx + arm * signX, sy)
        p.lineTo(sx, sy)
        p.lineTo(sx, sy + arm * signY)
        return p
    }

    return listOf(
        cornerPath(0f, 0f, 1, 1),
        cornerPath(s, 0f, -1, 1),
        cornerPath(0f, s, 1, -1),
        cornerPath(s, s, -1, -1),
    )
}

private fun computePinPath(s: Float): Path {
    val pinWidth = s * (28f / 120f)
    val pinHeight = s * (40f / 120f)
    val pinTop = s * (38f / 120f)
    val pinLeft = (s - pinWidth) / 2f
    val cx = pinLeft + pinWidth / 2f
    val cy = pinTop + pinWidth / 2f
    val r = pinWidth / 2f
    return Path().apply {
        addArc(
            Rect(offset = Offset(pinLeft, pinTop), size = Size(pinWidth, pinWidth)),
            startAngleDegrees = 200f,
            sweepAngleDegrees = 140f,
        )
        lineTo(cx, pinTop + pinHeight)
        close()
        addOval(Rect(center = Offset(cx, cy), radius = r))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun LogoMarkPreviewDefault() {
    ScanPangTheme {
        LogoMark()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "LogoMark 60dp")
@Composable
private fun LogoMarkPreviewSmall() {
    ScanPangTheme {
        LogoMark(size = 60.dp)
    }
}
