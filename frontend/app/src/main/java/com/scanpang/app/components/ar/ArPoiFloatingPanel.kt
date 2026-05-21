package com.scanpang.app.components.ar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalPhone
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Stairs
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scanpang.app.data.remote.ArOverlay
import com.scanpang.app.data.remote.Docent
import com.scanpang.app.data.remote.FloorInfo
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

const val ArPoiTabBuilding = "building"
const val ArPoiTabFloors = "floors"

private val DetailTabTrackGray = Color(0xFFEBEBEB)
private val DetailChipBg = Color(0xFFF3F4F6)
private val DetailHalalChipBg = Color(0xFFE8F5E9)
private val DetailHalalChipFg = Color(0xFF2E7D32)

private data class ArFloorStoreLine(val name: String, val category: String, val isHalal: Boolean)

private data class ArFloorSectionUi(
    val label: String,
    val storeCount: Int,
    val categoryLabel: String,
    val stores: List<ArFloorStoreLine>,
)

/**
 * AR 탐색·길안내 공통 — 건물 정보 플로팅 패널 (361×352, 상단 Y=230).
 */
@Composable
fun ArPoiFloatingDetailOverlay(
    poiName: String,
    activeDetailTab: String,
    onActiveDetailTabChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onFloorStoreClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    isSaved: Boolean = false,
    onSave: () -> Unit = {},
    arOverlay: ArOverlay? = null,
    docent: Docent? = null,
) {
    var expandedFloors by remember { mutableStateOf(setOf("B1")) }
    val floorData = remember(arOverlay) {
        if (arOverlay != null && arOverlay.floor_info.isNotEmpty()) {
            arOverlay.floor_info.map { fi ->
                ArFloorSectionUi(
                    label = fi.floor,
                    storeCount = fi.stores.size,
                    categoryLabel = fi.stores.map { it.category }.distinct().take(2).joinToString("·"),
                    stores = fi.stores.map { ArFloorStoreLine(it.name, it.category, false) },
                )
            }
        } else {
            emptyList()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScanPangColors.ArOverlayScrimDark)
                .clickable { onDismiss() },
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = ScanPangDimens.detailArPanelTop)
                .width(ScanPangDimens.detailArPanelWidth)
                .height(ScanPangDimens.detailArPanelHeight)
                .clickable(enabled = false) { },
            shape = ScanPangShapes.radius16,
            color = Color.White.copy(alpha = 0.93f),
            shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = ScanPangSpacing.md)
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Header: Close (left) · Name · Bookmark (right) — matches hufs-cdp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "닫기",
                        tint = ScanPangColors.OnSurfaceMuted,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { onDismiss() },
                    )
                    Text(
                        text = poiName,
                        style = ScanPangType.profileName18,
                        color = ScanPangColors.OnSurfaceStrong,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSaved) ScanPangColors.PrimarySoft else DetailTabTrackGray)
                            .clickable { onSave() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = "저장",
                            tint = if (isSaved) ScanPangColors.Primary else ScanPangColors.OnSurfaceMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                ArPoiStatusMetaRow(
                    category = arOverlay?.category ?: "",
                    openHours = arOverlay?.open_hours ?: "",
                    isEstimated = arOverlay?.is_estimated ?: false,
                )
                ArPoiDetailSegmentedTabs(
                    active = activeDetailTab,
                    onSelect = onActiveDetailTabChange,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (activeDetailTab) {
                        ArPoiTabBuilding -> ArPoiBuildingTabBody(arOverlay = arOverlay)
                        ArPoiTabFloors -> ArPoiFloorsTabBody(
                            floors = floorData,
                            expanded = expandedFloors,
                            onToggle = { id ->
                                expandedFloors =
                                    if (id in expandedFloors) expandedFloors - id else expandedFloors + id
                            },
                            onStoreClick = onFloorStoreClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArPoiStatusMetaRow(
    category: String = "",
    openHours: String = "",
    isEstimated: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (category.isNotBlank() || isEstimated) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
            ) {
                if (category.isNotBlank()) {
                    Surface(
                        shape = ScanPangShapes.badge6,
                        color = ScanPangColors.PrimarySoft,
                    ) {
                        Text(
                            text = category,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = ScanPangType.caption12Medium,
                            color = ScanPangColors.Primary,
                        )
                    }
                }
                if (isEstimated) {
                    Surface(
                        shape = ScanPangShapes.badge6,
                        color = Color(0xFFFFF3E0),
                    ) {
                        Text(
                            text = "AI 추정",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = ScanPangType.caption12Medium,
                            color = Color(0xFFE65100),
                        )
                    }
                }
            }
        }
        if (openHours.isNotBlank()) {
            Text(
                text = openHours,
                style = ScanPangType.body14Regular,
                color = ScanPangColors.OnSurfaceMuted,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ScanPangColors.Success),
                )
                Text(
                    text = "영업 중",
                    style = ScanPangType.caption12Medium,
                    color = ScanPangColors.Success,
                )
            }
        }
    }
}

