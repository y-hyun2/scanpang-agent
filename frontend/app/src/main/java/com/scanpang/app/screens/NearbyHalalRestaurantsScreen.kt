package com.scanpang.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
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
import com.scanpang.app.data.DummyData
import com.scanpang.app.data.RestaurantPlace
import com.scanpang.app.data.remote.ScanPangViewModel
import com.scanpang.app.data.toRestaurantPlace
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

private data class NearbyHalalPlace(
    val title: String,
    val categoryFilter: String,
    val badgeKind: SearchResultBadgeKind,
    val badgeLabel: String,
    val cuisineLabel: String,
    val distance: String,
    val isOpen: Boolean,
    val trustTags: List<SearchResultTrustTag>,
)

private fun RestaurantPlace.toNearbyHalalPlace(): NearbyHalalPlace {
    val place = this.place
    val badgeKind = when (halalCategory) {
        "HALAL MEAT" -> SearchResultBadgeKind.HalalMeat
        "SEAFOOD" -> SearchResultBadgeKind.Seafood
        "VEGGIE" -> SearchResultBadgeKind.Veggie
        "SALAM SEOUL" -> SearchResultBadgeKind.SalamSeoul
        else -> SearchResultBadgeKind.HalalMeat
    }
    val trustTags = place.tags.map { tag ->
        val icon = when {
            tag.contains("인증") || tag.contains("살람") -> Icons.Rounded.Verified
            tag.contains("추천") -> Icons.Rounded.Star
            else -> Icons.Rounded.Verified
        }
        SearchResultTrustTag(tag, icon)
    }
    val cuisineLabel = place.subCategory.ifBlank { "한식" }
    return NearbyHalalPlace(
        title = place.name,
        categoryFilter = halalCategory,
        badgeKind = badgeKind,
        badgeLabel = halalCategory,
        cuisineLabel = cuisineLabel,
        distance = place.distance,
        isOpen = place.isOpen,
        trustTags = trustTags,
    )
}

/**
 * Figma: 주변 할랄 식당 (`290:2034`)
 */
@Composable
fun NearbyHalalRestaurantsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val viewModel: ScanPangViewModel = viewModel()
    val apiRestaurants by viewModel.restaurants.collectAsState()
    val isLoading by viewModel.loading.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadRestaurants() }

    var filterIndex by remember { mutableIntStateOf(0) }

    val allPlaces = remember(apiRestaurants) {
        val fromApi = apiRestaurants.map { it.toRestaurantPlace().toNearbyHalalPlace() }
        fromApi.ifEmpty { DummyData.halalRestaurants.map { it.toNearbyHalalPlace() } }
    }

    val visiblePlaces = remember(filterIndex, allPlaces) {
        if (filterIndex == 0) allPlaces
        else {
            val key = filterLabels[filterIndex]
            allPlaces.filter { it.categoryFilter == key }
        }
    }

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
            if (isLoading && allPlaces.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ScanPangColors.Primary)
                    }
                }
            }
            items(
                items = visiblePlaces,
                key = { it.title + it.distance + it.badgeLabel },
            ) { place ->
                SearchResultPlaceCard(
                    title = place.title,
                    badgeKind = place.badgeKind,
                    badgeLabel = place.badgeLabel,
                    cuisineLabel = place.cuisineLabel,
                    distance = place.distance,
                    isOpen = place.isOpen,
                    trustTags = place.trustTags,
                    onClick = {
                        // 통합 PlaceDetailScreen 으로 — id 없으니 DummyData 1번째로 폴백.
                        // Phase B 에서 backend store_details.id 를 알게 되면 그걸로 교체.
                        navController.navigate(
                            AppRoutes.placeDetailRoute("restaurant", "")
                        ) { launchSingleTop = true }
                    },
                )
            }
        }
    }
}
