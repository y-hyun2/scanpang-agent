package com.scanpang.app.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.scanpang.app.data.OnboardingPreferences
import com.scanpang.app.screens.onboarding.OnboardingChoiceContent
import com.scanpang.app.screens.onboarding.OnboardingSelectableCard
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
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

/**
 * 내 정보 → 언어 설정. 온보딩 1단계와 동일한 카드 UI 를 재사용하되,
 * "다음" 버튼 없이 선택 즉시 SharedPreferences 에 저장한다.
 */
@Composable
fun LanguageSettingsScreen(
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
    var selectedCode by remember { mutableStateOf(prefs.getLanguageCode()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScanPangColors.Surface,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScanPangColors.Surface)
                .statusBarsPadding(),
        ) {
            SettingsTitleBar(
                title = stringResource(R.string.settings_language_title),
                onBack = { navController.popBackStack() },
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = ScanPangDimens.screenHorizontal)
                    .padding(top = 16.dp),
            ) {
            Text(
                text = stringResource(R.string.settings_language_desc),
                style = ScanPangType.meta13.copy(lineHeight = 19.5.sp),
                color = ScanPangColors.OnSurfaceMuted,
            )
            Spacer(modifier = Modifier.height(ScanPangSpacing.lg))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                options.forEach { opt ->
                    OnboardingSelectableCard(
                        selected = selectedCode == opt.code,
                        onClick = {
                            selectedCode = opt.code
                            prefs.setLanguageCode(opt.code)
                        },
                        shape = RoundedCornerShape(14.dp),
                        horizontalPadding = 20.dp,
                        verticalPadding = 20.dp,
                        horizontalGap = 16.dp,
                    ) {
                        OnboardingChoiceContent(
                            leading = opt.flag,
                            title = opt.label,
                            subtitle = opt.subLabel,
                            leadingTextStyle = ScanPangType.headlineMedium.copy(fontWeight = FontWeight.Normal),
                            subtitleTextStyle = ScanPangType.meta13,
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
internal fun SettingsTitleBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 4.dp, end = ScanPangDimens.screenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = ScanPangColors.OnSurfaceStrong,
            )
        }
        Text(
            text = title,
            style = ScanPangType.profileName18,
            color = ScanPangColors.OnSurfaceStrong,
        )
    }
}
