package com.scanpang.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.scanpang.app.data.DummyData
import com.scanpang.app.data.Place
import com.scanpang.app.data.RestaurantPlace
import com.scanpang.app.data.remote.NavCandidate
import com.scanpang.app.data.remote.ScanPangViewModel
import com.scanpang.app.ui.theme.ScanPangType
import com.scanpang.app.components.ScanPangSearchFieldFilled
import com.scanpang.app.components.SearchResultBadgeKind
import com.scanpang.app.components.SearchResultPlaceCard
import com.scanpang.app.components.SearchResultTrustTag
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangSpacing

private data class ResultItem(
    val id: String,
    val title: String,
    val badgeKind: SearchResultBadgeKind,
    val badgeLabel: String,
    val cuisine: String,
    val distance: String,
    val isOpen: Boolean,
    val trust: List<SearchResultTrustTag>,
)

private data class ResultRow(
    val id: String,
    val item: ResultItem,
    val detailRoute: String,
)

private fun RestaurantPlace.toRestaurantResultItem(): ResultItem {
    val p = place
    val badgeKind = when (halalCategory) {
        "HALAL MEAT" -> SearchResultBadgeKind.HalalMeat
        "SEAFOOD" -> SearchResultBadgeKind.Seafood
        "VEGGIE" -> SearchResultBadgeKind.Veggie
        "SALAM SEOUL" -> SearchResultBadgeKind.SalamSeoul
        else -> SearchResultBadgeKind.HalalMeat
    }
    val trust = p.tags.map { tag ->
        val icon = when {
            tag.contains("인증") || tag.contains("살람") -> Icons.Rounded.Verified
            tag.contains("추천") -> Icons.Rounded.Star
            else -> Icons.Rounded.Verified
        }
        SearchResultTrustTag(tag, icon)
    }
    return ResultItem(
        id = p.id,
        title = p.name,
        badgeKind = badgeKind,
        badgeLabel = halalCategory,
        cuisine = p.subCategory.ifBlank { "한식" },
        distance = p.distance,
        isOpen = p.isOpen,
        trust = trust,
    )
}

private fun Place.toGenericResultItem(): ResultItem {
    val secondary = subCategory.ifBlank { tags.firstOrNull() ?: "—" }
    val trust = tags.take(4).map { tag ->
        SearchResultTrustTag(tag, Icons.Rounded.Star)
    }
    return ResultItem(
        id = id,
        title = name,
        badgeKind = SearchResultBadgeKind.General,
        badgeLabel = category,
        cuisine = secondary,
        distance = distance,
        isOpen = isOpen,
        trust = trust,
    )
}

private fun restaurantMatchesQuery(rp: RestaurantPlace, raw: String): Boolean {
    val q = raw.trim()
    if (q.isEmpty()) return true
    val p = rp.place
    return p.name.contains(q, ignoreCase = true) ||
        p.description.contains(q, ignoreCase = true) ||
        p.address.contains(q, ignoreCase = true) ||
        p.tags.any { it.contains(q, ignoreCase = true) } ||
        p.category.contains(q, ignoreCase = true) ||
        p.subCategory.contains(q, ignoreCase = true) ||
        rp.halalCategory.contains(q, ignoreCase = true) ||
        rp.menuItems.any { it.name.contains(q, ignoreCase = true) }
}

private fun placeMatchesQuery(p: Place, raw: String): Boolean {
    val q = raw.trim()
    if (q.isEmpty()) return true
    return p.name.contains(q, ignoreCase = true) ||
        p.description.contains(q, ignoreCase = true) ||
        p.address.contains(q, ignoreCase = true) ||
        p.category.contains(q, ignoreCase = true) ||
        p.subCategory.contains(q, ignoreCase = true) ||
        p.tags.any { it.contains(q, ignoreCase = true) }
}

private fun NavCandidate.toResultRow(): ResultRow {
    val item = ResultItem(
        id = poi_id,
        title = name,
        badgeKind = SearchResultBadgeKind.General,
        badgeLabel = "장소",
        cuisine = address.take(30),
        distance = "",
        isOpen = true,
        trust = if (recommended) listOf(SearchResultTrustTag("추천", Icons.Rounded.Star)) else emptyList(),
    )
    // 카테고리 키워드로 적절한 상세 화면 결정
    val route = when {
        name.contains("성당") || name.contains("타워") || name.contains("궁") || name.contains("박물관") -> AppRoutes.TouristDetail
        name.contains("약국") -> AppRoutes.PharmacyDetail
        name.contains("병원") || name.contains("의원") -> AppRoutes.HospitalDetail
        name.contains("은행") -> AppRoutes.BankDetail
        name.contains("환전") -> AppRoutes.ExchangeDetail
        name.contains("카페") || name.contains("커피") || name.contains("스타벅스") -> AppRoutes.CafeDetail
        name.contains("편의점") || name.contains("GS25") || name.contains("CU") || name.contains("세븐") -> AppRoutes.ConvenienceDetail
        name.contains("역") && (name.contains("지하철") || name.length <= 6) -> AppRoutes.SubwayDetail
        name.contains("ATM") -> AppRoutes.AtmDetail
        name.contains("화장실") -> AppRoutes.RestroomDetail
        name.contains("보관") || name.contains("로커") -> AppRoutes.LockersDetail
        name.contains("쇼핑") || name.contains("백화점") || name.contains("몰") || name.contains("마트") -> AppRoutes.ShoppingDetail
        else -> AppRoutes.restaurantDetailRoute(name, address)
    }
    return ResultRow(
        id = "nav:$poi_id",
        item = item,
        detailRoute = route,
    )
}

