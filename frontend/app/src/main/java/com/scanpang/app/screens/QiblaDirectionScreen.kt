package com.scanpang.app.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanpang.app.data.remote.ScanPangViewModel
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.scanpang.app.components.PrayerTimeCard
import com.scanpang.app.components.QiblaCompass
import com.scanpang.app.components.ScanPangHeaderWithBack
import com.scanpang.app.qibla.getMeccaDistanceKm
import com.scanpang.app.qibla.getPrayerTimes
import com.scanpang.app.qibla.getQiblaDirection
import com.scanpang.app.qibla.rememberDeviceAzimuthDegrees
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun QiblaDirectionScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ScanPangViewModel = viewModel(),
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadPrayerTimesAndQibla() }
    val apiPrayerTimes by viewModel.prayerTimes.collectAsState()
    val apiQibla by viewModel.qibla.collectAsState()
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasLocationPermission = result.values.any { it }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    var locationLine by remember { mutableStateOf("위치 권한을 허용해 주세요") }

    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            locationLine = "위치 권한을 허용해 주세요"
            return@LaunchedEffect
        }
        locationLine = "위치를 가져오는 중…"
        fusedClient.lastLocation
            .addOnSuccessListener { loc ->
                locationLine = if (loc != null) {
                    String.format(
                        Locale.getDefault(),
                        "현재 위치: 위도 %.5f, 경도 %.5f",
                        loc.latitude,
                        loc.longitude,
                    )
                } else {
                    "현재 위치: 일시적으로 확인할 수 없습니다"
                }
            }
            .addOnFailureListener {
                locationLine = "현재 위치: 가져오기에 실패했습니다"
            }
    }

    val azimuthState = rememberDeviceAzimuthDegrees()
    val deviceAzimuth = azimuthState.floatValue
    val qiblaFromNorth = apiQibla?.direction?.toFloat() ?: getQiblaDirection()
    val needleRotation = ((qiblaFromNorth - deviceAzimuth + 360f) % 360f)

    val prayerTimes = getPrayerTimes()
    val meccaKm = getMeccaDistanceKm()
    val meccaLine = String.format(
        Locale.getDefault(),
        "메카까지 거리: %,.0f km",
        meccaKm.toDouble(),
    )

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
                .navigationBarsPadding()
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
                QiblaCompass(bearingDegrees = needleRotation)
                Text(
                    text = formatQiblaLabel(qiblaFromNorth),
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
                        text = locationLine,
                        style = ScanPangType.link13,
                        color = ScanPangColors.OnSurfaceMuted,
                        maxLines = 3,
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
                        text = meccaLine,
                        style = ScanPangType.link13,
                        color = ScanPangColors.OnSurfaceMuted,
                    )
                }
                PrayerTimeCard(
                    subtitle = "다음 기도 시간",
                    prayerNameTime = apiPrayerTimes?.let { "${it.next_prayer} ${it.next_prayer_time}" }
                        ?: "${prayerTimes.nextPrayerName} ${prayerTimes.nextPrayerTime}",
                    remainingLabel = prayerTimes.remainingLabel,
                )
                apiPrayerTimes?.let { pt ->
                    PrayerTimeCard(subtitle = "Fajr", prayerNameTime = pt.fajr, remainingLabel = "")
                    PrayerTimeCard(subtitle = "Dhuhr", prayerNameTime = pt.dhuhr, remainingLabel = "")
                    PrayerTimeCard(subtitle = "Asr", prayerNameTime = pt.asr, remainingLabel = "")
                    PrayerTimeCard(subtitle = "Maghrib", prayerNameTime = pt.maghrib, remainingLabel = "")
                    PrayerTimeCard(subtitle = "Isha", prayerNameTime = pt.isha, remainingLabel = "")
                }
            }
        }
    }
}

private fun formatQiblaLabel(degrees: Float): String {
    val dirs = listOf("북", "북동", "동", "남동", "남", "남서", "서", "북서")
    val idx = ((degrees / 45f).roundToInt() % 8 + 8) % 8
    return "${dirs[idx]} ${degrees.roundToInt()}°"
}
