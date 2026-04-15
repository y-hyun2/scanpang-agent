package com.scanpang.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

@Composable
fun ScanPangFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) ScanPangColors.Primary else ScanPangColors.Background
    val fg = if (selected) ScanPangColors.Surface else ScanPangColors.OnSurfaceStrong
    val style = if (selected) ScanPangType.chip13SemiBold else ScanPangType.chip13Medium
    Box(
        modifier = modifier
            .clip(ScanPangShapes.filterChip)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = ScanPangSpacing.lg, vertical = ScanPangSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = style, color = fg)
    }
}
