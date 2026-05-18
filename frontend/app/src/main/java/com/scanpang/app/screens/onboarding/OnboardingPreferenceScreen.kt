package com.scanpang.app.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.scanpang.app.data.OnboardingPreferences
import com.scanpang.app.data.ValueAdded
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

private data class PreferenceOption(
    val value: ValueAdded,
    val emoji: String,
    val title: String,
    val subtitle: String?,
)

@Composable
fun OnboardingPreferenceScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { OnboardingPreferences(context) }
    val options = remember {
        listOf(
            PreferenceOption(
                value = ValueAdded.HALAL,
                emoji = "🕌",
                title = "할랄",
                subtitle = "할랄 식당, 기도실, 키블라 방향 등",
            ),
            PreferenceOption(
                value = ValueAdded.VEGAN,
                emoji = "🌱",
                title = "비건",
                subtitle = "비건 식당, 채식 메뉴 등",
            ),
            PreferenceOption(
                value = ValueAdded.GENERAL,
                emoji = "✨",
                title = "괜찮아요",
                subtitle = null,
            ),
        )
    }
    var selected by remember {
        mutableStateOf<ValueAdded?>(prefs.getValueAdded())
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.White,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(innerPadding)
                .padding(horizontal = ScanPangSpacing.lg),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(modifier = Modifier.height(ScanPangSpacing.md))
                OnboardingProgressHeader(step = 3)
                Spacer(modifier = Modifier.height(ScanPangSpacing.lg))
                Text(
                    text = "여행 중 무엇을 우선하시나요?",
                    style = ScanPangType.titleLarge,
                    color = ScanPangColors.OnSurfaceStrong,
                )
                Spacer(modifier = Modifier.height(ScanPangSpacing.xs))
                Text(
                    text = "안내와 알림이 이 선택에 맞춰집니다.",
                    style = ScanPangType.body14Regular,
                    color = ScanPangColors.OnSurfaceMuted,
                )
                Spacer(modifier = Modifier.height(ScanPangSpacing.lg))
                Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm)) {
                    options.forEach { opt ->
                        OnboardingSelectableCard(
                            selected = selected == opt.value,
                            onClick = { selected = opt.value },
                        ) {
                            OnboardingChoiceContent(
                                leading = opt.emoji,
                                title = opt.title,
                                subtitle = opt.subtitle,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(ScanPangSpacing.lg))
            }
            OnboardingPrimaryButton(
                text = "시작하기",
                enabled = selected != null,
                onClick = {
                    selected?.let { prefs.setValueAdded(it) }
                    prefs.setOnboardingComplete(true)
                    // 신규 가입 흐름이 시작된 약관 화면 이전(Login·Splash)까지 통째로 정리.
                    navController.navigate(AppRoutes.Home) {
                        popUpTo(AppRoutes.TermsAgreement) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
            Spacer(modifier = Modifier.height(ScanPangSpacing.lg))
        }
    }
}
