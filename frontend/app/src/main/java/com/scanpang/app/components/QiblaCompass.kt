package com.scanpang.app.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangType

/**
 * 키블라 나침반: 원형 테두리, 방위 문자, 중심점, 북쪽 방향 표시 아이콘
 */
@Composable
fun QiblaCompass(
    bearingDegrees: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val strokePx = with(density) { ScanPangDimens.compassStroke.toPx() }
    BoxWithConstraints(
        modifier = modifier.size(ScanPangDimens.compassSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f - strokePx / 2f
            drawCircle(
                color = ScanPangColors.Primary,
                radius = r,
                style = Stroke(width = strokePx),
            )
        }
        Text(
            text = "N",
            style = ScanPangType.compassLabel12,
            color = ScanPangColors.OnSurfaceMuted,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = ScanPangDimens.borderHairline * 2),
        )
        Text(
            text = "S",
            style = ScanPangType.compassLabel12,
            color = ScanPangColors.OnSurfaceMuted,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = ScanPangDimens.borderHairline * 2),
        )
        Text(
            text = "W",
            style = ScanPangType.compassLabel12,
            color = ScanPangColors.OnSurfaceMuted,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = ScanPangDimens.borderHairline * 2),
        )
        Text(
            text = "E",
            style = ScanPangType.compassLabel12,
            color = ScanPangColors.OnSurfaceMuted,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = ScanPangDimens.borderHairline * 2),
        )
        Canvas(
            modifier = Modifier
                .size(ScanPangDimens.compassCenterDot)
                .align(Alignment.Center),
        ) {
            drawCircle(
                color = ScanPangColors.Primary,
                radius = size.minDimension / 2f,
                center = Offset(size.width / 2f, size.height / 2f),
            )
        }
        Icon(
            imageVector = Icons.Rounded.Navigation,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-ScanPangDimens.compassNavIcon / 4))
                .rotate(bearingDegrees)
                .size(ScanPangDimens.compassNavIcon),
            tint = ScanPangColors.Primary,
        )
    }
}
