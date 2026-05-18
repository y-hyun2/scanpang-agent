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
import androidx.compose.ui.res.stringResource
import com.scanpang.app.R

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
    val strHalal      = stringResource(R.string.value_added_halal_title)
    val strHalalDesc  = stringResource(R.string.value_added_halal_desc)
    val strVegan      = stringResource(R.string.value_added_vegan_title)
    val strVeganDesc  = stringResource(R.string.value_added_vegan_desc)
    val strGeneral    = stringResource(R.string.value_added_general_title)
    + val options = remember(strHalal, strHalalDesc, strVegan, strVeganDesc, strGeneral) {
        listOf(
            PreferenceOption(ValueAdded.HALAL,   "🕌", strHalal,   strHalalDesc),
            PreferenceOption(ValueAdded.VEGAN,   "🌱", strVegan,   strVeganDesc),
            PreferenceOption(ValueAdded.GENERAL, "✨", strGeneral, null),
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
                    text = stringResource(R.string.onboarding_preference_title),
                    style = ScanPangType.titleLarge,
                    color = ScanPangColors.OnSurfaceStrong,
                )
                Spacer(modifier = Modifier.height(ScanPangSpacing.xs))
                Text(
                    text = stringResource(R.string.onboarding_preference_subtitle),
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
                text = stringResource(R.string.onboarding_start_button),
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
