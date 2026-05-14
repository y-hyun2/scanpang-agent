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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.LocalConvenienceStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Mosque
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

/** SavedPlaceNavTarget → 해당 상세 화면 라우트. */
fun SavedPlaceNavTarget.toDetailRoute(): String = when (this) {
    SavedPlaceNavTarget.Restaurant -> AppRoutes.RestaurantDetail
    SavedPlaceNavTarget.PrayerRoom -> AppRoutes.PrayerRoomDetail
    SavedPlaceNavTarget.TouristSpot -> AppRoutes.TouristDetail
    SavedPlaceNavTarget.Shopping -> AppRoutes.ShoppingDetail
    SavedPlaceNavTarget.ConvenienceStore -> AppRoutes.ConvenienceDetail
    SavedPlaceNavTarget.Cafe -> AppRoutes.CafeDetail
    SavedPlaceNavTarget.Atm -> AppRoutes.AtmDetail
    SavedPlaceNavTarget.Bank -> AppRoutes.BankDetail
    SavedPlaceNavTarget.Exchange -> AppRoutes.ExchangeDetail
    SavedPlaceNavTarget.Subway -> AppRoutes.SubwayDetail
    SavedPlaceNavTarget.Restroom -> AppRoutes.RestroomDetail
    SavedPlaceNavTarget.Lockers -> AppRoutes.LockersDetail
    SavedPlaceNavTarget.Hospital -> AppRoutes.HospitalDetail
    SavedPlaceNavTarget.Pharmacy -> AppRoutes.PharmacyDetail
}

/**
 * 최근 본 장소 카드 행 — Home 미리보기/RecentlyViewedListScreen 전체 리스트가 동일하게 사용한다.
 * 좌측 카테고리 아이콘(원형 PrimarySoft 배경) + 이름/부제 + 우측 chevron.
 */
@Composable
fun RecentlyViewedRow(
    entry: RecentlyViewedEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScanPangShapes.radius14)
            .background(ScanPangColors.Background)
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
            val subtitle = entry.distanceLine.ifBlank { entry.category }
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
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(ScanPangDimens.icon20),
            tint = ScanPangColors.OnSurfacePlaceholder,
        )
    }
}
