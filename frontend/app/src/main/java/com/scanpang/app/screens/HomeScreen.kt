package com.scanpang.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.scanpang.app.data.remote.ScanPangViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import java.util.Locale
import com.scanpang.app.data.OnboardingPreferences
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

@Composable
fun HomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ScanPangViewModel = viewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.loadPrayerTimesAndQibla()
    }

    val prayerTimes by viewModel.prayerTimes.collectAsState()
    val qibla by viewModel.qibla.collectAsState()

    val qiblaText = qibla?.let { "키블라 방향: ${it.direction.toInt()}°" } ?: "키블라 방향: 292°"
    val nextPrayerText = prayerTimes?.let { "다음 기도: ${it.next_prayer} ${it.next_prayer_time}" } ?: "다음 기도: Dhuhr 12:15"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScanPangColors.Surface,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScanPangColors.Surface)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = ScanPangDimens.mainTabContentBottomInset),
        ) {
            HomeTopSection(
                navController = navController,
                qiblaText = qiblaText,
                nextPrayerText = nextPrayerText,
            )
        }
    }
}

@Composable
private fun HomeTopSection(
    navController: NavController,
    qiblaText: String = "키블라 방향: 남서 232°",
    nextPrayerText: String = "다음 기도: Dhuhr 12:15",
) {
    val context = LocalContext.current
    val savedName = remember(context) {
        OnboardingPreferences(context).getDisplayName()
    }
    val greetingLine = if (!savedName.isNullOrBlank()) {
        "안녕하세요, ${savedName}님!"
    } else {
        "안녕하세요!"
    }

    // GPS + 역지오코딩으로 현재 위치 표시
    var locationText by remember { mutableStateOf("현재 위치를 가져오는 중...") }
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            locationText = "위치 권한이 필요합니다"
            return@LaunchedEffect
        }
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                try {
                    @Suppress("DEPRECATION")
                    val geocoder = Geocoder(context, Locale.KOREAN)
                    val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        val dong = addr.subLocality ?: addr.thoroughfare ?: ""
                        val detail = addr.featureName ?: ""
                        locationText = "현재 위치: ${dong} ${detail} 근처".trim()
                    } else {
                        locationText = "현재 위치: %.4f, %.4f".format(loc.latitude, loc.longitude)
                    }
                } catch (_: Exception) {
                    locationText = "현재 위치: %.4f, %.4f".format(loc.latitude, loc.longitude)
                }
            } else {
                locationText = "현재 위치를 확인할 수 없습니다"
            }
        }.addOnFailureListener {
            locationText = "위치 가져오기 실패"
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScanPangSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ScanPangDimens.homeSectionGap),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = ScanPangDimens.homeHeaderInset,
                    end = ScanPangDimens.homeHeaderInset,
                    top = ScanPangDimens.homeHeaderTop,
                    bottom = ScanPangDimens.homeHeaderInset,
                ),
            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
        ) {
            Text(
                text = "$greetingLine\n오늘 명동을 탐험해볼까요?",
                style = ScanPangType.homeGreeting,
                color = ScanPangColors.OnSurfaceStrong,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.AltRoute,
                    contentDescription = null,
                    modifier = Modifier.size(ScanPangDimens.icon20),
                    tint = ScanPangColors.OnSurfaceMuted,
                )
                Text(
                    text = locationText,
                    style = ScanPangType.meta13,
                    color = ScanPangColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ScanPangDimens.homeSectionGap),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ScanPangDimens.homeSearchBarHeight)
                    .clip(ScanPangShapes.radius14)
                    .background(ScanPangColors.Background)
                    .clickable { navController.navigate(AppRoutes.Search) }
                    .padding(horizontal = ScanPangSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Rounded.NearMe,
                    contentDescription = null,
                    modifier = Modifier.size(ScanPangDimens.tabIcon),
                    tint = ScanPangColors.OnSurfacePlaceholder,
                )
                Text(
                    text = "목적지 검색",
                    style = ScanPangType.searchPlaceholder,
                    color = ScanPangColors.OnSurfacePlaceholder,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ScanPangDimens.homeSectionGap),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ScanPangShapes.radius14)
                    .background(ScanPangColors.PrimarySoft)
                    .clickable { navController.navigate(AppRoutes.Qibla) }
                    .padding(horizontal = ScanPangSpacing.lg, vertical = ScanPangDimens.homeQiblaRowVertical),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Explore,
                            contentDescription = null,
                            modifier = Modifier.size(ScanPangDimens.tabIcon),
                            tint = ScanPangColors.Primary,
                        )
                        Text(
                            text = qiblaText,
                            style = ScanPangType.title14,
                            color = ScanPangColors.OnSurfaceStrong,
                        )
                    }
                    Text(
                        text = nextPrayerText,
                        style = ScanPangType.caption12Medium,
                        color = ScanPangColors.OnSurfaceMuted,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(ScanPangDimens.icon20),
                    tint = ScanPangColors.Primary,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ScanPangDimens.homeSectionGap),
            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
        ) {
            QuickActionChip(
                title = "할랄 식당",
                icon = Icons.Rounded.Restaurant,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(AppRoutes.NearbyHalal) },
            )
            QuickActionChip(
                title = "기도실",
                icon = Icons.Rounded.Mosque,
                modifier = Modifier.weight(1f),
                onClick = { navController.navigate(AppRoutes.NearbyPrayer) },
            )
            QuickActionChip(
                title = "실시간 번역",
                icon = Icons.Rounded.Translate,
                modifier = Modifier.weight(1f),
                onClick = { },
            )
        }
    }
}

@Composable
private fun QuickActionChip(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(ScanPangShapes.radius14)
            .background(ScanPangColors.Background)
            .clickable(onClick = onClick)
            .padding(horizontal = ScanPangDimens.homeQuickChipHorizontal, vertical = ScanPangSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(ScanPangDimens.tabIcon),
            tint = ScanPangColors.Primary,
        )
        Text(
            text = title,
            style = ScanPangType.quickLabel12,
            color = ScanPangColors.OnSurfaceStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
