package com.scanpang.app.screens.profile

import androidx.compose.runtime.rememberCoroutineScope
import com.scanpang.app.data.auth.AuthRepository
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.scanpang.app.components.ProfileSettingsCard
import com.scanpang.app.components.ProfileSettingsRow
import com.scanpang.app.components.ProfileSettingsSectionLabel
import com.scanpang.app.components.ProfileSettingsToggleRow
import com.scanpang.app.components.auth.LogoutConfirmDialog
import com.scanpang.app.data.AppSettingsPreferences
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val onboardingPrefs = remember(context) { OnboardingPreferences(context) }
    val appSettingsPrefs = remember(context) { AppSettingsPreferences(context) }
    val logoutScope = rememberCoroutineScope()
    var showLogoutDialog by remember { mutableStateOf(false) }

    // 언어/부가가치 설정 화면에서 변경 후 돌아왔을 때 프로필 pill 이 즉시 반영되도록
    // ON_RESUME 시 prefs 를 다시 읽어 state 를 갱신한다.
    var languageCode by remember { mutableStateOf(onboardingPrefs.getLanguageCode()) }
    var valueAdded by remember { mutableStateOf(onboardingPrefs.getValueAdded()) }
    var ttsEnabled by remember { mutableStateOf(appSettingsPrefs.isTtsEnabled()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                languageCode = onboardingPrefs.getLanguageCode()
                valueAdded = onboardingPrefs.getValueAdded()
                ttsEnabled = appSettingsPrefs.isTtsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val savedName = onboardingPrefs.getDisplayName().orEmpty().trim()
    val profileName = if (savedName.isNotEmpty()) savedName else "여행자"
    val langLabel = OnboardingPreferences.languageDisplayLabel(languageCode)
    val valueAddedShort = OnboardingPreferences.valueAddedShortLabel(valueAdded)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScanPangColors.Surface,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { _ ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(ScanPangColors.Surface)
                .statusBarsPadding()
                .padding(horizontal = ScanPangDimens.screenHorizontal)
                .padding(bottom = ScanPangDimens.mainTabContentBottomInset + ScanPangSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                        .border(
                            ScanPangDimens.borderHairline,
                            ScanPangColors.OutlineSubtle,
                            ScanPangShapes.radius16,
                        )
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
                        // Figma: 아바타 우측엔 이름만. 서브타이틀(할랄 · 한국어) 은 제거됨 — 아래 pill 행과 정보가 중복돼서.
                        Text(
                            text = profileName,
                            style = ScanPangType.profileName18,
                            color = ScanPangColors.OnSurfaceStrong,
                        )
                    }
                    // 부가가치 pill(할랄/비건/일반) + 언어 pill(한국어/English) 두 개만 노출.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                    ) {
                        if (valueAddedShort.isNotEmpty()) {
                            ProfilePreferenceTag(valueAddedShort)
                        }
                        ProfilePreferenceTag(langLabel)
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
                        onClick = { navController.navigate(AppRoutes.SettingsLanguage) },
                        showDividerBelow = true,
                    )
                    ProfileSettingsRow(
                        label = "부가가치 설정",
                        icon = Icons.Rounded.Tune,
                        iconTint = ScanPangColors.Primary,
                        onClick = { navController.navigate(AppRoutes.SettingsValueAdded) },
                        showDividerBelow = true,
                    )
                    // TTS 는 별도 페이지가 아닌 토글로 바로 ON/OFF — 사용자 요구사항.
                    ProfileSettingsToggleRow(
                        label = "TTS 음성 안내",
                        icon = Icons.Rounded.RecordVoiceOver,
                        iconTint = ScanPangColors.Primary,
                        checked = ttsEnabled,
                        onCheckedChange = {
                            ttsEnabled = it
                            appSettingsPrefs.setTtsEnabled(it)
                        },
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
                        label = "알림 설정",
                        icon = Icons.Rounded.Notifications,
                        iconTint = ScanPangColors.Primary,
                        onClick = { navController.navigate(AppRoutes.SettingsNotification) },
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
                        label = "회원탈퇴",
                        icon = Icons.Rounded.PersonRemove,
                        iconTint = ScanPangColors.OnSurfaceMuted,
                        onClick = { navController.navigate(AppRoutes.Withdrawal) },
                        showDividerBelow = true,
                    )
                    ProfileSettingsRow(
                        label = "로그아웃",
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        iconTint = ScanPangColors.DangerStrong,
                        labelColor = ScanPangColors.DangerStrong,
                        onClick = { showLogoutDialog = true },
                        showDividerBelow = false,
                    )
                }
            }
        }
    }

    if (showLogoutDialog) {
        LogoutConfirmDialog(
            onDismiss = { showLogoutDialog = false },
            onConfirm = {
                showLogoutDialog = false
                // signOut() 은 서버 세션 무효화 + 로컬 토큰 삭제. 완료를 기다리지 않고 바로 navigate
                // 해도 SDK 가 sessionStatus 를 NotAuthenticated 로 갱신하므로 충돌 없음.
                logoutScope.launch { AuthRepository.signOut() }
                // 로그아웃은 세션만 끊고 prefs 는 보존 → 다음 로그인 시 Home 직행.
                navController.navigate(AppRoutes.Login) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            },
        )
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
