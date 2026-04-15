package com.scanpang.app.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.scanpang.app.data.OnboardingPreferences
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

private val IconCircleSize = 72.dp
private val PreferenceIconSize = 48.dp

@Composable
fun OnboardingPreferenceScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { OnboardingPreferences(context) }
    var selected by remember {
        mutableStateOf<String?>(prefs.getTravelPreference())
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
                    text = "여행 중 중요하게 보는 조건은 무엇인가요?",
                    style = ScanPangType.title16SemiBold,
                    color = ScanPangColors.OnSurfaceStrong,
                )
                Spacer(modifier = Modifier.height(ScanPangSpacing.lg))
                Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm)) {
                    OnboardingSelectableCard(
                        selected = selected == OnboardingPreferences.TRAVEL_PREF_HALAL,
                        onClick = { selected = OnboardingPreferences.TRAVEL_PREF_HALAL },
                    ) {
                        OnboardingPreferenceCardInner(
                            icon = Icons.Rounded.Mosque,
                            title = "할랄 관련 정보가 중요해요",
                            subtitle = "할랄 식당, 기도실, 키블라 방향 등",
                            selected = selected == OnboardingPreferences.TRAVEL_PREF_HALAL,
                        )
                    }
                    OnboardingSelectableCard(
                        selected = selected == OnboardingPreferences.TRAVEL_PREF_GENERAL,
                        onClick = { selected = OnboardingPreferences.TRAVEL_PREF_GENERAL },
                    ) {
                        OnboardingPreferenceCardInner(
                            icon = Icons.Rounded.Explore,
                            title = "일반 여행 정보가 중요해요",
                            subtitle = "관광지, 맛집, 쇼핑 등",
                            selected = selected == OnboardingPreferences.TRAVEL_PREF_GENERAL,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(ScanPangSpacing.lg))
            }
            OnboardingPrimaryButton(
                text = "시작하기",
                enabled = selected != null,
                onClick = {
                    selected?.let { prefs.setTravelPreference(it) }
                    prefs.setOnboardingComplete(true)
                    navController.navigate(AppRoutes.Home) {
                        popUpTo(AppRoutes.OnboardingLanguage) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
            Spacer(modifier = Modifier.height(ScanPangSpacing.lg))
        }
    }
}

@Composable
private fun OnboardingPreferenceCardInner(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = if (selected) 28.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(IconCircleSize)
                .clip(CircleShape)
                .background(ScanPangColors.PrimarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(PreferenceIconSize),
                tint = ScanPangColors.Primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ScanPangType.body15Medium,
                color = ScanPangColors.OnSurfaceStrong,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = ScanPangType.caption12Medium,
                color = ScanPangColors.OnSurfaceMuted,
            )
        }
    }
}
