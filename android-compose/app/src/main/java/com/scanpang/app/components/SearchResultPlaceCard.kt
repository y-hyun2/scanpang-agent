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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

enum class SearchResultBadgeKind {
    HalalMeat,
    Seafood,
    Veggie,
    SalamSeoul,
}

data class SearchResultTrustTag(
    val label: String,
    val icon: ImageVector,
)

@Composable
fun SearchResultPlaceCard(
    title: String,
    badgeKind: SearchResultBadgeKind,
    badgeLabel: String,
    cuisineLabel: String,
    distance: String,
    isOpen: Boolean,
    trustTags: List<SearchResultTrustTag>,
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
                horizontalArrangement = Arrangement.spacedBy(ScanPangDimens.stackGap6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ResultBadge(badgeKind = badgeKind, label = badgeLabel)
                Box(
                    modifier = Modifier
                        .clip(ScanPangShapes.badge6)
                        .background(ScanPangColors.PrimarySoft)
                        .padding(
                            horizontal = ScanPangDimens.cuisineBadgeHorizontal,
                            vertical = ScanPangDimens.badgePadVertical,
                        ),
                ) {
                    Text(
                        text = cuisineLabel,
                        style = ScanPangType.badge9SemiBold,
                        color = ScanPangColors.Primary,
                    )
                }
                Text(
                    text = distance,
                    style = ScanPangType.meta11Medium,
                    color = ScanPangColors.OnSurfaceMuted,
                )
                if (isOpen) {
                    Box(
                        modifier = Modifier
                            .size(ScanPangDimens.icon5)
                            .clip(CircleShape)
                            .background(ScanPangColors.OnSurfaceMuted),
                    )
                    Text(
                        text = "영업 중",
                        style = ScanPangType.meta11SemiBold,
                        color = ScanPangColors.StatusOpen,
                    )
                }
            }
            if (trustTags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ScanPangDimens.stackGap6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    trustTags.forEach { t ->
                        TrustChip(text = t.label, icon = t.icon)
                    }
                }
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(ScanPangDimens.icon18),
            tint = ScanPangColors.OnSurfacePlaceholder,
        )
    }
}

@Composable
private fun ResultBadge(
    badgeKind: SearchResultBadgeKind,
    label: String,
) {
    val bg = when (badgeKind) {
        SearchResultBadgeKind.HalalMeat -> ScanPangColors.HalalMeatBadgeBackground
        SearchResultBadgeKind.Seafood -> ScanPangColors.SeafoodBadgeBackground
        SearchResultBadgeKind.Veggie -> ScanPangColors.VeggieBadgeBackground
        SearchResultBadgeKind.SalamSeoul -> ScanPangColors.SalamSeoulBadgeBackground
    }
    val fg = when (badgeKind) {
        SearchResultBadgeKind.HalalMeat -> ScanPangColors.HalalMeatBadgeText
        SearchResultBadgeKind.Seafood -> ScanPangColors.Primary
        SearchResultBadgeKind.Veggie -> ScanPangColors.VeggieBadgeText
        SearchResultBadgeKind.SalamSeoul -> ScanPangColors.SalamSeoulBadgeText
    }
    Row(
        modifier = Modifier
            .clip(ScanPangShapes.badge6)
            .background(bg)
            .padding(horizontal = ScanPangSpacing.sm, vertical = ScanPangDimens.badgePadVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
    ) {
        Box(
            modifier = Modifier
                .size(ScanPangDimens.icon5)
                .clip(CircleShape)
                .background(fg),
        )
        Text(text = label, style = ScanPangType.badge9Bold, color = fg)
    }
}

@Composable
private fun TrustChip(
    text: String,
    icon: ImageVector,
) {
    Row(
        modifier = Modifier
            .clip(ScanPangShapes.badge6)
            .background(ScanPangColors.TrustPillBackground)
            .padding(
                horizontal = ScanPangDimens.trustChipHorizontal,
                vertical = ScanPangDimens.trustChipVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangDimens.trustIconGap),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(ScanPangDimens.icon10),
            tint = ScanPangColors.TrustPillText,
        )
        Text(text = text, style = ScanPangType.badge9SemiBold, color = ScanPangColors.TrustPillText)
    }
}
