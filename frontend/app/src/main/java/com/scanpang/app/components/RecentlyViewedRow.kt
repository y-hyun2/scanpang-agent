package com.scanpang.app.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Atm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.LocalConvenienceStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.scanpang.app.data.RecentlyViewedEntry
import com.scanpang.app.data.SavedPlaceNavTarget
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.i18n.LocalStrings
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

/** SavedPlaceNavTarget → 최근 본 장소 행에 쓰는 카테고리 아이콘. */
fun SavedPlaceNavTarget.toRecentIcon(): ImageVector = when (this) {
    SavedPlaceNavTarget.Restaurant -> Icons.Rounded.Restaurant
    SavedPlaceNavTarget.PrayerRoom -> Icons.Rounded.Mosque
    SavedPlaceNavTarget.TouristSpot -> Icons.Rounded.Whatshot
    SavedPlaceNavTarget.Shopping -> Icons.Rounded.LocalMall
    SavedPlaceNavTarget.ConvenienceStore -> Icons.Rounded.LocalConvenienceStore
    SavedPlaceNavTarget.Cafe -> Icons.Rounded.Coffee
    SavedPlaceNavTarget.Atm -> Icons.Rounded.Atm
    SavedPlaceNavTarget.Bank -> Icons.Rounded.AccountBalance
    SavedPlaceNavTarget.Exchange -> Icons.Rounded.CurrencyExchange
    SavedPlaceNavTarget.Subway -> Icons.Rounded.Train
    SavedPlaceNavTarget.Restroom -> Icons.Rounded.Wc
    SavedPlaceNavTarget.Lockers -> Icons.Rounded.Lock
    SavedPlaceNavTarget.Hospital -> Icons.Rounded.LocalHospital
    SavedPlaceNavTarget.Pharmacy -> Icons.Rounded.Medication
}

/**
 * SavedPlaceNavTarget + RecentlyViewedEntry.id → 통합 PlaceDetailScreen 라우트.
 * placeId 가 있으면 findPlaceById 가 정확한 매장을 찾고, 없으면 카테고리 1번째로 폴백.
 */
fun RecentlyViewedEntry.toDetailRoute(): String =
    AppRoutes.placeDetailRoute(target.toCategoryKey(), id)

/**
 * 최근 본 장소 카드 행 — Home 미리보기/RecentlyViewedListScreen 전체 리스트가 동일하게 사용한다.
 * 좌측 카테고리 아이콘(원형 PrimarySoft 배경) + 이름/부제 + 우측 chevron.
 * [isDeleteMode] 가 true 이면 chevron 대신 선택 체크박스를 노출한다.
 */
@Composable
fun RecentlyViewedRow(
    entry: RecentlyViewedEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDeleteMode: Boolean = false,
    isSelected: Boolean = false,
    // 호출처가 현재 위치 기준 거리 라벨(예: '120m') 을 계산해 전달. 좌표 없거나
    // GPS 미수신이면 빈 string → subtitle 은 category 만 보임.
    distanceLabel: String = "",
) {
    val s = LocalStrings.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScanPangShapes.radius14)
            .background(
                if (isDeleteMode && isSelected) ScanPangColors.PrimarySoft
                else ScanPangColors.Background,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = ScanPangSpacing.md, vertical = ScanPangDimens.recentRowVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(ScanPangDimens.recentIconCircle)
                .clip(CircleShape)
                .background(ScanPangColors.PrimarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = entry.target.toRecentIcon(),
                contentDescription = null,
                modifier = Modifier.size(ScanPangDimens.icon20),
                tint = ScanPangColors.Primary,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
        ) {
            Text(
                text = entry.name,
                style = ScanPangType.title14,
                color = ScanPangColors.OnSurfaceStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 피그마: '카테고리 · 거리' 둘 다. 한쪽만 있으면 그쪽만, 둘 다 없으면 행 숨김.
            val subtitle = listOfNotNull(
                entry.category.ifBlank { null },
                distanceLabel.ifBlank { null },
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = ScanPangType.caption12Medium,
                    color = ScanPangColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isDeleteMode) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (isSelected) s.selectedDesc else s.selectDesc,
                modifier = Modifier.size(ScanPangDimens.icon20),
                tint = if (isSelected) ScanPangColors.Primary else ScanPangColors.OnSurfacePlaceholder,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(ScanPangDimens.icon20),
                tint = ScanPangColors.OnSurfacePlaceholder,
            )
        }
    }
}
