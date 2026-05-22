package com.scanpang.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.scanpang.app.components.SavedPlaceCard
import com.scanpang.app.components.ScanPangFilterChip
import com.scanpang.app.data.SavedPlaceEntry
import com.scanpang.app.data.SavedPlaceNavTarget
import com.scanpang.app.data.SavedPlacesStore
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

// 필터 chip 한국어 라벨 → 매칭할 target enum 집합.
// SavedPlaceEntry.category 의 한국어 string contains 매칭은 표기 변동(예: "할랄 식당"
// vs "식당") 에 약하므로 target enum (= category_key) 기반 정확 매칭으로 통일.
// "식당" 탭은 일반 + 할랄 식당 둘 다 포함(SavedPlaceNavTarget.fromCategoryKey 에서
// halal_restaurant 도 Restaurant 로 매핑됨).
private data class SavedFilter(val label: String, val targets: Set<SavedPlaceNavTarget>)

private val savedFilters = listOf(
    SavedFilter("전체", emptySet()),  // 빈 set = 필터 안 함
    SavedFilter("식당", setOf(SavedPlaceNavTarget.Restaurant)),
    SavedFilter("카페", setOf(SavedPlaceNavTarget.Cafe)),
    SavedFilter("편의점", setOf(SavedPlaceNavTarget.ConvenienceStore)),
    SavedFilter("쇼핑", setOf(SavedPlaceNavTarget.Shopping)),
    SavedFilter("관광지", setOf(SavedPlaceNavTarget.TouristSpot)),
    SavedFilter("기도실", setOf(SavedPlaceNavTarget.PrayerRoom)),
    SavedFilter("환전소", setOf(SavedPlaceNavTarget.Exchange)),
    SavedFilter("은행", setOf(SavedPlaceNavTarget.Bank)),
    SavedFilter("ATM", setOf(SavedPlaceNavTarget.Atm)),
    SavedFilter("병원", setOf(SavedPlaceNavTarget.Hospital)),
    SavedFilter("약국", setOf(SavedPlaceNavTarget.Pharmacy)),
    SavedFilter("지하철역", setOf(SavedPlaceNavTarget.Subway)),
    SavedFilter("화장실", setOf(SavedPlaceNavTarget.Restroom)),
    SavedFilter("물품보관함", setOf(SavedPlaceNavTarget.Lockers)),
)

private enum class SavedSort {
    ByDistance,
    ByRecent,
}

private data class SavedPlaceRow(
    val id: String,
    val title: String,
    val categoryLabel: String,
    val distanceLine: String,
    val distanceMeters: Int,
    val savedOrder: Long,
    val navTarget: SavedPlaceNavTarget,
)

private fun parseDistanceMeters(line: String): Int {
    Regex("""(\d+(?:\.\d+)?)\s*km""").find(line)?.groupValues?.get(1)?.toDoubleOrNull()
        ?.let { return (it * 1000).toInt() }
    Regex("""(\d+)\s*m""").find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    return Int.MAX_VALUE / 4
}

private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLng / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(a))
}

private fun formatDistanceLabel(meters: Int): String = when {
    meters < 1000 -> "${meters}m"
    else          -> "%.1fkm".format(meters / 1000.0)
}

// Detail 화면들이 distanceLine 을 "카테고리 · 80m" 처럼 카테고리 prefix 와 함께 저장한다.
// 저장한 장소 카드는 이미 좌측에 파란 카테고리 pill 을 노출하므로, 회색 텍스트 영역에는
// 거리값만 남기기 위해 " · " 직전의 카테고리 부분을 잘라낸다.
// (마지막 " · " 만 잘라내므로 "지하철 1호선, 4호선 · 80m" 처럼 중간 콤마가 있어도 안전.)
private fun stripCategoryPrefix(line: String): String {
    val idx = line.lastIndexOf(" · ")
    return if (idx >= 0) line.substring(idx + 3).trim() else line.trim()
}

/**
 * 사용자 현재 위치 기준 거리 동적 계산.
 *  - lat/lng 모두 있으면 Haversine 으로 실거리 계산
 *  - 좌표 없거나(0.0/0.0 = 옛 저장 row) GPS 미수신이면 distanceLine 의 박힌 값 사용
 */
private fun SavedPlaceEntry.toUiRow(userLat: Double?, userLng: Double?): SavedPlaceRow {
    val hasCoords = lat != 0.0 && lng != 0.0
    val (meters, label) = if (hasCoords && userLat != null && userLng != null) {
        val m = haversineMeters(userLat, userLng, lat, lng).toInt()
        m to formatDistanceLabel(m)
    } else {
        parseDistanceMeters(distanceLine) to stripCategoryPrefix(distanceLine)
    }
    return SavedPlaceRow(
        id = id,
        title = name,
        categoryLabel = category,
        distanceLine = label,
        distanceMeters = meters,
        savedOrder = savedOrder,
        navTarget = target,
    )
}

private fun NavController.navigateToSavedDetail(target: SavedPlaceNavTarget, placeId: String) {
    navigate(AppRoutes.placeDetailRoute(target.toCategoryKey(), placeId)) { launchSingleTop = true }
}

