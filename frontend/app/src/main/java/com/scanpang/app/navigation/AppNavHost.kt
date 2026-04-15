package com.scanpang.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import com.scanpang.app.screens.HomeScreen
import com.scanpang.app.screens.SplashScreen
import com.scanpang.app.screens.onboarding.OnboardingLanguageScreen
import com.scanpang.app.screens.onboarding.OnboardingNameScreen
import com.scanpang.app.screens.onboarding.OnboardingPreferenceScreen
import com.scanpang.app.screens.NearbyHalalRestaurantsScreen
import com.scanpang.app.screens.NearbyPrayerRoomsScreen
import com.scanpang.app.screens.PrayerRoomDetailScreen
import com.scanpang.app.screens.ProfileScreen
import com.scanpang.app.screens.QiblaDirectionScreen
import com.scanpang.app.screens.RestaurantDetailScreen
import com.scanpang.app.screens.SavedPlacesScreen
import com.scanpang.app.screens.SearchDefaultScreen
import com.scanpang.app.screens.SearchResultsScreen
import com.scanpang.app.screens.ar.ArExploreScreen
import com.scanpang.app.screens.ar.ArNavigationMapScreen

object AppRoutes {
    const val Splash = "splash"
    const val OnboardingLanguage = "onboarding_language"
    const val OnboardingName = "onboarding_name"
    const val OnboardingPreference = "onboarding_preference"

    const val Home = "home"
    const val Qibla = "qibla"
    const val Search = "search"
    const val SearchResults = "search_results"

    fun searchResultsRoute(query: String): String {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        return "$SearchResults/$encoded"
    }

    const val Saved = "saved"
    const val Profile = "profile"
    const val NearbyHalal = "nearby_halal"
    const val NearbyPrayer = "nearby_prayer"
    const val RestaurantDetail = "restaurant_detail"
    fun restaurantDetailRoute(name: String, address: String = ""): String {
        val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
        val encodedAddr = URLEncoder.encode(address, StandardCharsets.UTF_8.name())
        return "$RestaurantDetail/$encodedName/$encodedAddr"
    }
    const val PrayerRoomDetail = "prayer_room_detail"
    const val ArExplore = "ar_explore"
    const val ArNavMap = "ar_nav_map"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = AppRoutes.Splash,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(AppRoutes.Splash) {
            SplashScreen(navController = navController)
        }
        composable(AppRoutes.OnboardingLanguage) {
            OnboardingLanguageScreen(navController = navController)
        }
        composable(AppRoutes.OnboardingName) {
            OnboardingNameScreen(navController = navController)
        }
        composable(AppRoutes.OnboardingPreference) {
            OnboardingPreferenceScreen(navController = navController)
        }
        composable(AppRoutes.Home) {
            HomeScreen(navController = navController)
        }
        composable(AppRoutes.Qibla) {
            QiblaDirectionScreen(navController = navController)
        }
        composable(AppRoutes.Search) {
            SearchDefaultScreen(navController = navController)
        }
        composable(
            route = "${AppRoutes.SearchResults}/{query}",
            arguments = listOf(
                navArgument("query") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val raw = entry.arguments?.getString("query").orEmpty()
            val query = runCatching {
                URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
            }.getOrDefault(raw)
            SearchResultsScreen(
                navController = navController,
                searchQuery = query,
            )
        }
        composable(AppRoutes.Saved) {
            SavedPlacesScreen(navController = navController)
        }
        composable(AppRoutes.Profile) {
            ProfileScreen(navController = navController)
        }
        composable(AppRoutes.NearbyHalal) {
            NearbyHalalRestaurantsScreen(navController = navController)
        }
        composable(AppRoutes.NearbyPrayer) {
            NearbyPrayerRoomsScreen(navController = navController)
        }
        composable(AppRoutes.RestaurantDetail) {
            RestaurantDetailScreen(navController = navController, placeName = "", placeAddress = "")
        }
        composable(
            route = "${AppRoutes.RestaurantDetail}/{name}/{address}",
            arguments = listOf(
                navArgument("name") { type = NavType.StringType; defaultValue = "" },
                navArgument("address") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val name = runCatching { URLDecoder.decode(entry.arguments?.getString("name").orEmpty(), StandardCharsets.UTF_8.name()) }.getOrDefault("")
            val address = runCatching { URLDecoder.decode(entry.arguments?.getString("address").orEmpty(), StandardCharsets.UTF_8.name()) }.getOrDefault("")
            RestaurantDetailScreen(navController = navController, placeName = name, placeAddress = address)
        }
        composable(AppRoutes.PrayerRoomDetail) {
            PrayerRoomDetailScreen(navController = navController)
        }
        composable(AppRoutes.ArExplore) {
            ArExploreScreen(navController = navController)
        }
        composable(AppRoutes.ArNavMap) {
            ArNavigationMapScreen(navController = navController)
        }
    }
}
