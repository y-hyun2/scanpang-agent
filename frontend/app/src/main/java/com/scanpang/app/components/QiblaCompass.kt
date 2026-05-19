package com.scanpang.app.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val CompassSize = 232.dp

@Composable
fun QiblaCompass(
    azimuthDegrees: Float,
    qiblaFromNorth: Float,
    modifier: Modifier = Modifier,
) {
    val dialRotation by animateFloatAsState(
        targetValue = -azimuthDegrees,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "compassDial",
    )

    Box(
        modifier = modifier.size(CompassSize),
        contentAlignment = Alignment.Center,
    ) {
        // ── Rotating compass dial ─────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(dialRotation),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val center = Offset(cx, cy)
                val outerR = min(size.width, size.height) / 2f * 0.84f
                val innerR = outerR * 0.58f

                // ── Face fill
                drawCircle(color = ScanPangColors.Background, radius = outerR, center = center)

                // ── Outer ring
                drawCircle(
                    color = ScanPangColors.Primary,
                    radius = outerR,
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx()),
                )

                // ── Inner guide ring (subtle)
                drawCircle(
                    color = ScanPangColors.OnSurfaceMuted.copy(alpha = 0.12f),
                    radius = innerR,
                    center = center,
                    style = Stroke(width = 1.dp.toPx()),
                )

                // ── Cardinal notches
                for (deg in listOf(0, 90, 180, 270)) {
                    val rad = (deg - 90.0) * PI / 180.0
                    val cosA = cos(rad).toFloat()
                    val sinA = sin(rad).toFloat()
                    val notchLen = outerR * 0.09f
                    drawLine(
                        color = ScanPangColors.Primary.copy(alpha = 0.35f),
                        start = Offset(cx + cosA * (outerR - notchLen), cy + sinA * (outerR - notchLen)),
                        end = Offset(cx + cosA * (outerR - 2f), cy + sinA * (outerR - 2f)),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                // ── Qibla 화살표 — 메카 방향을 가리키는 메인 인디케이터
                val qRad = (qiblaFromNorth - 90.0) * PI / 180.0
                val qCos = cos(qRad).toFloat()
                val qSin = sin(qRad).toFloat()

                val tipDist = outerR * 0.78f
                val arrowHeadLen = 16.dp.toPx()
                val arrowHeadWidth = 12.dp.toPx()

                val tipX = cx + qCos * tipDist
                val tipY = cy + qSin * tipDist
                val shaftEndX = cx + qCos * (tipDist - arrowHeadLen)
                val shaftEndY = cy + qSin * (tipDist - arrowHeadLen)
                val perpX = -qSin
                val perpY = qCos

                // 샤프트: 중심 → 화살표 머리 기저부
                drawLine(
                    color = ScanPangColors.Primary,
                    start = Offset(cx, cy),
                    end = Offset(shaftEndX, shaftEndY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )

                // 화살표 머리 (filled triangle)
                val arrowPath = Path().apply {
                    moveTo(tipX, tipY)
                    lineTo(shaftEndX + perpX * arrowHeadWidth / 2, shaftEndY + perpY * arrowHeadWidth / 2)
                    lineTo(shaftEndX - perpX * arrowHeadWidth / 2, shaftEndY - perpY * arrowHeadWidth / 2)
                    close()
                }
                drawPath(arrowPath, color = ScanPangColors.Primary)
            }

            // ── Cardinal labels
            val lPad = 8.dp
            listOf(
                Alignment.TopCenter to "N",
                Alignment.BottomCenter to "S",
                Alignment.CenterEnd to "E",
                Alignment.CenterStart to "W",
            ).forEach { (align, label) ->
                Box(
                    modifier = Modifier
                        .align(align)
                        .then(
                            when (align) {
                                Alignment.TopCenter -> Modifier.padding(top = lPad)
                                Alignment.BottomCenter -> Modifier.padding(bottom = lPad)
                                Alignment.CenterEnd -> Modifier.padding(end = lPad)
                                else -> Modifier.padding(start = lPad)
                            },
                        )
                        .background(
                            color = if (label == "N") ScanPangColors.PrimarySoft else Color.Transparent,
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = label,
                        style = ScanPangType.compassLabel12.copy(
                            fontSize = if (label == "N") 12.sp else 11.sp,
                            fontWeight = if (label == "N") FontWeight.Bold else FontWeight.Medium,
                            color = if (label == "N") ScanPangColors.Primary
                                    else ScanPangColors.OnSurfaceMuted,
                        ),
                    )
                }
            }
        }

        // ── Fixed overlay — 중심 점 (고정, 화살표 샤프트 시작점을 깔끔하게 마감)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val center = Offset(cx, cy)
            drawCircle(color = ScanPangColors.Primary, radius = 5.5.dp.toPx(), center = center)
            drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = center)
        }
    }
}
