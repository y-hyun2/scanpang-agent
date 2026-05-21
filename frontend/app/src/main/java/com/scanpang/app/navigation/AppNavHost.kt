package com.scanpang.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import com.scanpang.app.screens.HomeScreen
import com.scanpang.app.screens.RecentlyViewedListScreen
import com.scanpang.app.screens.profile.LanguageSettingsScreen
import com.scanpang.app.screens.profile.NotificationSettingsScreen
import com.scanpang.app.screens.profile.ValueAddedSettingsScreen
import com.scanpang.app.screens.auth.SplashScreen
import com.scanpang.app.screens.auth.LoginErrorScreen
import com.scanpang.app.screens.auth.LoginScreen
import com.scanpang.app.screens.auth.OAuthLoadingScreen
import com.scanpang.app.screens.auth.TermsAgreementScreen
import com.scanpang.app.screens.auth.TermsDetailScreen
import com.scanpang.app.screens.auth.WithdrawalScreen
import com.scanpang.app.screens.onboarding.OnboardingLanguageScreen
import com.scanpang.app.screens.onboarding.OnboardingNameScreen
import com.scanpang.app.screens.onboarding.OnboardingPreferenceScreen
import com.scanpang.app.screens.PlaceDetailScreen
import com.scanpang.app.screens.profile.ContactScreen
import com.scanpang.app.screens.profile.HelpScreen
import com.scanpang.app.screens.profile.ProfileEditScreen
import com.scanpang.app.screens.profile.ProfileScreen
import com.scanpang.app.screens.QiblaDirectionScreen
import com.scanpang.app.screens.SavedPlacesScreen
import com.scanpang.app.screens.SearchDefaultScreen
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

    const val SearchSavedStateClearQueryKey = "clear_search_query"

    /**
     * Home 의 quick action 등에서 검색 탭을 띄우면서 입력칸에 미리 채워넣을 검색어를 전달할 때 쓰는
     * savedStateHandle 키. SearchDefaultScreen 진입 시 값이 있으면 query 로 반영하고 null 로 리셋.
     */
    const val SearchSavedStatePendingQueryKey = "pending_search_query"

    const val Saved = "saved"
    const val Profile = "profile"
    const val ProfileEdit = "profile_edit"
    const val RecentlyViewed = "recently_viewed"

    // 내 정보 → 상세 설정 페이지들
    const val SettingsLanguage = "settings_language"
    const val SettingsValueAdded = "settings_value_added"
    const val SettingsNotification = "settings_notification"
    /**
     * 통합 Detail 라우트 베이스. 실제 이동은 [placeDetailRoute] 가 만든
     * `place_detail/{categoryKey}/{placeId}` 형태의 URL 로 한다.
     *
     * categoryKey 는 backend store_details.category_key 또는
     * [com.scanpang.app.data.SavedPlaceNavTarget.toCategoryKey] 값.
     * placeId 는 DummyData 의 Place.id 또는 backend store_details.id —
     * 매칭 없으면 [com.scanpang.app.data.DummyData.findPlaceById] 가 카테고리 1번째로 폴백.
     */
    const val PlaceDetail = "place_detail"

    fun placeDetailRoute(categoryKey: String, id: String = ""): String {
        val encCat = URLEncoder.encode(categoryKey.ifBlank { "restaurant" }, StandardCharsets.UTF_8.name())
        val encId = URLEncoder.encode(id, StandardCharsets.UTF_8.name())
        return "$PlaceDetail/$encCat/$encId"
    }

    const val ArExplore = "ar_explore"
    const val ArNavMap = "ar_nav_map"
    fun arNavMapRoute(destName: String = ""): String {
        val encoded = URLEncoder.encode(destName, StandardCharsets.UTF_8.name())
        return "$ArNavMap/$encoded"
    }

    const val Login = "login"
    const val TermsAgreement = "terms_agreement"
    const val TermsDetail = "terms_detail/{termId}"
    fun termsDetailRoute(termId: String) = "terms_detail/$termId"
    /** 라우트 패턴 — 호출 시 [oauthLoadingRoute] 사용 */
    const val OAuthLoading = "oauth_loading?provider={provider}"
    const val OAuthLoadingArgProvider = "provider"
    const val LoginError = "login_error"
    const val Withdrawal = "withdrawal"
    const val Contact = "contact"
    const val Help = "help"

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
        composable(AppRoutes.Saved) { SavedPlacesScreen(navController = navController) }
        composable(AppRoutes.Profile) { ProfileScreen(navController = navController) }
        composable(AppRoutes.ProfileEdit) { ProfileEditScreen(navController = navController) }
        composable(AppRoutes.RecentlyViewed) { RecentlyViewedListScreen(navController = navController) }
        composable(AppRoutes.SettingsLanguage) { LanguageSettingsScreen(navController = navController) }
        composable(AppRoutes.SettingsValueAdded) { ValueAddedSettingsScreen(navController = navController) }
        composable(AppRoutes.SettingsNotification) { NotificationSettingsScreen(navController = navController) }
        composable(AppRoutes.Contact) { ContactScreen(navController = navController) }
        composable(AppRoutes.Help) { HelpScreen(navController = navController) }
        composable(
            route = "${AppRoutes.PlaceDetail}/{categoryKey}/{placeId}",
            arguments = listOf(
                navArgument("categoryKey") { type = NavType.StringType; defaultValue = "restaurant" },
                navArgument("placeId") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val categoryKey = runCatching {
                URLDecoder.decode(entry.arguments?.getString("categoryKey").orEmpty(), StandardCharsets.UTF_8.name())
            }.getOrDefault("restaurant")
            val placeId = runCatching {
                URLDecoder.decode(entry.arguments?.getString("placeId").orEmpty(), StandardCharsets.UTF_8.name())
            }.getOrDefault("")
            PlaceDetailScreen(
                navController = navController,
                categoryKey = categoryKey,
                placeId = placeId,
            )
        }
        composable(AppRoutes.ArExplore) { ArExploreScreen(navController = navController) }
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
                onBack = {
                    navController.navigate(AppRoutes.Login) {
                        popUpTo(AppRoutes.TermsAgreement) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onAllAgreedAndContinue = {
                    navController.navigate(AppRoutes.OnboardingLanguage)
                },
                onTermDetailClick = { termId ->
                    navController.navigate(AppRoutes.termsDetailRoute(termId))
                },
            )
        }
        composable(
            route = AppRoutes.TermsDetail,
            arguments = listOf(navArgument("termId") { type = NavType.StringType }),
        ) { entry ->
            val termId = entry.arguments?.getString("termId").orEmpty()
            TermsDetailScreen(
                termId = termId,
                onBack = { navController.popBackStack() },
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

/**
 * Home 의 quick action 등에서 검색 탭을 띄우면서 입력칸에 미리 채워넣을 검색어를 전달.
 * SearchDefaultScreen 이 [AppRoutes.SearchSavedStatePendingQueryKey] 값을 읽어 query 로 적용.
 */
fun NavController.navigateToSearchWithQuery(prefillQuery: String) {
    navigate(AppRoutes.Search) {
        popUpTo(AppRoutes.Home) {
            saveState = true
            inclusive = false
        }
        launchSingleTop = true
        restoreState = true
    }
    runCatching {
        getBackStackEntry(AppRoutes.Search).savedStateHandle[AppRoutes.SearchSavedStatePendingQueryKey] =
            prefillQuery
    }
}
