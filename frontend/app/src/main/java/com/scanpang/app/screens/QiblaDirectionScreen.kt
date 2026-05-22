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
import com.scanpang.app.components.PrayerTimeListCard
import com.scanpang.app.components.QiblaCompass
import com.scanpang.app.components.ScanPangHeaderWithBack
import com.scanpang.app.i18n.AppStrings
import com.scanpang.app.i18n.LocalStrings
import com.scanpang.app.qibla.getMeccaDistanceKm
import com.scanpang.app.qibla.getPrayerTimes
import com.scanpang.app.qibla.getQiblaDirection
import com.scanpang.app.qibla.rememberDeviceAzimuthDegrees
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType
import kotlin.math.roundToInt

@Composable
fun QiblaDirectionScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ScanPangViewModel = viewModel(),
) {
    val s = LocalStrings.current
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

    var locationLine by remember { mutableStateOf(s.qiblaPermissionRequired) }

    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            locationLine = s.qiblaPermissionRequired
            return@LaunchedEffect
        }
        locationLine = s.qiblaLocating
        fusedClient.lastLocation
            .addOnSuccessListener { loc ->
                locationLine = if (loc != null) {
                    s.qiblaLocationFormat(loc.latitude, loc.longitude)
                } else {
                    s.qiblaLocationUnavailable
                }
            }
            .addOnFailureListener {
                locationLine = s.qiblaLocationFailed
            }
    }

    val azimuthState = rememberDeviceAzimuthDegrees()
    val deviceAzimuth = azimuthState.floatValue
    val qiblaFromNorth = apiQibla?.direction?.toFloat() ?: getQiblaDirection()

    val prayerTimes = getPrayerTimes()
    val meccaKm = getMeccaDistanceKm()
    val meccaLine = s.qiblaDistanceFormat(meccaKm.toDouble())

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
                title = s.qiblaTitle,
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
                QiblaCompass(azimuthDegrees = deviceAzimuth, qiblaFromNorth = qiblaFromNorth)
                Text(
                    text = formatQiblaLabel(qiblaFromNorth, s),
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
                    subtitle = s.qiblaNextPrayer,
                    prayerNameTime = apiPrayerTimes?.let { "${it.next_prayer} ${it.next_prayer_time}" }
                        ?: "${prayerTimes.nextPrayerName} ${prayerTimes.nextPrayerTime}",
                    remainingLabel = prayerTimes.remainingLabel,
                )
                apiPrayerTimes?.let { pt ->
                    PrayerTimeListCard(
                        times = listOf(
                            "Fajr" to pt.fajr,
                            "Dhuhr" to pt.dhuhr,
                            "Asr" to pt.asr,
                            "Maghrib" to pt.maghrib,
                            "Isha" to pt.isha,
                        ),
                        nextPrayer = pt.next_prayer,
                    )
                }
            }
        }
    }
}

private fun formatQiblaLabel(degrees: Float, s: AppStrings): String {
    val dirs = listOf(s.dirN, s.dirNE, s.dirE, s.dirSE, s.dirS, s.dirSW, s.dirW, s.dirNW)
    val idx = ((degrees / 45f).roundToInt() % 8 + 8) % 8
    return "${dirs[idx]} ${degrees.roundToInt()}°"
}
