package com.scanpang.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

data class SavedPlaceTag(
    val label: String,
    val style: SavedPlaceTagStyle,
)

enum class SavedPlaceTagStyle {
    Success,
    Warning,
    Neutral,
}

@Composable
fun SavedPlaceCard(
    title: String,
    categoryLabel: String,
    distanceLine: String,
    tags: List<SavedPlaceTag>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScanPangShapes.radius14)
            .border(ScanPangDimens.borderHairline, ScanPangColors.OutlineSubtle, ScanPangShapes.radius14)
            .background(ScanPangColors.Surface)
            .clickable(onClick = onClick)
            .padding(ScanPangSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ScanPangDimens.stackGap6),
        ) {
            Text(
                text = title,
                style = ScanPangType.title16SemiBold,
                color = ScanPangColors.OnSurfaceStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(ScanPangShapes.badge6)
                        .background(ScanPangColors.PrimarySoft)
                        .padding(horizontal = ScanPangSpacing.sm, vertical = ScanPangDimens.badgePadVertical),
                ) {
                    Text(
                        text = categoryLabel,
                        style = ScanPangType.tag11Medium,
                        color = ScanPangColors.Primary,
                    )
                }
                Text(
                    text = distanceLine,
                    style = ScanPangType.caption12,
                    color = ScanPangColors.OnSurfaceMuted,
                )
            }
            if (tags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ScanPangDimens.stackGap6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tags.forEach { tag ->
                        val (tc, bg) = tagColors(tag.style)
                        Box(
                            modifier = Modifier
                                .clip(ScanPangShapes.tag4)
                                .background(bg)
                                .padding(
                                    horizontal = ScanPangDimens.chipPadHorizontal,
                                    vertical = ScanPangDimens.chipPadVertical,
                                ),
                        ) {
                            Text(text = tag.label, style = ScanPangType.tag10Medium, color = tc)
                        }
                    }
                }
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(ScanPangDimens.tabIcon),
            tint = ScanPangColors.Primary,
        )
    }
}

private fun tagColors(style: SavedPlaceTagStyle): Pair<Color, Color> {
    val bg = ScanPangColors.Background
    return when (style) {
        SavedPlaceTagStyle.Success -> ScanPangColors.Success to bg
        SavedPlaceTagStyle.Warning -> ScanPangColors.AccentAmber to bg
        SavedPlaceTagStyle.Neutral -> ScanPangColors.OnSurfaceMuted to bg
    }
}
