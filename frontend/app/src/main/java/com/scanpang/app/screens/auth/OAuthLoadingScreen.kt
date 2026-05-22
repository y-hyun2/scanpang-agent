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
import com.scanpang.app.data.AuthProvider
import com.scanpang.app.data.OnboardingPreferences
import com.scanpang.app.data.auth.AuthRepository
import com.scanpang.app.i18n.LocalStrings
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangTheme
import com.scanpang.app.ui.theme.ScanPangType
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first

private val LoadingGradient = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to ScanPangColors.LoginGradientStart,
        0.55f to ScanPangColors.LoginGradientMid,
        1f to ScanPangColors.LoginGradientEnd,
    ),
)

/**
 * OAuth 인증 진행 중 로딩 화면.
 *
 * 진입 시 [provider] 에 따라 [AuthRepository.signInWithKakao] / [signInWithGoogle] 을 호출해
 * Custom Tab 으로 Supabase 호스팅 OAuth 페이지를 띄운다. 사용자가 외부 인증을 끝내면
 * deep link (`com.scanpang.app://login-callback`) 로 [com.scanpang.app.MainActivity] 가
 * intent 를 받아 SDK 에 위임하고, SDK 는 PKCE 코드 교환 후 [AuthRepository.sessionStatus]
 * 를 [SessionStatus.Authenticated] 로 갱신한다.
 *
 * 이 화면은 그 갱신 이벤트를 기다렸다가 onboarding 완료 여부로 분기:
 *   - true  → [onAuthSuccessExistingUser]   (Home, login·oauth_loading 스택 제거)
 *   - false → [onAuthSuccessNewUser]        (TermsAgreement)
 *
 * 사용자가 Custom Tab 을 그냥 닫고 돌아오면 sessionStatus 가 갱신되지 않아 계속 대기.
 * 백 버튼으로 Login 으로 빠져나갈 수 있어 dead-lock 은 아니지만, 추후 timeout 추가 가능.
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

    // (1) OAuth 시작 — provider 가 정해진 경우에만 1회 호출.
    LaunchedEffect(provider) {
        when (provider) {
            AuthProvider.KAKAO -> AuthRepository.signInWithKakao()
            AuthProvider.GOOGLE -> AuthRepository.signInWithGoogle()
            null -> Unit
        }
    }

    // (2) Authenticated 이벤트 첫 발생을 기다렸다가 onboarding 상태로 분기.
    LaunchedEffect(Unit) {
        AuthRepository.sessionStatus
            .filterIsInstance<SessionStatus.Authenticated>()
            .first()
        // 로그인 직후 user_preferences 서버 pull — 다기기/재설치 동기화.
        com.scanpang.app.data.UserPreferencesSync.pullFromBackend(context)
        if (prefs.isOnboardingComplete()) {
            onAuthSuccessExistingUser()
        } else {
            onAuthSuccessNewUser()
        }
    }

    val s = LocalStrings.current
    val title = s.authLoadingDefault
    val subtitle = when (provider) {
        AuthProvider.KAKAO -> s.authLoadingKakao
        AuthProvider.GOOGLE -> s.authLoadingGoogle
        null -> s.authLoadingGeneric
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
