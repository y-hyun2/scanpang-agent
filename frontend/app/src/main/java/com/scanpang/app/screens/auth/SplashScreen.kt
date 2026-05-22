package com.scanpang.app.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.scanpang.app.data.UserPreferencesSync
import com.scanpang.app.data.auth.AuthRepository
import com.scanpang.app.navigation.AppRoutes
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first

@Composable
fun SplashScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        // Supabase SDK 가 로컬 storage 에서 세션 복원할 때까지 기다린다 (Initializing 단계).
        // 첫 결정 가능한 상태(Authenticated / NotAuthenticated) 가 잡히면 그에 맞게 분기.
        // RefreshFailure 는 세션이 만료된 경우 → Login 으로 보내 재로그인 유도.
        val status = AuthRepository.sessionStatus.first {
            it is SessionStatus.Authenticated ||
                it is SessionStatus.NotAuthenticated ||
                it is SessionStatus.RefreshFailure
        }
        // Authenticated 면 Home 가기 전에 user_preferences 서버 pull — 다기기 동기화.
        if (status is SessionStatus.Authenticated) {
            UserPreferencesSync.pullFromBackend(context)
        }
        val target = if (status is SessionStatus.Authenticated) AppRoutes.Home else AppRoutes.Login
        navController.navigate(target) {
            popUpTo(AppRoutes.Splash) { inclusive = true }
            launchSingleTop = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        YouScanAnimatedLogo()
    }
}
