package com.scanpang.app.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.scanpang.app.components.ScanPangTabBar
import com.scanpang.app.components.tabIdForRoute
import com.scanpang.app.screens.ar.ArExploreScreen
import com.scanpang.app.screens.common.PlaceholderScreen
import com.scanpang.app.screens.detail.PrayerRoomDetailScreen
import com.scanpang.app.screens.detail.RestaurantDetailScreen
import com.scanpang.app.screens.halal.NearbyHalalRestaurantsScreen
import com.scanpang.app.screens.halal.NearbyPrayerRoomsScreen
import com.scanpang.app.screens.home.HomeScreen
import com.scanpang.app.screens.profile.ProfileScreen
import com.scanpang.app.screens.saved.SavedPlacesScreen
import com.scanpang.app.screens.search.SearchDefaultScreen
import com.scanpang.app.screens.search.SearchResultsScreen

@Composable
fun ScanPangRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val showTabBar = route?.startsWith("ar_") != true
    val selectedTab = tabIdForRoute(route)

    Scaffold(
        bottomBar = {
            if (showTabBar) {
                Column(Modifier.navigationBarsPadding()) {
                    ScanPangTabBar(
                        activeTab = selectedTab,
                        onHomePress = {
                            navController.navigateToTab(TabRoutes.HOME_TAB)
                        },
                        onSearchPress = {
                            navController.navigateToTab(TabRoutes.SEARCH_TAB)
                        },
                        onExplorePress = {
                            navController.navigateToTab(TabRoutes.AR_TAB)
                        },
                        onSavePress = {
                            navController.navigateToTab(TabRoutes.SAVED_TAB)
                        },
                        onProfilePress = {
                            navController.navigateToTab(TabRoutes.PROFILE_TAB)
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        ScanPangNavHost(
            navController = navController,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@Composable
fun ScanPangNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TabRoutes.HOME_TAB,
        modifier = modifier,
    ) {
        navigation(
            route = TabRoutes.HOME_TAB,
            startDestination = HomeRoutes.MAIN,
        ) {
            composable(HomeRoutes.MAIN) {
                HomeScreen(navController = navController)
            }
            composable(HomeRoutes.QIBLA) {
                PlaceholderScreen(
                    title = "키블라 방향",
                    onBack = { navController.popBackStack() },
                )
            }
            composable(HomeRoutes.NEARBY_HALAL) {
                NearbyHalalRestaurantsScreen(
                    navController = navController,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(HomeRoutes.NEARBY_PRAYER) {
                NearbyPrayerRoomsScreen(
                    navController = navController,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "${HomeRoutes.RESTAURANT_DETAIL}/{title}",
                arguments = listOf(
                    navArgument("title") { type = NavType.StringType },
                ),
            ) { entry ->
                val enc = entry.arguments?.getString("title").orEmpty()
                RestaurantDetailScreen(
                    title = Uri.decode(enc),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "${HomeRoutes.PRAYER_ROOM_DETAIL}/{title}",
                arguments = listOf(
                    navArgument("title") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                val enc = entry.arguments?.getString("title").orEmpty()
                PrayerRoomDetailScreen(
                    title = Uri.decode(enc),
                    onBack = { navController.popBackStack() },
                )
            }
        }

        navigation(
            route = TabRoutes.SEARCH_TAB,
            startDestination = SearchRoutes.MAIN,
        ) {
            composable(SearchRoutes.MAIN) {
                SearchDefaultScreen(navController = navController)
            }
            composable(
                route = "${SearchRoutes.RESULTS}/{query}",
                arguments = listOf(navArgument("query") { type = NavType.StringType }),
            ) { entry ->
                val q = Uri.decode(entry.arguments?.getString("query").orEmpty())
                SearchResultsScreen(query = q, navController = navController)
            }
            composable(
                route = "${SearchRoutes.RESTAURANT_DETAIL}/{title}",
                arguments = listOf(navArgument("title") { type = NavType.StringType }),
            ) { entry ->
                val enc = entry.arguments?.getString("title").orEmpty()
                RestaurantDetailScreen(
                    title = Uri.decode(enc),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "${SearchRoutes.PRAYER_ROOM_DETAIL}/{title}",
                arguments = listOf(navArgument("title") { type = NavType.StringType }),
            ) { entry ->
                val enc = entry.arguments?.getString("title").orEmpty()
                PrayerRoomDetailScreen(
                    title = Uri.decode(enc),
                    onBack = { navController.popBackStack() },
                )
            }
        }

        navigation(
            route = TabRoutes.AR_TAB,
            startDestination = ArRoutes.EXPLORE,
        ) {
            composable(ArRoutes.EXPLORE) {
                ArExploreScreen(navController = navController)
            }
            composable(ArRoutes.CHAT) {
                PlaceholderScreen(
                    title = "AR 탐색 채팅",
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ArRoutes.RECOMMENDED) {
                PlaceholderScreen(
                    title = "추천 결과",
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ArRoutes.NAV_MAP) {
                PlaceholderScreen(
                    title = "길 안내 지도",
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ArRoutes.NAV_AGENT) {
                PlaceholderScreen(
                    title = "길 안내 에이전트",
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ArRoutes.ALMOST_ARRIVAL) {
                PlaceholderScreen(
                    title = "거의 도착",
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ArRoutes.ARRIVED) {
                PlaceholderScreen(
                    title = "도착",
                    onBack = { navController.popBackStack() },
                )
            }
        }

        navigation(
            route = TabRoutes.SAVED_TAB,
            startDestination = SavedRoutes.MAIN,
        ) {
            composable(SavedRoutes.MAIN) {
                SavedPlacesScreen()
            }
            composable(
                route = "${SavedRoutes.RESTAURANT_DETAIL}/{title}",
                arguments = listOf(navArgument("title") { type = NavType.StringType }),
            ) { entry ->
                val enc = entry.arguments?.getString("title").orEmpty()
                RestaurantDetailScreen(
                    title = Uri.decode(enc),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = "${SavedRoutes.PRAYER_ROOM_DETAIL}/{title}",
                arguments = listOf(navArgument("title") { type = NavType.StringType }),
            ) { entry ->
                val enc = entry.arguments?.getString("title").orEmpty()
                PrayerRoomDetailScreen(
                    title = Uri.decode(enc),
                    onBack = { navController.popBackStack() },
                )
            }
        }

        navigation(
            route = TabRoutes.PROFILE_TAB,
            startDestination = ProfileRoutes.MAIN,
        ) {
            composable(ProfileRoutes.MAIN) {
                ProfileScreen()
            }
        }
    }
}
