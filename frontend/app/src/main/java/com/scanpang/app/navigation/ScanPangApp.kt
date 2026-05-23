package com.scanpang.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.scanpang.app.components.ScanPangMainTab
import com.scanpang.app.components.ScanPangTabBar
import com.scanpang.app.data.OnboardingPreferences
import com.scanpang.app.i18n.EnStrings
import com.scanpang.app.i18n.KoStrings
import com.scanpang.app.i18n.LocalStrings

private val mainTabRoutes = setOf(
    AppRoutes.Home,
    AppRoutes.Search,
    AppRoutes.Saved,
    AppRoutes.Profile,
    AppRoutes.ArExplore,
)

@Composable
fun ScanPangApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember(context) { OnboardingPreferences(context) }
    val strings = if (prefs.getLanguageCode() == "en") EnStrings else KoStrings

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showTabBar = currentRoute in mainTabRoutes

    CompositionLocalProvider(LocalStrings provides strings) {
    Box(modifier = modifier.fillMaxSize()) {
        AppNavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize(),
        )
        if (showTabBar) {
            ScanPangTabBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
                selectedTab = when (currentRoute) {
                    AppRoutes.Home -> ScanPangMainTab.Home
                    AppRoutes.Search -> ScanPangMainTab.Search
                    AppRoutes.Saved -> ScanPangMainTab.Saved
                    AppRoutes.Profile -> ScanPangMainTab.Profile
                    else -> ScanPangMainTab.Explore
                },
                onHomeClick = { navigateToHome(navController) },
                onSearchClick = { navigateMainTab(navController, AppRoutes.Search) },
                onSavedClick = { navigateMainTab(navController, AppRoutes.Saved) },
                onProfileClick = { navigateMainTab(navController, AppRoutes.Profile) },
                onExploreClick = {
                    navController.navigate(AppRoutes.ArExplore) { launchSingleTop = true }
                },
            )
        }
    }
    } // CompositionLocalProvider
}

private fun navigateToHome(navController: androidx.navigation.NavController) {
    navController.navigate(AppRoutes.Home) {
        popUpTo(AppRoutes.Home) { inclusive = true }
        launchSingleTop = true
    }
}

private fun navigateMainTab(navController: androidx.navigation.NavController, route: String) {
    navController.navigate(route) {
        popUpTo(AppRoutes.Home) {
            inclusive = false
        }
        launchSingleTop = true
    }
}
