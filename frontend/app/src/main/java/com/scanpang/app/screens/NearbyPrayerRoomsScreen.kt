package com.scanpang.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.scanpang.app.data.DummyData
import com.scanpang.app.data.Place
import com.scanpang.app.data.remote.ScanPangViewModel
import com.scanpang.app.data.toPlace
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType
import androidx.compose.ui.res.stringResource
import com.scanpang.app.R

private fun parseDistanceMetersForSort(line: String): Int {
    Regex("""(\d+(?:\.\d+)?)\s*km""").find(line)?.groupValues?.get(1)?.toDoubleOrNull()
        ?.let { return (it * 1000).toInt() }
    Regex("""(\d+)\s*m""").find(line)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    return Int.MAX_VALUE / 4
}

private fun prayerRoomsForFilter(filterIndex: Int, rooms: List<Place>): List<Place> {
    return when (filterIndex) {
        1 -> rooms.sortedBy { parseDistanceMetersForSort(it.distance) }
        2 -> rooms.filter { room -> room.tags.any { it.contains("남녀") } }
        else -> rooms
    }
}

/**
 * Figma: 주변 기도실 (`197:2256`)
 */
@Composable
fun NearbyPrayerRoomsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val viewModel: ScanPangViewModel = viewModel()
    val apiPrayerRooms by viewModel.prayerRooms.collectAsState()
    val isLoading by viewModel.loading.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadPrayerRooms() }

    var filterIndex by remember { mutableIntStateOf(0) }
    val filterLabels = listOf(
        stringResource(R.string.filter_all),
        stringResource(R.string.filter_sort_distance),
        stringResource(R.string.filter_gender_separated),
    )

    val allRooms = remember(apiPrayerRooms) {
        val fromApi = apiPrayerRooms.map { it.toPlace() }
        fromApi.ifEmpty { DummyData.prayerRooms }
    }
    val prayerPlaces = remember(filterIndex, allRooms) { prayerRoomsForFilter(filterIndex, allRooms) }
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
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                horizontal = ScanPangDimens.screenHorizontal,
                vertical = ScanPangSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = ScanPangColors.OnSurfaceStrong,
                        )
                    }
                    Text(
                        text = stringResource(R.string.nearby_prayer_rooms_title),
                        style = ScanPangType.detailScreenTitle22,
                        color = ScanPangColors.OnSurfaceStrong,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ScanPangShapes.radius14)
                        .clickable {
                            navController.navigate(AppRoutes.Qibla) { launchSingleTop = true }
                        },
                    shape = ScanPangShapes.radius14,
                    color = ScanPangColors.PrimarySoft,
                    border = BorderStroke(
                        ScanPangDimens.borderHairline,
                        ScanPangColors.OutlineSubtle,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(ScanPangSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Explore,
                            contentDescription = null,
                            tint = ScanPangColors.Primary,
                            modifier = Modifier.size(ScanPangDimens.icon18),
                        )
                        Text(
                            text = "stringResource(R.string.nearby_prayer_qibla_link),
                            style = ScanPangType.title14,
                            color = ScanPangColors.Primary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = ScanPangColors.Primary,
                            modifier = Modifier.size(ScanPangDimens.icon18),
                        )
                    }
                }
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ScanPangShapes.radius14,
                    color = ScanPangColors.Background,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ScanPangDimens.searchBarHeightActive)
                            .padding(horizontal = ScanPangDimens.searchBarInnerHorizontal),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = ScanPangColors.OnSurfacePlaceholder,
                        )
                        Text(
                            text = stringResource(R.string.nearby_prayer_search_placeholder),
                            style = ScanPangType.caption12Medium,
                            color = ScanPangColors.OnSurfacePlaceholder,
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                ) {
                    filterLabels.forEachIndexed { index, label ->
                        val selected = index == filterIndex
                        Surface(
                            modifier = Modifier
                                .clip(ScanPangShapes.filterChip)
                                .clickable { filterIndex = index },
                            shape = ScanPangShapes.filterChip,
                            color = if (selected) ScanPangColors.Primary else ScanPangColors.Surface,
                            border = BorderStroke(
                                ScanPangDimens.borderHairline,
                                ScanPangColors.OutlineSubtle,
                            ),
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(
                                    horizontal = ScanPangSpacing.md,
                                    vertical = ScanPangDimens.chipPadVertical,
                                ),
                                style = ScanPangType.caption12Medium,
                                color = if (selected) Color.White else ScanPangColors.OnSurfaceMuted,
                            )
                        }
                    }
                }
            }
            if (isLoading && allRooms == DummyData.prayerRooms) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ScanPangColors.Primary)
                    }
                }
            }
            items(
                items = prayerPlaces,
                key = { it.id },
            ) { place ->
                PrayerRoomRowCard(
                    title = place.name,
                    subtitle = "${place.distance} · ${place.address}",
                    onClick = {
                        navController.navigate(
                            AppRoutes.placeDetailRoute("prayer_room", "")
                        ) { launchSingleTop = true }
                    },
                )
            }
        }
    }
}

@Composable
private fun PrayerRoomRowCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ScanPangShapes.radius14)
            .border(
                ScanPangDimens.borderHairline,
                ScanPangColors.OutlineSubtle,
                ScanPangShapes.radius14,
            )
            .background(ScanPangColors.Surface)
            .clickable(onClick = onClick)
            .padding(ScanPangSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(ScanPangDimens.placeImageHeight)
                .clip(CircleShape)
                .background(ScanPangColors.PrimarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Mosque,
                contentDescription = null,
                modifier = Modifier.size(ScanPangDimens.icon18),
                tint = ScanPangColors.Primary,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ScanPangDimens.stackGap6),
        ) {
            Text(
                text = title,
                style = ScanPangType.title16SemiBold,
                color = ScanPangColors.OnSurfaceStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = ScanPangType.meta11Medium,
                color = ScanPangColors.OnSurfaceMuted,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(ScanPangDimens.icon18),
            tint = ScanPangColors.OnSurfacePlaceholder,
        )
    }
}
