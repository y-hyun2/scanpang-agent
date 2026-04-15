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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.scanpang.app.ui.theme.ScanPangType
import com.scanpang.app.components.ScanPangSearchFieldFilled
import com.scanpang.app.components.SearchResultBadgeKind
import com.scanpang.app.components.SearchResultPlaceCard
import com.scanpang.app.components.SearchResultTrustTag
import com.scanpang.app.data.remote.ScanPangViewModel
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangSpacing

@Composable
fun SearchResultsScreen(
    navController: NavController,
    searchQuery: String,
    modifier: Modifier = Modifier,
    viewModel: ScanPangViewModel = viewModel(),
) {
    val displayQuery = searchQuery.trim().ifEmpty { "검색" }
    val navSearchResult by viewModel.navSearchResult.collectAsState()
    val loading by viewModel.loading.collectAsState()

    LaunchedEffect(displayQuery) {
        if (displayQuery != "검색") {
            viewModel.searchNavigation(displayQuery, 37.5636, 126.9822)
        }
    }

    val candidates = navSearchResult?.candidates ?: emptyList()

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
                    query = displayQuery,
                    onClearClick = { navController.popBackStack() },
                )
            }
            item {
                Text(
                    text = "'$displayQuery' 검색 결과 ${candidates.size}개",
                    style = ScanPangType.link13,
                    color = ScanPangColors.OnSurfaceMuted,
                )
            }
            if (loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ScanPangColors.Primary)
                    }
                }
            }
            items(candidates) { candidate ->
                SearchResultPlaceCard(
                    title = candidate.name,
                    badgeKind = if (candidate.recommended) SearchResultBadgeKind.HalalMeat else SearchResultBadgeKind.Seafood,
                    badgeLabel = if (candidate.recommended) "추천" else "",
                    cuisineLabel = candidate.address,
                    distance = "",
                    isOpen = true,
                    trustTags = emptyList(),
                    onClick = {
                        navController.navigate(
                            AppRoutes.restaurantDetailRoute(candidate.name, candidate.address)
                        ) { launchSingleTop = true }
                    },
                )
            }
        }
    }
}
