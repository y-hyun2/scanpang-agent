package com.scanpang.app.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.scanpang.app.data.OnboardingPreferences
import com.scanpang.app.data.ValueAdded
import com.scanpang.app.i18n.LocalStrings
import com.scanpang.app.screens.onboarding.OnboardingChoiceContent
import com.scanpang.app.screens.onboarding.OnboardingSelectableCard
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

private val SettingsValueAddedEmojiStyle = TextStyle(
    fontSize = 32.sp,
    lineHeight = 40.sp,
    fontWeight = FontWeight.Normal,
)

private data class ValueAddedOption(
    val value: ValueAdded,
    val emoji: String,
    val title: String,
    val subtitle: String?,
)

/**
 * 내 정보 → 부가가치 설정. 온보딩 3단계와 동일한 카드 UI 를 재사용하되,
 * 선택 즉시 SharedPreferences 에 저장한다.
 *
 * Home/Search 등 다른 화면이 ON_RESUME 에서 prefs 를 다시 읽어 분기를 갱신하므로,
 * 이 화면에서 별도의 broadcast 는 필요 없음.
 */
@Composable
fun ValueAddedSettingsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val prefs = remember { OnboardingPreferences(context) }
    val options = remember(s) {
        listOf(
            ValueAddedOption(
                value = ValueAdded.HALAL,
                emoji = "🕌",
                title = s.onboardingPreferenceHalal,
                subtitle = s.onboardingPreferenceHalalDesc,
            ),
            ValueAddedOption(
                value = ValueAdded.VEGAN,
                emoji = "🌱",
                title = s.onboardingPreferenceVegan,
                subtitle = s.onboardingPreferenceVeganDesc,
            ),
            ValueAddedOption(
                value = ValueAdded.GENERAL,
                emoji = "✨",
                title = s.onboardingPreferenceGeneral,
                subtitle = null,
            ),
        )
    }
    var selected by remember { mutableStateOf(prefs.getValueAdded()) }

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
                title = s.profileValueAddedSettings,
                onBack = { navController.popBackStack() },
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = ScanPangDimens.screenHorizontal)
                    .padding(top = 16.dp),
            ) {
            Text(
                text = s.settingsValueAddedDesc,
                style = ScanPangType.meta13.copy(lineHeight = 19.5.sp),
                color = ScanPangColors.OnSurfaceMuted,
            )
            Spacer(modifier = Modifier.height(ScanPangSpacing.lg))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                options.forEach { opt ->
                    OnboardingSelectableCard(
                        selected = selected == opt.value,
                        onClick = {
                            selected = opt.value
                            prefs.setValueAdded(opt.value)
                        },
                        shape = RoundedCornerShape(14.dp),
                        horizontalPadding = 20.dp,
                        verticalPadding = 20.dp,
                        horizontalGap = 16.dp,
                    ) {
                        OnboardingChoiceContent(
                            leading = opt.emoji,
                            title = opt.title,
                            subtitle = opt.subtitle,
                            leadingTextStyle = SettingsValueAddedEmojiStyle,
                            subtitleTextStyle = ScanPangType.meta13,
                            titleSubtitleSpacing = 4.dp,
                        )
                    }
                }
            }
            }
        }
    }
}
