package com.scanpang.app.screens.profile

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
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.scanpang.app.components.ProfileSettingsCard
import com.scanpang.app.components.ProfileSettingsSectionLabel
import com.scanpang.app.components.ProfileSettingsToggleRow
import com.scanpang.app.data.AppSettingsPreferences
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangSpacing
import androidx.compose.ui.res.stringResource
import com.scanpang.app.R

@Composable
fun NotificationSettingsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember { AppSettingsPreferences(context) }

    var pushEnabled by remember { mutableStateOf(prefs.isPushEnabled()) }
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
                title = stringResource(R.string.settings_notification_title),
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
                    ProfileSettingsSectionLabel(text = stringResource(R.string.notification_section_receive))
                    ProfileSettingsCard(bordered = false) {
                        ProfileSettingsToggleRow(
                            label = stringResource(R.string.notification_push_label),
                            subtitle = stringResource(R.string.notification_push_subtitle),
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
                    ProfileSettingsSectionLabel(text = stringResource(R.string.notification_section_type))
                    ProfileSettingsCard(bordered = false) {
                        ProfileSettingsToggleRow(
                            label = stringResource(R.string.notification_event_promo_label),
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
                    ProfileSettingsSectionLabel(text = stringResource(R.string.notification_section_dnd))
                    ProfileSettingsCard(bordered = false) {
                        ProfileSettingsToggleRow(
                            label = stringResource(R.string.notification_dnd_label),
                            subtitle = stringResource(R.string.notification_dnd_subtitle),
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
