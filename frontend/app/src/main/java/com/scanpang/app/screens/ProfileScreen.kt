package com.scanpang.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.scanpang.app.components.ProfileSettingsCard
import com.scanpang.app.components.ProfileSettingsRow
import com.scanpang.app.components.ProfileSettingsSectionLabel
import com.scanpang.app.data.OnboardingPreferences
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.ScanPangFigmaAssets
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

@Composable
fun ProfileScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val onboardingPrefs = remember(context) { OnboardingPreferences(context) }
    val savedName = onboardingPrefs.getDisplayName().orEmpty().trim()
    val profileName = if (savedName.isNotEmpty()) savedName else "여행자"
    val langLabel = OnboardingPreferences.languageDisplayLabel(onboardingPrefs.getLanguageCode())
    val travelLabel = OnboardingPreferences.travelPreferenceDisplayLabel(onboardingPrefs.getTravelPreference())
    val profileSubtitle = buildString {
        if (travelLabel.isNotEmpty()) {
            append(travelLabel)
            append(" · ")
        }
        append(langLabel)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScanPangColors.Background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { _ ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(ScanPangColors.Background)
                .statusBarsPadding()
                .padding(horizontal = ScanPangDimens.screenHorizontal)
                .padding(bottom = ScanPangDimens.mainTabContentBottomInset + ScanPangSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
        ) {
            item {
                Text(
                    text = "내 정보",
                    style = ScanPangType.homeGreeting,
                    color = ScanPangColors.OnSurfaceStrong,
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ScanPangShapes.radius16)
                        .background(ScanPangColors.Surface)
                        .padding(ScanPangDimens.profileCardPadding),
                    verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.lg),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(ScanPangDimens.profileAvatar)
                                .clip(CircleShape)
                                .background(ScanPangColors.Background),
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(ScanPangFigmaAssets.ProfileAvatar)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs)) {
                            Text(
                                text = profileName,
                                style = ScanPangType.profileName18,
                                color = ScanPangColors.OnSurfaceStrong,
                            )
                            Text(
                                text = profileSubtitle,
                                style = ScanPangType.meta13,
                                color = ScanPangColors.OnSurfaceMuted,
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                    ) {
                        when (onboardingPrefs.getTravelPreference()) {
                            OnboardingPreferences.TRAVEL_PREF_HALAL ->
                                ProfilePreferenceTag("할랄 우선")
                            OnboardingPreferences.TRAVEL_PREF_GENERAL ->
                                ProfilePreferenceTag("일반 여행")
                            else -> ProfilePreferenceTag("여행 맞춤")
                        }
                        ProfilePreferenceTag("AR 탐색 모드")
                        ProfilePreferenceTag("TTS 활성")
                    }
                }
            }
            item {
                ProfileSettingsSectionLabel(text = "여행 설정")
            }
            item {
                ProfileSettingsCard {
                    ProfileSettingsRow(
                        label = "언어 설정",
                        icon = Icons.Rounded.Language,
                        iconTint = ScanPangColors.Primary,
                        onClick = { },
                        showDividerBelow = true,
                    )
                    ProfileSettingsRow(
                        label = "할랄 우선 설정",
                        icon = Icons.Rounded.Restaurant,
                        iconTint = ScanPangColors.Primary,
                        onClick = { },
                        showDividerBelow = true,
                    )
                    ProfileSettingsRow(
                        label = "기도 지원 설정",
                        icon = Icons.Rounded.Mosque,
                        iconTint = ScanPangColors.Primary,
                        onClick = { },
                        showDividerBelow = true,
                    )
                    ProfileSettingsRow(
                        label = "TTS 음성 안내",
                        icon = Icons.Rounded.RecordVoiceOver,
                        iconTint = ScanPangColors.Primary,
                        onClick = { },
                        showDividerBelow = false,
                    )
                }
            }
            item {
                ProfileSettingsSectionLabel(text = "앱 설정")
            }
            item {
                ProfileSettingsCard {
                    ProfileSettingsRow(
                        label = "저장한 장소",
                        icon = Icons.Rounded.Bookmark,
                        iconTint = ScanPangColors.Primary,
                        onClick = { navController.navigate(AppRoutes.Saved) },
                        showDividerBelow = true,
                    )
                    ProfileSettingsRow(
                        label = "알림 설정",
                        icon = Icons.Rounded.Notifications,
                        iconTint = ScanPangColors.Primary,
                        onClick = { },
                        showDividerBelow = false,
                    )
                }
            }
            item {
                ProfileSettingsSectionLabel(text = "기타")
            }
            item {
                ProfileSettingsCard {
                    ProfileSettingsRow(
                        label = "도움말",
                        icon = Icons.AutoMirrored.Rounded.Help,
                        iconTint = ScanPangColors.Primary,
                        onClick = { },
                        showDividerBelow = true,
                    )
                    ProfileSettingsRow(
                        label = "문의하기",
                        icon = Icons.Rounded.Mail,
                        iconTint = ScanPangColors.Primary,
                        onClick = { },
                        showDividerBelow = true,
                    )
                    ProfileSettingsRow(
                        label = "로그아웃",
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        iconTint = ScanPangColors.DangerStrong,
                        labelColor = ScanPangColors.DangerStrong,
                        onClick = {
                            OnboardingPreferences(context).clearForLogout()
                            navController.navigate(AppRoutes.Splash) {
                                popUpTo(navController.graph.id) {
                                    inclusive = true
                                }
                            }
                        },
                        showDividerBelow = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfilePreferenceTag(label: String) {
    Row(
        modifier = Modifier
            .clip(ScanPangShapes.profileTag)
            .background(ScanPangColors.PrimarySoft)
            .padding(horizontal = ScanPangSpacing.md, vertical = ScanPangDimens.stackGap6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangDimens.stackGap6),
    ) {
        Box(
            modifier = Modifier
                .size(ScanPangDimens.profileTagIcon)
                .background(ScanPangColors.Primary),
        )
        Text(
            text = label,
            style = ScanPangType.chip12Medium,
            color = ScanPangColors.Primary,
        )
    }
}
