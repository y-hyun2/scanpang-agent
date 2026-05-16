package com.scanpang.app.screens.profile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.scanpang.app.components.ProfileSettingsCard
import com.scanpang.app.components.ProfileSettingsSectionLabel
import com.scanpang.app.components.ProfileSettingsToggleRow
import com.scanpang.app.data.AppSettingsPreferences
import com.scanpang.app.data.OnboardingPreferences
import com.scanpang.app.data.ValueAdded
import com.scanpang.app.notifications.PrayerAlarmScheduler
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangSpacing
import kotlinx.coroutines.launch

/**
 * 내 정보 → 알림 설정.
 *
 * 세 섹션 (알림 받기 / 알림 종류 / 방해 금지) 으로 구성된다.
 * - "기도 시간 알림" 은 [ValueAdded.HALAL] 사용자에게만 노출 (비건·일반 유저는 숨김).
 * - "푸시 알림" 마스터 토글이 꺼지면 다른 토글이 의미 없어지지만,
 *   사용자가 다시 켰을 때 이전 선택을 잃지 않도록 prefs 는 독립적으로 보관한다.
 *   (실제 알림 전송 로직에서 isPushEnabled() && isPrayerAlarmEnabled() 식으로 합쳐 사용)
 */
@Composable
fun NotificationSettingsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { AppSettingsPreferences(context) }
    val onboardingPrefs = remember { OnboardingPreferences(context) }

    var valueAdded by remember { mutableStateOf(onboardingPrefs.getValueAdded()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                valueAdded = onboardingPrefs.getValueAdded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val showPrayerAlarm = valueAdded == ValueAdded.HALAL

    val coroutineScope = rememberCoroutineScope()

    // Android 13+ 알림 권한 요청 launcher (호이스팅 — 조건 내 선언 불가)
    val notificationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 허용 여부와 무관하게 알람은 예약됨. 권한 없으면 알림만 안 뜸 */ }

    var pushEnabled by remember { mutableStateOf(prefs.isPushEnabled()) }
    var prayerAlarmEnabled by remember { mutableStateOf(prefs.isPrayerAlarmEnabled()) }
    var eventPromoEnabled by remember { mutableStateOf(prefs.isEventPromoEnabled()) }
    var dndEnabled by remember { mutableStateOf(prefs.isDoNotDisturbEnabled()) }

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
                title = "알림 설정",
                onBack = { navController.popBackStack() },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = ScanPangDimens.screenHorizontal)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // ── 알림 받기 ──────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileSettingsSectionLabel(text = "알림 받기")
                    ProfileSettingsCard(bordered = false) {
                        ProfileSettingsToggleRow(
                            label = "푸시 알림",
                            subtitle = "모든 알림을 한 번에 켜고 끕니다",
                            icon = Icons.Rounded.Notifications,
                            iconTint = ScanPangColors.Primary,
                            checked = pushEnabled,
                            onCheckedChange = {
                                pushEnabled = it
                                prefs.setPushEnabled(it)
                            },
                            showDividerBelow = false,
                        )
                    }
                }

                // ── 알림 종류 ──────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileSettingsSectionLabel(text = "알림 종류")
                    ProfileSettingsCard(bordered = false) {
                        if (showPrayerAlarm) {
                            ProfileSettingsToggleRow(
                                label = "기도 시간 알림",
                                icon = Icons.Rounded.Mosque,
                                iconTint = ScanPangColors.Primary,
                                checked = prayerAlarmEnabled,
                                onCheckedChange = { enabled ->
                                    prayerAlarmEnabled = enabled
                                    prefs.setPrayerAlarmEnabled(enabled)
                                    if (enabled) {
                                        // Android 13+ 알림 권한 없으면 요청
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.POST_NOTIFICATIONS
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        coroutineScope.launch {
                                            PrayerAlarmScheduler.scheduleForToday(
                                                context,
                                                prefs.getLastKnownLat(),
                                                prefs.getLastKnownLng(),
                                            )
                                        }
                                    } else {
                                        PrayerAlarmScheduler.cancelAll(context)
                                    }
                                },
                                showDividerBelow = true,
                            )
                        }
                        ProfileSettingsToggleRow(
                            label = "이벤트 및 프로모션",
                            icon = Icons.Rounded.Campaign,
                            iconTint = ScanPangColors.Primary,
                            checked = eventPromoEnabled,
                            onCheckedChange = {
                                eventPromoEnabled = it
                                prefs.setEventPromoEnabled(it)
                            },
                            showDividerBelow = false,
                        )
                    }
                }

                // ── 방해 금지 ──────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileSettingsSectionLabel(text = "방해 금지")
                    ProfileSettingsCard(bordered = false) {
                        ProfileSettingsToggleRow(
                            label = "방해 금지 모드",
                            subtitle = "22:00 - 07:00 동안 알림을 받지 않습니다",
                            icon = Icons.Rounded.DoNotDisturbOn,
                            iconTint = ScanPangColors.Primary,
                            checked = dndEnabled,
                            onCheckedChange = {
                                dndEnabled = it
                                prefs.setDoNotDisturbEnabled(it)
                            },
                            showDividerBelow = false,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(ScanPangSpacing.lg))
            }
        }
    }
}
