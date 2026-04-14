package com.scanpang.app.navigation

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.hufs.arnavigation_com.ArNavigationActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.scanpang.app.screens.HomeScreen
import com.scanpang.app.screens.ProfileScreen
import com.scanpang.app.screens.QiblaDirectionScreen
import com.scanpang.app.screens.SavedPlacesScreen
import com.scanpang.app.screens.SearchResultsScreen
import com.scanpang.app.screens.SearchScreen
import com.scanpang.app.screens.ar.ArExploreChatKeyboardScreen
import com.scanpang.app.screens.ar.ArExploreDefaultScreen
import com.scanpang.app.screens.ar.ArExploreFilterScreen
import com.scanpang.app.screens.ar.ArExploreFreezeScreen
import com.scanpang.app.screens.ar.ArExploreRecommendedScreen
import com.scanpang.app.screens.ar.ArExploreSearchOverlayScreen
import com.scanpang.app.screens.ar.ArNavigationAgentScreen
import com.scanpang.app.screens.ar.ArNavigationArrivedScreen
import com.scanpang.app.screens.ar.ArNavigationArrivalSoonScreen
import com.scanpang.app.screens.ar.ArNavigationMapScreen
import com.scanpang.app.screens.detail.ArPlaceDetailAiGuideScreen
import com.scanpang.app.screens.detail.ArPlaceDetailBuildingScreen
import com.scanpang.app.screens.detail.ArPlaceDetailFloorsScreen
import com.scanpang.app.screens.detail.ArStoreDetailOverlayScreen
import com.scanpang.app.screens.detail.NearbyHalalRestaurantsScreen
import com.scanpang.app.screens.detail.NearbyPrayerRoomsScreen
import com.scanpang.app.screens.detail.PrayerRoomDetailScreen
import com.scanpang.app.screens.detail.RestaurantDetailScreen

object AppRoutes {
    const val Home = "home"
    const val Qibla = "qibla"
    const val Search = "search"
    const val SearchResults = "search_results"
    const val Saved = "saved"
    const val Profile = "profile"
    const val ArDefault = "ar_default"
    const val ArFilter = "ar_filter"
    const val ArSearch = "ar_search"
    const val ArChatKeyboard = "ar_chat_keyboard"
    const val ArRecommended = "ar_recommended"
    const val ArFreeze = "ar_freeze"
    const val ArNavMap = "ar_nav_map"
    const val ArNavAgent = "ar_nav_agent"
    const val ArNavArrivalSoon = "ar_nav_arrival_soon"
    const val ArNavArrived = "ar_nav_arrived"
    const val DetailArPlaceBuilding = "detail_ar_place_building"
    const val DetailArPlaceFloors = "detail_ar_place_floors"
    const val DetailArPlaceAi = "detail_ar_place_ai"
    const val DetailArStore = "detail_ar_store"
    const val DetailNearbyHalal = "detail_nearby_halal"
    const val DetailNearbyPrayer = "detail_nearby_prayer"
    const val DetailRestaurant = "detail_restaurant"
    const val DetailPrayerRoom = "detail_prayer_room"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = AppRoutes.Home,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(AppRoutes.Home) {
            HomeScreen(navController = navController)
        }
        composable(AppRoutes.Qibla) {
            QiblaDirectionScreen(navController = navController)
        }
        composable(AppRoutes.Search) {
            SearchScreen(navController = navController)
        }
        composable(AppRoutes.SearchResults) {
            SearchResultsScreen(navController = navController)
        }
        composable(AppRoutes.Saved) {
            SavedPlacesScreen(navController = navController)
        }
        composable(AppRoutes.Profile) {
            ProfileScreen(navController = navController)
        }
        composable(AppRoutes.ArDefault) {
            ArExploreDefaultScreen(navController = navController)
        }
        composable(AppRoutes.ArFilter) {
            ArExploreFilterScreen(navController = navController)
        }
        composable(AppRoutes.ArSearch) {
            ArExploreSearchOverlayScreen(navController = navController)
        }
        composable(AppRoutes.ArChatKeyboard) {
            ArExploreChatKeyboardScreen(navController = navController)
        }
        composable(AppRoutes.ArRecommended) {
            ArExploreRecommendedScreen(navController = navController)
        }
        composable(AppRoutes.ArFreeze) {
            ArExploreFreezeScreen(navController = navController)
        }
        composable(AppRoutes.ArNavMap) {
            // AR Navigation Activity 실행 (같은 앱 내)
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                context.startActivity(Intent(context, ArNavigationActivity::class.java))
            }
            // 돌아왔을 때 이전 화면으로
            ArNavigationMapScreen(navController = navController)
        }
        composable(AppRoutes.ArNavAgent) {
            ArNavigationAgentScreen(navController = navController)
        }
        composable(AppRoutes.ArNavArrivalSoon) {
            ArNavigationArrivalSoonScreen(navController = navController)
        }
        composable(AppRoutes.ArNavArrived) {
            ArNavigationArrivedScreen(navController = navController)
        }
        composable(AppRoutes.DetailArPlaceBuilding) {
            ArPlaceDetailBuildingScreen(navController = navController)
        }
        composable(AppRoutes.DetailArPlaceFloors) {
            ArPlaceDetailFloorsScreen(navController = navController)
        }
        composable(AppRoutes.DetailArPlaceAi) {
            ArPlaceDetailAiGuideScreen(navController = navController)
        }
        composable(AppRoutes.DetailArStore) {
            ArStoreDetailOverlayScreen(navController = navController)
        }
        composable(AppRoutes.DetailNearbyHalal) {
            NearbyHalalRestaurantsScreen(navController = navController)
        }
        composable(AppRoutes.DetailNearbyPrayer) {
            NearbyPrayerRoomsScreen(navController = navController)
        }
        composable(AppRoutes.DetailRestaurant) {
            RestaurantDetailScreen(navController = navController)
        }
        composable(AppRoutes.DetailPrayerRoom) {
            PrayerRoomDetailScreen(navController = navController)
        }
    }
}
