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
import com.scanpang.app.screens.auth.LoginErrorScreen
import com.scanpang.app.screens.auth.LoginScreen
import com.scanpang.app.screens.auth.OAuthLoadingScreen
import com.scanpang.app.screens.auth.TermsAgreementScreen
import com.scanpang.app.screens.auth.WithdrawalScreen
import com.scanpang.app.screens.onboarding.OnboardingLanguageScreen
import com.scanpang.app.screens.onboarding.OnboardingNameScreen
import com.scanpang.app.screens.onboarding.OnboardingPreferenceScreen
import com.scanpang.app.screens.NearbyHalalRestaurantsScreen
import com.scanpang.app.screens.NearbyPrayerRoomsScreen
import com.scanpang.app.screens.AtmDetailScreen
import com.scanpang.app.screens.BankDetailScreen
import com.scanpang.app.screens.CafeDetailScreen
import com.scanpang.app.screens.ConvenienceStoreDetailScreen
import com.scanpang.app.screens.ExchangeDetailScreen
import com.scanpang.app.screens.HospitalDetailScreen
import com.scanpang.app.screens.LockersDetailScreen
import com.scanpang.app.screens.PharmacyDetailScreen
import com.scanpang.app.screens.PrayerRoomDetailScreen
import com.scanpang.app.screens.ProfileScreen
import com.scanpang.app.screens.QiblaDirectionScreen
import com.scanpang.app.screens.RestaurantDetailScreen
import com.scanpang.app.screens.RestroomDetailScreen
import com.scanpang.app.screens.SavedPlacesScreen
import com.scanpang.app.screens.ShoppingDetailScreen
import com.scanpang.app.screens.SubwayDetailScreen
import com.scanpang.app.screens.TouristSpotDetailScreen
import com.scanpang.app.screens.SearchDefaultScreen
import com.scanpang.app.screens.SearchResultsScreen
import com.scanpang.app.screens.ar.ArDebugPreviewScreen
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

    const val SearchSavedStateClearQueryKey = "clear_search_query"

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
    fun prayerRoomDetailRoute(name: String): String {
        val encoded = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
        return "$PrayerRoomDetail/$encoded"
    }
    const val TouristDetail = "tourist_detail"
    const val ShoppingDetail = "shopping_detail"
    const val ConvenienceDetail = "convenience_detail"
    const val CafeDetail = "cafe_detail"
    const val AtmDetail = "atm_detail"
    const val BankDetail = "bank_detail"
    const val ExchangeDetail = "exchange_detail"
    const val SubwayDetail = "subway_detail"
    const val RestroomDetail = "restroom_detail"
    const val LockersDetail = "lockers_detail"
    const val HospitalDetail = "hospital_detail"
    const val PharmacyDetail = "pharmacy_detail"
    const val ArExplore = "ar_explore"
    const val ArNavMap = "ar_nav_map"
    const val ArDebugPreview = "ar_debug_preview"
    fun arNavMapRoute(destName: String = ""): String {
        val encoded = URLEncoder.encode(destName, StandardCharsets.UTF_8.name())
        return "$ArNavMap/$encoded"
    }

    const val Login = "login"
    const val TermsAgreement = "terms_agreement"
    /** 라우트 패턴 — 호출 시 [oauthLoadingRoute] 사용 */
    const val OAuthLoading = "oauth_loading?provider={provider}"
    const val OAuthLoadingArgProvider = "provider"
    const val LoginError = "login_error"
    const val Withdrawal = "withdrawal"

    /** OAuth Loading 화면으로 이동할 때 provider 식별자를 함께 전달 */
    fun oauthLoadingRoute(provider: String): String = "oauth_loading?provider=$provider"
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
        composable(AppRoutes.Splash) { SplashScreen(navController = navController) }
        composable(AppRoutes.OnboardingLanguage) { OnboardingLanguageScreen(navController = navController) }
        composable(AppRoutes.OnboardingName) { OnboardingNameScreen(navController = navController) }
        composable(AppRoutes.OnboardingPreference) { OnboardingPreferenceScreen(navController = navController) }
        composable(AppRoutes.Home) { HomeScreen(navController = navController) }
        composable(AppRoutes.Qibla) { QiblaDirectionScreen(navController = navController) }
        composable(AppRoutes.Search) { SearchDefaultScreen(navController = navController) }
        composable(
            route = "${AppRoutes.SearchResults}/{query}",
            arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            val raw = entry.arguments?.getString("query").orEmpty()
            val query = runCatching { URLDecoder.decode(raw, StandardCharsets.UTF_8.name()) }.getOrDefault(raw)
            SearchResultsScreen(navController = navController, searchQuery = query)
        }
        composable(AppRoutes.Saved) { SavedPlacesScreen(navController = navController) }
        composable(AppRoutes.Profile) { ProfileScreen(navController = navController) }
        composable(AppRoutes.NearbyHalal) { NearbyHalalRestaurantsScreen(navController = navController) }
        composable(AppRoutes.NearbyPrayer) { NearbyPrayerRoomsScreen(navController = navController) }
        composable(AppRoutes.RestaurantDetail) { RestaurantDetailScreen(navController = navController) }
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
        composable(AppRoutes.PrayerRoomDetail) { PrayerRoomDetailScreen(navController = navController) }
        composable(
            route = "${AppRoutes.PrayerRoomDetail}/{name}",
            arguments = listOf(navArgument("name") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            val name = runCatching { URLDecoder.decode(entry.arguments?.getString("name").orEmpty(), StandardCharsets.UTF_8.name()) }.getOrDefault("")
            PrayerRoomDetailScreen(navController = navController, placeName = name)
        }
        composable(AppRoutes.TouristDetail) { TouristSpotDetailScreen(navController = navController) }
        composable(AppRoutes.ShoppingDetail) { ShoppingDetailScreen(navController = navController) }
        composable(AppRoutes.ConvenienceDetail) { ConvenienceStoreDetailScreen(navController = navController) }
        composable(AppRoutes.CafeDetail) { CafeDetailScreen(navController = navController) }
        composable(AppRoutes.AtmDetail) { AtmDetailScreen(navController = navController) }
        composable(AppRoutes.BankDetail) { BankDetailScreen(navController = navController) }
        composable(AppRoutes.ExchangeDetail) { ExchangeDetailScreen(navController = navController) }
        composable(AppRoutes.SubwayDetail) { SubwayDetailScreen(navController = navController) }
        composable(AppRoutes.RestroomDetail) { RestroomDetailScreen(navController = navController) }
        composable(AppRoutes.LockersDetail) { LockersDetailScreen(navController = navController) }
        composable(AppRoutes.HospitalDetail) { HospitalDetailScreen(navController = navController) }
        composable(AppRoutes.PharmacyDetail) { PharmacyDetailScreen(navController = navController) }
        composable(AppRoutes.ArExplore) { ArExploreScreen(navController = navController) }
        composable(AppRoutes.ArDebugPreview) { ArDebugPreviewScreen(navController = navController) }
        composable(AppRoutes.ArNavMap) { ArNavigationMapScreen(navController = navController, destinationName = "") }
        composable(
            route = "${AppRoutes.ArNavMap}/{destName}",
            arguments = listOf(navArgument("destName") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            val destName = runCatching { URLDecoder.decode(entry.arguments?.getString("destName").orEmpty(), StandardCharsets.UTF_8.name()) }.getOrDefault("")
            ArNavigationMapScreen(navController = navController, destinationName = destName)
        }
        composable(AppRoutes.Login) {
            val context = androidx.compose.ui.platform.LocalContext.current
            LoginScreen(
                onKakaoClick = {
                    navController.navigate(AppRoutes.oauthLoadingRoute(AuthProviderArg.Kakao))
                },
                onGoogleClick = {
                    navController.navigate(AppRoutes.oauthLoadingRoute(AuthProviderArg.Google))
                },
                onTermsClick = {
                    android.widget.Toast
                        .makeText(context, "이용약관 (전문 화면은 준비 중)", android.widget.Toast.LENGTH_SHORT)
                        .show()
                },
                onPrivacyClick = {
                    android.widget.Toast
                        .makeText(context, "개인정보 처리방침 (전문 화면은 준비 중)", android.widget.Toast.LENGTH_SHORT)
                        .show()
                },
            )
        }
        composable(AppRoutes.TermsAgreement) {
            TermsAgreementScreen(
                onBack = { navController.popBackStack() },
                onAllAgreedAndContinue = {
                    navController.navigate(AppRoutes.OnboardingLanguage)
                },
                onTermDetailClick = { /* TODO: 약관 전문 보기 */ },
            )
        }
        composable(
            route = AppRoutes.OAuthLoading,
            arguments = listOf(
                navArgument(AppRoutes.OAuthLoadingArgProvider) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val providerArg = entry.arguments?.getString(AppRoutes.OAuthLoadingArgProvider)
            val provider = when (providerArg) {
                AuthProviderArg.Kakao -> com.scanpang.app.data.AuthProvider.KAKAO
                AuthProviderArg.Google -> com.scanpang.app.data.AuthProvider.GOOGLE
                else -> null
            }
            OAuthLoadingScreen(
                provider = provider,
                onAuthSuccessExistingUser = {
                    navController.navigate(AppRoutes.Home) {
                        popUpTo(AppRoutes.Login) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onAuthSuccessNewUser = {
                    navController.navigate(AppRoutes.TermsAgreement) {
                        popUpTo(AppRoutes.Login) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(AppRoutes.LoginError) {
            LoginErrorScreen(
                onRetry = { /* TODO: 재시도 */ },
                onBackToLogin = {
                    navController.popBackStack(AppRoutes.Login, inclusive = false)
                },
            )
        }
        composable(AppRoutes.Withdrawal) {
            val context = androidx.compose.ui.platform.LocalContext.current
            WithdrawalScreen(
                onBack = { navController.popBackStack() },
                onWithdraw = {
                    // 회원탈퇴: 모든 prefs 삭제 + 백스택 비우고 Login 으로
                    com.scanpang.app.data.OnboardingPreferences(context).clearAll()
                    navController.navigate(AppRoutes.Login) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}

/** OAuth provider 식별자(NavArg 직렬화용) */
private object AuthProviderArg {
    const val Kakao = "kakao"
    const val Google = "google"
}
