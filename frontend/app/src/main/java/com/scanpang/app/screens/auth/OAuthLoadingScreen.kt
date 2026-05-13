package com.scanpang.app.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scanpang.app.components.auth.LogoMark
import com.scanpang.app.data.AuthProvider
import com.scanpang.app.data.OnboardingPreferences
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangTheme
import com.scanpang.app.ui.theme.ScanPangType
import kotlinx.coroutines.delay

private val LoadingGradient = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to ScanPangColors.LoginGradientStart,
        0.55f to ScanPangColors.LoginGradientMid,
        1f to ScanPangColors.LoginGradientEnd,
    ),
)

private const val SimulatedAuthDelayMs = 1500L

/**
 * OAuth 인증 진행 중 로딩 화면.
 *
 * 실제 OAuth 미연동 상태이므로 [SimulatedAuthDelayMs] 후 [OnboardingPreferences.isOnboardingComplete]
 * 로 신규/기존 유저를 판별해 분기:
 *   - true  → [onAuthSuccessExistingUser]   (Home 으로 이동, login·oauth_loading 스택 제거)
 *   - false → [onAuthSuccessNewUser]        (TermsAgreement 로 이동, login·oauth_loading 스택 제거)
 *
 * TODO: 실제 OAuth 연동 시 try/catch 로 실패 케이스를 받아 LoginError 로 분기.
 */
@Composable
fun OAuthLoadingScreen(
    onAuthSuccessExistingUser: () -> Unit,
    onAuthSuccessNewUser: () -> Unit,
    modifier: Modifier = Modifier,
    provider: AuthProvider? = null,
) {
    val context = LocalContext.current
    val prefs = remember(context) { OnboardingPreferences(context) }

    LaunchedEffect(Unit) {
        delay(SimulatedAuthDelayMs)
        if (prefs.isOnboardingComplete()) {
            onAuthSuccessExistingUser()
        } else {
            onAuthSuccessNewUser()
        }
    }

    val title = "잠시만 기다려주세요"
    val subtitle = when (provider) {
        AuthProvider.KAKAO -> "카카오 인증 중입니다..."
        AuthProvider.GOOGLE -> "구글 인증 중입니다..."
        null -> "인증 중입니다..."
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LoadingGradient)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LogoMark(size = 60.dp)
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = ScanPangColors.Primary,
                strokeWidth = 3.dp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = ScanPangType.title16SemiBold,
                color = ScanPangColors.OnSurfaceStrong,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = ScanPangType.body14Regular,
                color = ScanPangColors.OnSurfaceMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun OAuthLoadingScreenPreview() {
    ScanPangTheme {
        OAuthLoadingScreen(
            onAuthSuccessExistingUser = {},
            onAuthSuccessNewUser = {},
            provider = AuthProvider.KAKAO,
        )
    }
}