private fun buildAllSearchRows(query: String): List<ResultRow> = buildList {
    DummyData.halalRestaurants.forEach { rp ->
        if (restaurantMatchesQuery(rp, query)) {
            add(
                ResultRow(
                    id = "restaurant:${rp.place.id}",
                    item = rp.toRestaurantResultItem(),
                    detailRoute = AppRoutes.RestaurantDetail,
                ),
            )
        }
    }
    fun addPlaces(places: List<Place>, route: String, prefix: String) {
        places.forEach { p ->
            if (placeMatchesQuery(p, query)) {
                add(
                    ResultRow(
                        id = "$prefix:${p.id}",
                        item = p.toGenericResultItem(),
                        detailRoute = route,
                    ),
                )
            }
        }
    }
    addPlaces(DummyData.prayerRooms, AppRoutes.PrayerRoomDetail, "prayer")
    addPlaces(DummyData.cafes, AppRoutes.CafeDetail, "cafe")
    addPlaces(DummyData.shoppingPlaces, AppRoutes.ShoppingDetail, "shopping")
    addPlaces(DummyData.convenienceStores, AppRoutes.ConvenienceDetail, "cv")
    addPlaces(DummyData.atmPlaces, AppRoutes.AtmDetail, "atm")
    addPlaces(DummyData.bankPlaces, AppRoutes.BankDetail, "bank")
    addPlaces(DummyData.exchangePlaces, AppRoutes.ExchangeDetail, "ex")
    addPlaces(DummyData.subwayPlaces, AppRoutes.SubwayDetail, "sub")
    addPlaces(DummyData.restroomPlaces, AppRoutes.RestroomDetail, "rest")
    addPlaces(DummyData.lockerPlaces, AppRoutes.LockersDetail, "lock")
    addPlaces(DummyData.hospitalPlaces, AppRoutes.HospitalDetail, "hosp")
    addPlaces(DummyData.pharmacyPlaces, AppRoutes.PharmacyDetail, "ph")
    addPlaces(DummyData.touristPlaces, AppRoutes.TouristDetail, "tour")
}

@Composable
fun SearchResultsScreen(
    navController: NavController,
    searchQuery: String,
    modifier: Modifier = Modifier,
) {
    val navQueryTrimmed = searchQuery.trim()
    var barClearedVisually by remember(searchQuery) { mutableStateOf(false) }
    val barQueryText = when {
        barClearedVisually -> ""
        navQueryTrimmed.isEmpty() -> "검색"
        else -> navQueryTrimmed
    }
    val resultCaptionQuery = when {
        barClearedVisually -> ""
        navQueryTrimmed.isEmpty() -> "검색"
        else -> navQueryTrimmed
    }

    val viewModel: ScanPangViewModel = viewModel()
    val navSearchResult by viewModel.navSearchResult.collectAsState()
    val isLoading by viewModel.loading.collectAsState()

    LaunchedEffect(navQueryTrimmed) {
        if (navQueryTrimmed.isNotBlank()) {
            viewModel.searchNavigation(navQueryTrimmed, 37.5636, 126.9822)
        }
    }

    fun signalSearchTabClear() {
        runCatching {
            navController.getBackStackEntry(AppRoutes.Search).savedStateHandle[AppRoutes.SearchSavedStateClearQueryKey] =
                true
        }
    }

    val resultRows = remember(navQueryTrimmed, navSearchResult) {
        val apiRows = navSearchResult?.candidates?.map { it.toResultRow() } ?: emptyList()
        apiRows.ifEmpty { buildAllSearchRows(navQueryTrimmed) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScanPangColors.Background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { _ ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(ScanPangColors.Background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(ScanPangDimens.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.lg),
        ) {
            item {
                ScanPangSearchFieldFilled(
                    query = barQueryText,
                    onSearchBarClick = {
                        signalSearchTabClear()
                        navController.popBackStack(AppRoutes.Search, false)
                    },
                    onClearClick = {
                        signalSearchTabClear()
                        barClearedVisually = true
                    },
                    hintWhenBlank = if (barClearedVisually) {
                        "지도 검색 · 장소명, 주소, 카테고리"
                    } else {
                        null
                    },
                )
            }
            item {
                Text(
                    text = "‘${resultCaptionQuery.ifEmpty { "검색" }}’ 검색 결과 ${resultRows.size}개",
                    style = ScanPangType.link13,
                    color = ScanPangColors.OnSurfaceMuted,
                )
            }
            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ScanPangColors.Primary)
                    }
                }
            }
            if (resultRows.isEmpty() && !isLoading) {
                item {
                    Text(
                        text = "조건에 맞는 장소가 없습니다. 다른 검색어를 시도해 보세요.",
                        style = ScanPangType.body15Medium,
                        color = ScanPangColors.OnSurfaceMuted,
                        modifier = Modifier.padding(top = ScanPangSpacing.md),
                    )
                }
            } else {
                items(
                    items = resultRows,
                    key = { it.id },
                ) { row ->
                    val r = row.item
                    SearchResultPlaceCard(
                        title = r.title,
                        badgeKind = r.badgeKind,
                        badgeLabel = r.badgeLabel,
                        cuisineLabel = r.cuisine,
                        distance = r.distance,
                        isOpen = r.isOpen,
                        trustTags = r.trust,
                        onClick = {
                            navController.navigate(row.detailRoute) { launchSingleTop = true }
                        },
                    )
                }
            }
        }
    }
}
