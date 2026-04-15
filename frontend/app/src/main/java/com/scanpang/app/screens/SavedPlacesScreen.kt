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
import com.scanpang.app.components.SavedPlaceTag
import com.scanpang.app.components.SavedPlaceTagStyle
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
    "전체", "식당", "카페", "편의점", "쇼핑", "환전소", "은행", "ATM",
    "병원", "약국", "지하철역", "화장실", "물품보관함",
)

private enum class SavedSort {
    ByDistance,
    ByRecent,
}

private enum class SavedTarget {
    Restaurant,
    PrayerRoom,
}

private data class SavedPlaceRow(
    val title: String,
    val categoryLabel: String,
    val distanceLine: String,
    val tags: List<SavedPlaceTag>,
    val distanceMeters: Int,
    val savedOrder: Long,
    val target: SavedTarget,
)

private fun tagLabelToStyle(label: String): SavedPlaceTagStyle = when {
    label.contains("인증") -> SavedPlaceTagStyle.Success
    label.contains("인기") || label.contains("필수") -> SavedPlaceTagStyle.Warning
    else -> SavedPlaceTagStyle.Neutral
}

private fun parseDistanceMeters(line: String): Int {
    Regex("""(\d+(?:\.\d+)?)\s*km""").find(line)?.groupValues?.get(1)?.toDoubleOrNull()
        ?.let { return (it * 1000).toInt() }
    Regex("""(\d+)\s*m""").find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    return Int.MAX_VALUE / 4
}

private fun SavedPlaceEntry.toUiRow(): SavedPlaceRow {
    val target = when (this.target) {
        SavedPlaceNavTarget.Restaurant -> SavedTarget.Restaurant
        SavedPlaceNavTarget.PrayerRoom -> SavedTarget.PrayerRoom
    }
    return SavedPlaceRow(
        title = name,
        categoryLabel = category,
        distanceLine = distanceLine,
        tags = tags.map { SavedPlaceTag(it, tagLabelToStyle(it)) },
        distanceMeters = parseDistanceMeters(distanceLine),
        savedOrder = savedOrder,
        target = target,
    )
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
        containerColor = ScanPangColors.Background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { _ ->
        if (entries.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ScanPangColors.Background)
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
                    .background(ScanPangColors.Background)
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
                        tags = row.tags,
                        onClick = {
                            when (row.target) {
                                SavedTarget.Restaurant ->
                                    navController.navigate(AppRoutes.RestaurantDetail) { launchSingleTop = true }
                                SavedTarget.PrayerRoom ->
                                    navController.navigate(AppRoutes.PrayerRoomDetail) { launchSingleTop = true }
                            }
                        },
                    )
                }
            }
        }
    }
}