@Composable
private fun ArPoiDetailSegmentedTabs(
    active: String,
    onSelect: (String) -> Unit,
) {
    val tabs = listOf(
        ArPoiTabBuilding to "건물 정보",
        ArPoiTabFloors to "층별 정보",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = DetailTabTrackGray,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            tabs.forEach { (key, label) ->
                val selected = active == key
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(key) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) ScanPangColors.Primary else Color.Transparent,
                ) {
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        style = ScanPangType.caption12Medium,
                        color = if (selected) Color.White else ScanPangColors.OnSurfaceMuted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArPoiBuildingTabBody(arOverlay: ArOverlay? = null) {
    // 데이터가 아직 안 왔거나 place_info 에 없는 건물 — 빈 상태 표시
    if (arOverlay == null) {
        ArPoiEmptyState(message = "건물 정보를 불러오는 중이에요…")
        return
    }
    val hasAnyInfo = listOf(
        arOverlay.description,
        arOverlay.open_hours,
        arOverlay.address,
        arOverlay.phone,
        arOverlay.parking_info,
        arOverlay.admission_fee,
        arOverlay.homepage,
        arOverlay.image_url,
    ).any { it.isNotBlank() } || arOverlay.floor_info.isNotEmpty()
    if (!hasAnyInfo) {
        ArPoiEmptyState(message = "아직 등록된 건물 정보가 없어요.\n곧 업데이트될 예정입니다.")
        return
    }

    val imageUrls = listOfNotNull(arOverlay.image_url.ifEmpty { null })
    val placeholderColors = listOf(
        Color(0xFFE8E8E8), Color(0xFFD8D8D8), Color(0xFFC8C8C8), Color(0xFFB8B8B8),
    )
    val buildingImageCount = if (imageUrls.isNotEmpty()) imageUrls.size else placeholderColors.size
    val buildingImageBg = placeholderColors
    val pagerState = rememberPagerState(pageCount = { buildingImageCount })
    var buildingGalleryFullscreen by remember { mutableStateOf(false) }
    val currentBuildingPage = pagerState.currentPage

    if (buildingGalleryFullscreen) {
        Dialog(
            onDismissRequest = { buildingGalleryFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageNestedScrollConnection = PagerDefaults.pageNestedScrollConnection(
                        state = pagerState,
                        orientation = Orientation.Horizontal,
                    ),
                ) { page ->
                    val url = imageUrls.getOrNull(page)
                    if (url != null) {
                        AsyncImage(
                            model = url,
                            contentDescription = "건물 이미지",
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(buildingImageBg[page % buildingImageBg.size]),
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(ScanPangSpacing.md),
                ) {
                    Text(
                        text = "${currentBuildingPage + 1}/$buildingImageCount",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = ScanPangType.meta11Medium,
                        color = Color.White,
                    )
                }
                IconButton(
                    onClick = { buildingGalleryFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(ScanPangSpacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "닫기",
                        tint = Color.White,
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Description card (like hufs-cdp)
        val descText = arOverlay?.description?.ifEmpty { null }
            ?: arOverlay?.let {
                listOfNotNull(it.name.ifEmpty { null }, it.category.ifEmpty { null }).joinToString(" · ")
            }
        if (descText != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = ScanPangColors.Primary,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = descText,
                    style = ScanPangType.caption12,
                    color = ScanPangColors.OnSurfaceStrong,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val floorRangeText = arOverlay.floor_info
            .mapNotNull { it.floor.ifBlank { null } }
            .takeIf { it.isNotEmpty() }
            ?.let { labels -> "${labels.first()}~${labels.last()} (${labels.size}개층)" }
        val hoursText = listOfNotNull(
            arOverlay.open_hours.ifEmpty { null },
            arOverlay.closed_days.ifEmpty { null }?.let { "휴무 $it" },
        ).joinToString(" · ")
        val gridItems = listOfNotNull(
            hoursText.ifEmpty { null }?.let { Triple(Icons.Rounded.AccessTime, it, false) },
            floorRangeText?.let { Triple(Icons.Rounded.Stairs, it, false) },
            arOverlay.address.ifEmpty { null }?.let { Triple(Icons.Rounded.Place, it, false) },
            arOverlay.phone.ifEmpty { null }?.let { Triple(Icons.Rounded.LocalPhone, it, false) },
            arOverlay.parking_info.ifEmpty { null }?.let { Triple(Icons.Rounded.LocalParking, it, false) },
            arOverlay.admission_fee.ifEmpty { null }?.let { Triple(Icons.Rounded.ConfirmationNumber, it, false) },
            arOverlay.halal_info.ifEmpty { null }?.let { Triple(Icons.Rounded.Restaurant, it, false) },
        )
        if (gridItems.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                gridItems.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        row.forEach { (icon, label, isLink) ->
                            ArPoiInfoChip(
                                icon = icon,
                                text = label,
                                modifier = Modifier.weight(1f),
                                textColor = if (isLink) ScanPangColors.Primary else ScanPangColors.OnSurfaceStrong,
                                iconTint = if (isLink) ScanPangColors.Primary else ScanPangColors.OnSurfaceMuted,
                                background = if (label.contains("할랄")) DetailHalalChipBg else DetailChipBg,
                                strongText = label.contains("할랄"),
                            )
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
                // Homepage — full width chip
                arOverlay.homepage.ifEmpty { null }?.let {
                    ArPoiInfoChip(
                        icon = Icons.Rounded.Language,
                        text = "홈페이지",
                        modifier = Modifier.fillMaxWidth(),
                        textColor = ScanPangColors.Primary,
                        iconTint = ScanPangColors.Primary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(ScanPangSpacing.md))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(118.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageNestedScrollConnection = PagerDefaults.pageNestedScrollConnection(
                    state = pagerState,
                    orientation = Orientation.Horizontal,
                ),
            ) { page ->
                val url = imageUrls.getOrNull(page)
                if (url != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = "건물 이미지",
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(buildingImageBg[page % buildingImageBg.size]),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            ) {
                Text(
                    text = "${currentBuildingPage + 1}/$buildingImageCount",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = ScanPangType.meta11Medium,
                    color = Color.White,
                )
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { buildingGalleryFullscreen = true },
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.35f),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInFull,
                        contentDescription = "전체 보기",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(buildingImageCount) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == currentBuildingPage) 6.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == currentBuildingPage) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.45f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArPoiInfoChip(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = ScanPangColors.OnSurfaceStrong,
    iconTint: Color = ScanPangColors.OnSurfaceMuted,
    background: Color = DetailChipBg,
    strongText: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (strongText) DetailHalalChipFg else iconTint,
            )
            Text(
                text = text,
                style = if (strongText) ScanPangType.caption12Medium else ScanPangType.caption12Medium,
                color = if (strongText) DetailHalalChipFg else textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArPoiEmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = ScanPangColors.OnSurfaceMuted,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = message,
                style = ScanPangType.caption12Medium,
                color = ScanPangColors.OnSurfaceMuted,
            )
        }
    }
}

@Composable
private fun ArPoiFloorsTabBody(
    floors: List<ArFloorSectionUi>,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onStoreClick: (String) -> Unit,
) {
    if (floors.isEmpty()) {
        ArPoiEmptyState(message = "층별 매장 정보가 아직 준비되지 않았어요.")
        return
    }
    floors.forEach { floor ->
        val isOpen = floor.label in expanded
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            shadowElevation = 1.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggle(floor.label) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = floor.label,
                        style = ScanPangType.title14,
                        color = ScanPangColors.OnSurfaceStrong,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${floor.storeCount}개",
                        style = ScanPangType.caption12Medium,
                        color = ScanPangColors.OnSurfaceMuted,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = ScanPangShapes.badge6,
                        color = ScanPangColors.PrimarySoft,
                    ) {
                        Text(
                            text = floor.categoryLabel,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = ScanPangType.meta11Medium,
                            color = ScanPangColors.Primary,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (isOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = if (isOpen) ScanPangColors.Primary else ScanPangColors.OnSurfaceStrong,
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (isOpen && floor.stores.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 12.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        floor.stores.forEach { store ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onStoreClick(store.name) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (store.isHalal) DetailHalalChipFg
                                            else ScanPangColors.OnSurfacePlaceholder,
                                        ),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = store.name,
                                    style = ScanPangType.body15Medium,
                                    color = ScanPangColors.OnSurfaceStrong,
                                )
                                Text(
                                    text = "  |  ${store.category}",
                                    style = ScanPangType.caption12Medium,
                                    color = if (store.isHalal) DetailHalalChipFg else ScanPangColors.OnSurfaceMuted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArPoiAiGuideTabBody(docent: Docent? = null) {
    val speechText = docent?.speech?.ifEmpty { null }
    val suggestions = docent?.follow_up_suggestions?.filter { it.isNotBlank() } ?: emptyList()

    if (speechText == null && suggestions.isEmpty()) {
        // docent 없음 — AR 카메라 스캔 유도 안내
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = DetailAiSummaryBg,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SmartToy,
                    contentDescription = null,
                    tint = ScanPangColors.OnSurfaceMuted,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "카메라로 건물을 스캔하면\nAI 가이드가 활성화됩니다.",
                    style = ScanPangType.body14Regular,
                    color = ScanPangColors.OnSurfaceMuted,
                )
            }
        }
        return
    }

    if (speechText != null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = DetailAiSummaryBg,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SmartToy,
                    contentDescription = null,
                    tint = ScanPangColors.Primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = speechText,
                    style = ScanPangType.body14Regular,
                    color = ScanPangColors.OnSurfaceStrong,
                )
            }
        }
        Spacer(modifier = Modifier.height(ScanPangSpacing.md))
    }

    if (suggestions.isNotEmpty()) {
        Text(
            text = "추천 질문",
            style = ScanPangType.title14,
            color = ScanPangColors.OnSurfaceStrong,
        )
        Spacer(modifier = Modifier.height(8.dp))
        suggestions.forEachIndexed { index, suggestion ->
            val icon = when (index % 3) {
                0 -> Icons.Rounded.Restaurant
                1 -> Icons.Rounded.ShoppingBag
                else -> Icons.Rounded.CameraAlt
            }
            ArPoiAiPointCard(icon = icon, title = suggestion, subtitle = "")
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ArPoiAiPointCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DetailChipBg,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = ScanPangColors.PrimarySoft,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ScanPangColors.Primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = ScanPangType.title14,
                    color = ScanPangColors.OnSurfaceStrong,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = ScanPangType.caption12Medium,
                    color = ScanPangColors.OnSurfaceMuted,
                )
            }
        }
    }
}

/**
 * 사진의 "탐색-매장(X)" 작은 카드 — AR 마커 탭 시 floating.
 *
 * 백엔드 `/place/store` 응답(StoreResponse) 풀필드를 받아서 표시:
 * - category: Kakao raw category_name ("음식점 > 한식 > 국밥")
 * - isOpenNow: 영업중 판정 (b55f1e5 백엔드 계산). null=판정 불가
 * 응답 도착 전에는 storeName만 표시되고, 메타 라인은 비어 있음.
 */
@Composable
fun ArFloorStoreGuideOverlay(
    storeName: String,
    onDismiss: () -> Unit,
    onStartNavigation: () -> Unit,
    modifier: Modifier = Modifier,
    category: String = "",
    isOpenNow: Boolean? = null,
    storeResult: com.scanpang.app.data.remote.StoreResponse? = null,
    distanceLabel: String = "",
) {
    val displayCategory = (storeResult?.category?.ifBlank { null }) ?: category
    val displayOpenNow  = storeResult?.is_open_now ?: isOpenNow
    val imageUrl        = storeResult?.image_urls?.firstOrNull()?.takeIf { it.isNotBlank() }
    val intro           = (storeResult?.details?.get("intro") as? String)?.trim().orEmpty()
    val openHours       = storeResult?.open_hours?.trim().orEmpty()
    val addr            = storeResult?.addr?.trim().orEmpty()
    val phone           = storeResult?.phone?.trim().orEmpty()
    val floor           = storeResult?.floor?.trim().orEmpty()
    val homepage        = storeResult?.homepage?.trim().orEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScanPangColors.ArOverlayScrimDark)
            .clickable { onDismiss() },
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // 콘텐츠(사진/소개/메타/chip)가 길어지면 BottomCenter Surface 가 화면
                // 위로 넘쳐 콘텐츠 대부분이 가려진다. 화면 65% 까지만 차지하고 내부
                // verticalScroll 로 콘텐츠 스크롤.
                .fillMaxHeight(0.65f)
                .padding(ScanPangSpacing.lg)
                .clickable(enabled = false) { },
            shape = ScanPangShapes.radius16,
            color = ScanPangColors.Surface,
            shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(ScanPangSpacing.lg)
                    .verticalScroll(rememberScrollState()),
            ) {
                // 매장 메인 사진 1장 — image_urls[0]. 없으면 행 자체 생략.
                if (imageUrl != null) {
                    coil.compose.AsyncImage(
                        model = imageUrl,
                        contentDescription = storeName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                    Spacer(modifier = Modifier.height(ScanPangSpacing.md))
                }
                Text(
                    text = storeName,
                    style = ScanPangType.title16SemiBold,
                    color = ScanPangColors.OnSurfaceStrong,
                )
                Spacer(modifier = Modifier.height(ScanPangSpacing.sm))
                val openLabel = when (displayOpenNow) {
                    true  -> "영업 중"
                    false -> "영업 종료"
                    null  -> ""
                }
                val parts = listOfNotNull(
                    displayCategory.takeIf { it.isNotBlank() },
                    distanceLabel.takeIf { it.isNotBlank() },
                    openLabel.takeIf { it.isNotBlank() },
                )
                if (parts.isNotEmpty()) {
                    Text(
                        text = parts.joinToString(" · "),
                        style = ScanPangType.caption12Medium,
                        color = when (displayOpenNow) {
                            true  -> ScanPangColors.Success
                            false -> ScanPangColors.OnSurfaceMuted
                            null  -> ScanPangColors.OnSurfaceMuted
                        },
                    )
                }
                if (intro.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(ScanPangSpacing.md))
                    Text(
                        text = intro,
                        style = ScanPangType.body14Regular,
                        color = ScanPangColors.OnSurfaceStrong,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 풀필드 메타 — 영업시간 / 주소 / 전화 / 층 / 홈페이지. 비어있으면 행 자체 숨김.
                val metaRows: List<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, Boolean>> = listOfNotNull(
                    openHours.takeIf { it.isNotEmpty() }?.let { Triple(Icons.Rounded.AccessTime, it, false) },
                    addr.takeIf { it.isNotEmpty() }?.let { Triple(Icons.Rounded.Place, it, false) },
                    phone.takeIf { it.isNotEmpty() }?.let { Triple(Icons.Rounded.LocalPhone, it, true) },
                    floor.takeIf { it.isNotEmpty() }?.let { Triple(Icons.Rounded.Stairs, it, false) },
                    homepage.takeIf { it.isNotEmpty() }?.let { Triple(Icons.Rounded.Language, it, true) },
                )
                if (metaRows.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(ScanPangSpacing.md))
                    metaRows.forEach { (icon, text, isLink) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isLink) ScanPangColors.Primary else ScanPangColors.OnSurfaceMuted,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = text,
                                style = ScanPangType.body14Regular,
                                color = if (isLink) ScanPangColors.Primary else ScanPangColors.OnSurfaceStrong,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                Spacer(modifier = Modifier.height(ScanPangSpacing.md))
                Button(
                    onClick = onStartNavigation,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ScanPangColors.Primary,
                        contentColor = Color.White,
                    ),
                    shape = ScanPangShapes.radius12,
                ) {
                    Text("길안내", style = ScanPangType.body15Medium)
                }
            }
        }
    }
}
