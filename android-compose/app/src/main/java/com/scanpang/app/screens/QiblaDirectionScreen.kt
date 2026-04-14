package com.scanpang.app.screens

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import com.scanpang.app.components.PrayerTimeCard
import com.scanpang.app.components.QiblaCompass
import com.scanpang.app.components.ScanPangBottomBar
import com.scanpang.app.components.ScanPangHeaderWithBack
import com.scanpang.app.components.ScanPangMainTab
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

@Composable
fun QiblaDirectionScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    vm: com.scanpang.app.data.HalalViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val prayerTimes by vm.prayerTimes.collectAsState()
    val qibla by vm.qibla.collectAsState()
    val qiblaDir = qibla?.direction?.toInt() ?: 232
    val qiblaCompass = when {
        qiblaDir >= 292 -> "북서"; qiblaDir >= 247 -> "서"; qiblaDir >= 202 -> "남서"
        qiblaDir >= 157 -> "남"; qiblaDir >= 112 -> "남동"; qiblaDir >= 67 -> "동"
        qiblaDir >= 22 -> "북동"; else -> "북"
    }
    val nextPrayerText = run {
        val pt = prayerTimes ?: return@run "Dhuhr 12:15"
        val now = java.util.Calendar.getInstance()
        val hhmm = "%02d:%02d".format(now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE))
        val prayers = listOf("Fajr" to pt.fajr, "Dhuhr" to pt.dhuhr, "Asr" to pt.asr, "Maghrib" to pt.maghrib, "Isha" to pt.isha)
        val next = prayers.firstOrNull { it.second > hhmm }
        if (next != null) "${next.first} ${next.second}" else "Fajr ${pt.fajr}"
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScanPangColors.Surface,
        bottomBar = {
            ScanPangBottomBar(
                selectedTab = ScanPangMainTab.Home,
                onHomeClick = { navController.navigate(AppRoutes.Home) { launchSingleTop = true } },
                onSearchClick = { navController.navigate(AppRoutes.Search) { launchSingleTop = true } },
                onSavedClick = { navController.navigate(AppRoutes.Saved) { launchSingleTop = true } },
                onProfileClick = { navController.navigate(AppRoutes.Profile) { launchSingleTop = true } },
                onExploreClick = {
                    navController.navigate(AppRoutes.ArDefault) { launchSingleTop = true }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ScanPangColors.Surface)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.xl),
        ) {
            ScanPangHeaderWithBack(
                title = "키블라 방향",
                onBackClick = { navController.popBackStack() },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScanPangDimens.screenHorizontal)
                    .padding(top = ScanPangSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ScanPangDimens.qiblaCompassSectionGap),
            ) {
                QiblaCompass(bearingDegrees = qiblaDir.toFloat())
                Text(
                    text = "$qiblaCompass ${qiblaDir}°",
                    style = ScanPangType.directionDegree,
                    color = ScanPangColors.Primary,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScanPangDimens.screenHorizontal)
                    .padding(bottom = ScanPangSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.lg),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(ScanPangDimens.icon16),
                        tint = ScanPangColors.OnSurfaceMuted,
                    )
                    Text(
                        text = "현재 위치: 명동역 6번 출구 근처",
                        style = ScanPangType.link13,
                        color = ScanPangColors.OnSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        modifier = Modifier.size(ScanPangDimens.icon16),
                        tint = ScanPangColors.OnSurfaceMuted,
                    )
                    Text(
                        text = "메카까지 거리: 8,565 km",
                        style = ScanPangType.link13,
                        color = ScanPangColors.OnSurfaceMuted,
                    )
                }
                PrayerTimeCard(
                    subtitle = "다음 기도 시간",
                    prayerNameTime = nextPrayerText,
                    remainingLabel = "2시간 34분 남음",
                )
            }
        }
    }
}
