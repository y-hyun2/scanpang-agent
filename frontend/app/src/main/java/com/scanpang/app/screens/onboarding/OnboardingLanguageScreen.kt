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
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType
import androidx.compose.ui.res.stringResource
import com.scanpang.app.R

private data class LanguageOption(
    val code: String,
    val flag: String,
    val label: String,
    val subLabel: String,
)

@Composable
fun OnboardingLanguageScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { OnboardingPreferences(context) }
    val options = remember {
        listOf(
            LanguageOption(OnboardingPreferences.LANG_KO, "🇰🇷", "한국어", "Korean"),
            LanguageOption(OnboardingPreferences.LANG_EN, "🇺🇸", "English", "영어"),
        )
    }
    var selectedCode by remember {
        mutableStateOf<String?>(prefs.getLanguageCode())
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
                .padding(horizontal = ScanPangSpacing.lg)
                .padding(bottom = ScanPangSpacing.sm),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(modifier = Modifier.height(ScanPangSpacing.md))
                OnboardingProgressHeader(step = 1)
                Spacer(modifier = Modifier.height(ScanPangSpacing.lg))
                Text(
                    text = stringResource(R.string.onboarding_language_title),
                    style = ScanPangType.titleLarge,
                    color = ScanPangColors.OnSurfaceStrong,
                )
                Spacer(modifier = Modifier.height(ScanPangSpacing.xs))
                Text(
                    text = stringResource(R.string.onboarding_language_subtitle),
                    style = ScanPangType.body14Regular,
                    color = ScanPangColors.OnSurfaceMuted,
                )
                Spacer(modifier = Modifier.height(ScanPangSpacing.lg))
                Column(
                    verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                ) {
                    options.forEach { opt ->
                        OnboardingSelectableCard(
                            selected = selectedCode == opt.code,
                            onClick = { selectedCode = opt.code },
                        ) {
                            OnboardingChoiceContent(
                                leading = opt.flag,
                                title = opt.label,
                                subtitle = opt.subLabel,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(ScanPangSpacing.lg))
            }
            OnboardingPrimaryButton(
                text = stringResource(R.string.onboarding_next_button),
                enabled = selectedCode != null,
                onClick = {
                    selectedCode?.let { prefs.setLanguageCode(it) }
                    navController.navigate(AppRoutes.OnboardingName)
                },
            )
            Spacer(modifier = Modifier.height(ScanPangSpacing.lg))
        }
    }
}
