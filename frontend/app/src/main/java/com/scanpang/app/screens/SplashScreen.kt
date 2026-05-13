package com.scanpang.app.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.scanpang.app.R
import com.scanpang.app.data.auth.AuthRepository
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangType
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.first

@Composable
fun SplashScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        // Supabase SDK 가 로컬 storage 에서 세션 복원할 때까지 기다린다 (Initializing 단계).
        // 첫 결정 가능한 상태(Authenticated / NotAuthenticated) 가 잡히면 그에 맞게 분기.
        // RefreshFailure 는 세션이 만료된 경우 → Login 으로 보내 재로그인 유도.
        val status = AuthRepository.sessionStatus.first {
            it is SessionStatus.Authenticated ||
                it is SessionStatus.NotAuthenticated ||
                it is SessionStatus.RefreshFailure
        }
        val target = if (status is SessionStatus.Authenticated) AppRoutes.Home else AppRoutes.Login
        navController.navigate(target) {
            popUpTo(AppRoutes.Splash) { inclusive = true }
            launchSingleTop = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "ScanPang",
            style = ScanPangType.title16SemiBold,
            color = ScanPangColors.Primary,
            textAlign = TextAlign.Center,
        )
    }
}
