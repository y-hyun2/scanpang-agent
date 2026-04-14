package com.scanpang.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import com.scanpang.app.components.RecentSearchRow
import com.scanpang.app.components.ScanPangBottomBar
import com.scanpang.app.components.ScanPangCategoryTile
import com.scanpang.app.components.ScanPangMainTab
import com.scanpang.app.components.ScanPangSearchFieldPlaceholder
import com.scanpang.app.components.ScanPangSuggestionRow
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

@Composable
fun SearchScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScanPangColors.Surface,
        bottomBar = {
            ScanPangBottomBar(
                selectedTab = ScanPangMainTab.Search,
                onHomeClick = { navController.navigate(AppRoutes.Home) { launchSingleTop = true } },
                onSearchClick = { },
                onSavedClick = { navController.navigate(AppRoutes.Saved) { launchSingleTop = true } },
                onProfileClick = { navController.navigate(AppRoutes.Profile) { launchSingleTop = true } },
                onExploreClick = {
                    navController.navigate(AppRoutes.ArDefault) { launchSingleTop = true }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ScanPangColors.Surface)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(ScanPangDimens.screenHorizontal)
                .padding(bottom = ScanPangSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.xl),
        ) {
            ScanPangSearchFieldPlaceholder(
                placeholder = "장소, 식당, 카테고리 검색",
                onClick = { navController.navigate(AppRoutes.SearchResults) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "최근 검색",
                        style = ScanPangType.sectionTitle16,
                        color = ScanPangColors.OnSurfaceStrong,
                    )
                    Text(
                        text = "전체 삭제",
                        style = ScanPangType.caption12Medium,
                        color = ScanPangColors.OnSurfacePlaceholder,
                    )
                }
                RecentSearchRow(
                    query = "할랄 식당",
                    onRowClick = { navController.navigate(AppRoutes.SearchResults) },
                    onRemoveClick = { },
                )
                RecentSearchRow(
                    query = "기도실",
                    onRowClick = { navController.navigate(AppRoutes.SearchResults) },
                    onRemoveClick = { },
                )
                RecentSearchRow(
                    query = "명동교자",
                    onRowClick = { navController.navigate(AppRoutes.SearchResults) },
                    onRemoveClick = { },
                )
                RecentSearchRow(
                    query = "환전소",
                    onRowClick = { navController.navigate(AppRoutes.SearchResults) },
                    onRemoveClick = { },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.lg)) {
                Text(
                    text = "추천 카테고리",
                    style = ScanPangType.sectionTitle16,
                    color = ScanPangColors.OnSurfaceStrong,
                )
                Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.rowGap10)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.rowGap10),
                    ) {
                        ScanPangCategoryTile(
                            label = "할랄 식당",
                            icon = Icons.Rounded.Restaurant,
                            iconTint = ScanPangColors.CategoryRestaurant,
                            onClick = { navController.navigate(AppRoutes.DetailNearbyHalal) },
                            modifier = Modifier.weight(1f),
                        )
                        ScanPangCategoryTile(
                            label = "기도실",
                            icon = Icons.Rounded.Mosque,
                            iconTint = ScanPangColors.Primary,
                            onClick = { navController.navigate(AppRoutes.DetailNearbyPrayer) },
                            modifier = Modifier.weight(1f),
                        )
                        ScanPangCategoryTile(
                            label = "카페",
                            icon = Icons.Rounded.Coffee,
                            iconTint = ScanPangColors.CategoryCafe,
                            onClick = { },
                            modifier = Modifier.weight(1f),
                        )
                        ScanPangCategoryTile(
                            label = "쇼핑",
                            icon = Icons.Rounded.LocalMall,
                            iconTint = ScanPangColors.CategoryMall,
                            onClick = { },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.rowGap10),
                    ) {
                        ScanPangCategoryTile(
                            label = "병원",
                            icon = Icons.Rounded.LocalHospital,
                            iconTint = ScanPangColors.CategoryMedical,
                            onClick = { },
                            modifier = Modifier.weight(1f),
                        )
                        ScanPangCategoryTile(
                            label = "약국",
                            icon = Icons.Rounded.Medication,
                            iconTint = ScanPangColors.CategoryMedical,
                            onClick = { },
                            modifier = Modifier.weight(1f),
                        )
                        ScanPangCategoryTile(
                            label = "환전소",
                            icon = Icons.Rounded.CurrencyExchange,
                            iconTint = ScanPangColors.CategoryExchange,
                            onClick = { },
                            modifier = Modifier.weight(1f),
                        )
                        ScanPangCategoryTile(
                            label = "관광지",
                            icon = Icons.Rounded.Place,
                            iconTint = ScanPangColors.Primary,
                            onClick = { },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md)) {
                Text(
                    text = "이런 곳은 어때요?",
                    style = ScanPangType.sectionTitle16,
                    color = ScanPangColors.OnSurfaceStrong,
                )
                Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm)) {
                    ScanPangSuggestionRow(title = "주변 할랄 식당 보기", onClick = { navController.navigate(AppRoutes.DetailNearbyHalal) })
                    ScanPangSuggestionRow(title = "주변 기도실 보기", onClick = { navController.navigate(AppRoutes.DetailNearbyPrayer) })
                    ScanPangSuggestionRow(title = "명동 인기 쇼핑몰", onClick = { })
                    ScanPangSuggestionRow(title = "외국인 인기 관광지", onClick = { })
                }
            }
        }
    }
}
