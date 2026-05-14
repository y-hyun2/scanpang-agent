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

private val filterLabels = listOf(
    "전체", "식당", "카페", "편의점", "쇼핑", "관광지", "기도실", "환전소", "은행", "ATM",
    "병원", "약국", "지하철역", "화장실", "물품보관함",
)

private enum class SavedSort {
    ByDistance,
    ByRecent,
}

private data class SavedPlaceRow(
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

// Detail 화면들이 distanceLine 을 "카테고리 · 80m" 처럼 카테고리 prefix 와 함께 저장한다.
// 저장한 장소 카드는 이미 좌측에 파란 카테고리 pill 을 노출하므로, 회색 텍스트 영역에는
// 거리값만 남기기 위해 " · " 직전의 카테고리 부분을 잘라낸다.
// (마지막 " · " 만 잘라내므로 "지하철 1호선, 4호선 · 80m" 처럼 중간 콤마가 있어도 안전.)
private fun stripCategoryPrefix(line: String): String {
    val idx = line.lastIndexOf(" · ")
    return if (idx >= 0) line.substring(idx + 3).trim() else line.trim()
}

private fun SavedPlaceEntry.toUiRow(): SavedPlaceRow = SavedPlaceRow(
    title = name,
    categoryLabel = category,
    distanceLine = stripCategoryPrefix(distanceLine),
    distanceMeters = parseDistanceMeters(distanceLine),
    savedOrder = savedOrder,
    navTarget = target,
)

private fun NavController.navigateToSavedDetail(target: SavedPlaceNavTarget) {
    val route = when (target) {
        SavedPlaceNavTarget.Restaurant -> AppRoutes.RestaurantDetail
        SavedPlaceNavTarget.PrayerRoom -> AppRoutes.PrayerRoomDetail
        SavedPlaceNavTarget.TouristSpot -> AppRoutes.TouristDetail
        SavedPlaceNavTarget.Shopping -> AppRoutes.ShoppingDetail
        SavedPlaceNavTarget.ConvenienceStore -> AppRoutes.ConvenienceDetail
        SavedPlaceNavTarget.Cafe -> AppRoutes.CafeDetail
        SavedPlaceNavTarget.Atm -> AppRoutes.AtmDetail
        SavedPlaceNavTarget.Bank -> AppRoutes.BankDetail
        SavedPlaceNavTarget.Exchange -> AppRoutes.ExchangeDetail
        SavedPlaceNavTarget.Subway -> AppRoutes.SubwayDetail
        SavedPlaceNavTarget.Restroom -> AppRoutes.RestroomDetail
        SavedPlaceNavTarget.Lockers -> AppRoutes.LockersDetail
        SavedPlaceNavTarget.Hospital -> AppRoutes.HospitalDetail
        SavedPlaceNavTarget.Pharmacy -> AppRoutes.PharmacyDetail
    }
    navigate(route) { launchSingleTop = true }
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

    val rows = remember(entries, filterIndex) {
        val mapped = entries.map { it.toUiRow() }
        if (filterIndex == 0) mapped
        else {
            val label = filterLabels[filterIndex]
            mapped.filter { row ->
                row.categoryLabel == label || row.categoryLabel.contains(label)
            }
        }
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
                        itemsIndexed(filterLabels) { index, label ->
                            ScanPangFilterChip(
                                label = label,
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
                        onClick = { navController.navigateToSavedDetail(row.navTarget) },
                    )
                }
            }
        }
    }
}
