package com.scanpang.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.NoDrinks
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.scanpang.app.components.SearchResultBadgeKind
import com.scanpang.app.components.SearchResultPlaceCard
import com.scanpang.app.components.SearchResultTrustTag
import com.scanpang.app.data.remote.ScanPangViewModel
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

private val filterLabels = listOf(
    "전체",
    "HALAL MEAT",
    "SEAFOOD",
    "VEGGIE",
    "SALAM SEOUL",
)

@Composable
fun NearbyHalalRestaurantsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ScanPangViewModel = viewModel(),
) {
    var filterIndex by remember { mutableIntStateOf(0) }
    val restaurants by viewModel.restaurants.collectAsState()
    val loading by viewModel.loading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadRestaurants()
    }

    val halalTypeFilter = if (filterIndex == 0) "" else filterLabels[filterIndex]
    val visiblePlaces = if (halalTypeFilter.isEmpty()) restaurants
    else restaurants.filter { it.halal_type.equals(halalTypeFilter, ignoreCase = true) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScanPangColors.Surface,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { _ ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(ScanPangColors.Surface)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                horizontal = ScanPangDimens.screenHorizontal,
                vertical = ScanPangSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "뒤로",
                            tint = ScanPangColors.OnSurfaceStrong,
                        )
                    }
                    Text(
                        text = "주변 할랄 식당",
                        style = ScanPangType.detailScreenTitle22,
                        color = ScanPangColors.OnSurfaceStrong,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ScanPangShapes.radius14,
                    color = ScanPangColors.Background,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ScanPangDimens.searchBarHeightActive)
                            .padding(horizontal = ScanPangDimens.searchBarInnerHorizontal),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = ScanPangColors.OnSurfacePlaceholder,
                        )
                        Text(
                            text = "식당 이름 또는 메뉴 검색",
                            style = ScanPangType.caption12Medium,
                            color = ScanPangColors.OnSurfacePlaceholder,
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                ) {
                    filterLabels.forEachIndexed { index, label ->
                        val selected = index == filterIndex
                        Surface(
                            modifier = Modifier
                                .clip(ScanPangShapes.filterChip)
                                .clickable { filterIndex = index },
                            shape = ScanPangShapes.filterChip,
                            color = if (selected) ScanPangColors.Primary else ScanPangColors.Surface,
                            border = BorderStroke(
                                ScanPangDimens.borderHairline,
                                ScanPangColors.OutlineSubtle,
                            ),
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(
                                    horizontal = ScanPangSpacing.md,
                                    vertical = ScanPangDimens.chipPadVertical,
                                ),
                                style = ScanPangType.caption12Medium,
                                color = if (selected) Color.White else ScanPangColors.OnSurfaceMuted,
                            )
                        }
                    }
                }
            }
            if (loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ScanPangColors.Primary)
                    }
                }
            }
            items(
                items = visiblePlaces,
                key = { it.restaurant_id.ifEmpty { it.name_ko } },
            ) { restaurant ->
                val badgeKind = when (restaurant.halal_type.uppercase()) {
                    "HALAL MEAT", "HALAL_MEAT" -> SearchResultBadgeKind.HalalMeat
                    "SEAFOOD" -> SearchResultBadgeKind.Seafood
                    "VEGGIE" -> SearchResultBadgeKind.Veggie
                    "SALAM SEOUL", "SALAM_SEOUL" -> SearchResultBadgeKind.SalamSeoul
                    else -> SearchResultBadgeKind.HalalMeat
                }
                SearchResultPlaceCard(
                    title = restaurant.name_ko,
                    badgeKind = badgeKind,
                    badgeLabel = restaurant.halal_type.uppercase(),
                    cuisineLabel = restaurant.cuisine,
                    distance = "${restaurant.distance_m}m",
                    isOpen = true,
                    trustTags = buildList {
                        add(SearchResultTrustTag("할랄 인증", Icons.Rounded.Verified))
                        if (restaurant.muslim_cooks_available) add(SearchResultTrustTag("무슬림 조리사", Icons.Rounded.Restaurant))
                        if (restaurant.no_alcohol_sales) add(SearchResultTrustTag("주류 미판매", Icons.Rounded.NoDrinks))
                    },
                    onClick = {
                        navController.navigate(AppRoutes.RestaurantDetail) { launchSingleTop = true }
                    },
                )
            }
        }
    }
}