@Composable
fun SavedPlacesScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val store = remember { SavedPlacesStore(context) }
    var entries by remember { mutableStateOf(store.getAll()) }

    // GPS — 저장된 각 장소까지의 거리 동적 계산용. 권한 없거나 lastLocation null
    // 이면 distanceLine 의 박힌 값으로 폴백.
    var userLat by remember { mutableStateOf<Double?>(null) }
    var userLng by remember { mutableStateOf<Double?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val hasPermission =
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_FINE_LOCATION
                ) ||
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
        if (!hasPermission) return@LaunchedEffect
        val client = com.google.android.gms.location.LocationServices
            .getFusedLocationProviderClient(context)
        client.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                userLat = loc.latitude; userLng = loc.longitude
            } else {
                client.getCurrentLocation(
                    com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    null,
                ).addOnSuccessListener { fresh ->
                    if (fresh != null) {
                        userLat = fresh.latitude; userLng = fresh.longitude
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                entries = store.getAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var filterIndex by remember { mutableIntStateOf(0) }
    var sort by remember { mutableStateOf(SavedSort.ByDistance) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val rows = remember(entries, filterIndex, userLat, userLng) {
        val mapped = entries.map { it.toUiRow(userLat, userLng) }
        val filter = savedFilters[filterIndex]
        if (filter.targets.isEmpty()) mapped   // "전체"
        else mapped.filter { it.navTarget in filter.targets }
    }

    val sortedRows = remember(sort, rows) {
        when (sort) {
            SavedSort.ByDistance -> rows.sortedBy { it.distanceMeters }
            SavedSort.ByRecent -> rows.sortedByDescending { it.savedOrder }
        }
    }

    val sortLabel = when (sort) {
        SavedSort.ByDistance -> "가까운 순"
        SavedSort.ByRecent -> "최근 저장 순"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScanPangColors.Surface,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { _ ->
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ScanPangColors.Surface)
                    .statusBarsPadding()
                    .padding(horizontal = ScanPangDimens.screenHorizontal)
                    .padding(bottom = ScanPangDimens.mainTabContentBottomInset),
            ) {
                Text(
                    text = "저장한 장소",
                    style = ScanPangType.homeGreeting,
                    color = ScanPangColors.OnSurfaceStrong,
                    modifier = Modifier.padding(top = ScanPangSpacing.sm),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "저장한 장소가 없어요",
                        style = ScanPangType.body15Medium,
                        color = ScanPangColors.OnSurfaceMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ScanPangColors.Surface)
                    .statusBarsPadding()
                    .padding(horizontal = ScanPangDimens.screenHorizontal)
                    .padding(bottom = ScanPangDimens.mainTabContentBottomInset),
                verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.lg),
            ) {
                item {
                    Text(
                        text = "저장한 장소",
                        style = ScanPangType.homeGreeting,
                        color = ScanPangColors.OnSurfaceStrong,
                        modifier = Modifier.padding(top = ScanPangSpacing.sm),
                    )
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                    ) {
                        itemsIndexed(savedFilters) { index, f ->
                            ScanPangFilterChip(
                                label = f.label,
                                selected = filterIndex == index,
                                onClick = { filterIndex = index },
                            )
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${sortedRows.size}개의 장소",
                            style = ScanPangType.link13,
                            color = ScanPangColors.OnSurfaceMuted,
                        )
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(ScanPangShapes.sortButton)
                                    .background(ScanPangColors.Background)
                                    .clickable { sortMenuExpanded = true }
                                    .padding(
                                        start = ScanPangDimens.sortButtonPaddingStart,
                                        end = ScanPangDimens.sortButtonPaddingEnd,
                                        top = ScanPangDimens.sortButtonPaddingVertical,
                                        bottom = ScanPangDimens.sortButtonPaddingVertical,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SwapVert,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(start = ScanPangSpacing.xs)
                                        .size(ScanPangDimens.icon14),
                                    tint = ScanPangColors.OnSurfaceStrong,
                                )
                                Text(
                                    text = sortLabel,
                                    style = ScanPangType.sort12SemiBold,
                                    color = ScanPangColors.OnSurfaceStrong,
                                )
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(ScanPangDimens.icon14),
                                    tint = ScanPangColors.OnSurfaceStrong,
                                )
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "가까운 순",
                                            style = ScanPangType.body15Medium,
                                            color = ScanPangColors.OnSurfaceStrong,
                                        )
                                    },
                                    onClick = {
                                        sort = SavedSort.ByDistance
                                        sortMenuExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "최근 저장 순",
                                            style = ScanPangType.body15Medium,
                                            color = ScanPangColors.OnSurfaceStrong,
                                        )
                                    },
                                    onClick = {
                                        sort = SavedSort.ByRecent
                                        sortMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                items(sortedRows) { row ->
                    SavedPlaceCard(
                        title = row.title,
                        categoryLabel = row.categoryLabel,
                        distanceLine = row.distanceLine,
                        onClick = { navController.navigateToSavedDetail(row.navTarget, row.id) },
                    )
                }
            }
        }
    }
}
