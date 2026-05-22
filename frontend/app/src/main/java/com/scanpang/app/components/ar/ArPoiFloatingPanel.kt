package com.scanpang.app.components.ar

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.material.icons.automirrored.rounded.Accessible
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.MiscellaneousServices
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Wc
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.scanpang.app.data.remote.ArOverlay
import com.scanpang.app.data.remote.FloorInfo
import com.scanpang.app.screens.DetailCategoryTagDistanceRow
import com.scanpang.app.screens.resolveCategoryLabel
import com.scanpang.app.screens.DetailFacilityTagRow
import com.scanpang.app.screens.DetailImageFullscreenDialog
import com.scanpang.app.screens.DetailMenuPriceRow
import com.scanpang.app.screens.DetailScreenDivider
import com.scanpang.app.screens.DetailSectionHeader
import com.scanpang.app.screens.rememberDetailBookmark
import com.scanpang.app.util.OpenHoursUtils
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
    onSave: () -> Unit = {},
    arOverlay: ArOverlay? = null,
) {
    var expandedFloors by remember { mutableStateOf(setOf("B1")) }
    val floorData = remember(arOverlay) {
        if (arOverlay != null && arOverlay.floor_info.isNotEmpty()) {
            arOverlay.floor_info.map { fi ->
                ArFloorSectionUi(
                    label = fi.floor,
                    storeCount = fi.stores.size,
                    categoryLabel = "",
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
            color = Color.White,
            shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = ScanPangSpacing.md, vertical = ScanPangSpacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = poiName,
                        style = ScanPangType.title16SemiBold,
                        color = ScanPangColors.OnSurfaceStrong,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(
                        onClick = onSave,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkBorder,
                            contentDescription = "저장",
                            tint = ScanPangColors.OnSurfaceStrong,
                            modifier = Modifier.size(ScanPangDimens.icon20),
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "닫기",
                            tint = ScanPangColors.OnSurfaceStrong,
                            modifier = Modifier.size(ScanPangDimens.icon20),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                ArPoiStatusMetaRow(
                    category = arOverlay?.category ?: "",
                    openHours = arOverlay?.open_hours ?: "",
                    isEstimated = arOverlay?.is_estimated ?: false,
                    distanceM = arOverlay?.distance_m,
                )
                Spacer(modifier = Modifier.height(ScanPangSpacing.sm))
                ArPoiDetailSegmentedTabs(
                    active = activeDetailTab,
                    onSelect = onActiveDetailTabChange,
                )
                Spacer(modifier = Modifier.height(ScanPangSpacing.sm))
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

private fun formatArDistance(m: Double?): String = when {
    m == null -> ""
    m < 1000  -> "${m.toInt()}m"
    else      -> "%.1fkm".format(m / 1000.0)
}

@Composable
private fun ArPoiStatusMetaRow(
    category: String = "",
    openHours: String = "",
    isEstimated: Boolean = false,
    distanceM: Double? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
        val distanceText = formatArDistance(distanceM)
        if (distanceText.isNotBlank()) {
            Text(
                text = distanceText,
                style = ScanPangType.body14Regular,
                color = ScanPangColors.OnSurfaceMuted,
            )
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
        if (openHours.isNotBlank()) {
            Text(
                text = openHours,
                style = ScanPangType.body14Regular,
                color = ScanPangColors.OnSurfaceMuted,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (openHours.isNotBlank()) {
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

@Composable
private fun ArPoiBuildingTabBody(arOverlay: ArOverlay? = null) {
    val imageUrl = arOverlay?.image_url?.trim().orEmpty()
    val hasImages = imageUrl.isNotBlank()
    var buildingGalleryFullscreen by remember { mutableStateOf(false) }

    if (hasImages && buildingGalleryFullscreen) {
        Dialog(
            onDismissRequest = { buildingGalleryFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                coil.compose.AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
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

    Column(modifier = Modifier.fillMaxWidth()) {
        val descText = arOverlay?.let {
            listOfNotNull(it.name.ifEmpty { null }, it.category.ifEmpty { null }).joinToString(" · ")
        }.orEmpty()
        if (descText.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = ScanPangColors.Primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = descText,
                    style = ScanPangType.body14Regular,
                    color = ScanPangColors.OnSurfaceStrong,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // floor_info 의 floor 라벨에서 "B2~8F" 같은 건물 단위 층 범위 derive.
        // "B1","1F","2F",..,"8F" 중 음수(지하)는 B prefix, 양수는 F suffix.
        val floorRange = arOverlay?.floor_info?.let { fs ->
            val parsed = fs.mapNotNull { f ->
                val s = f.floor.trim()
                when {
                    s.startsWith("B") -> s.removePrefix("B").toIntOrNull()?.let { -it }
                    s.endsWith("F")   -> s.removeSuffix("F").toIntOrNull()
                    else              -> null
                }
            }
            if (parsed.isEmpty()) null
            else {
                val mn = parsed.min(); val mx = parsed.max()
                val lo = if (mn < 0) "B${-mn}" else "${mn}F"
                val hi = if (mx < 0) "B${-mx}" else "${mx}F"
                if (lo == hi) lo else "$lo~$hi"
            }
        }
        val gridItems = listOfNotNull(
            arOverlay?.open_hours?.ifEmpty { null }?.let { Triple(Icons.Rounded.AccessTime, it, false) },
            floorRange?.let { Triple(Icons.Rounded.Stairs, it, false) },
            arOverlay?.address?.ifEmpty { null }?.let { Triple(Icons.Rounded.Place, it, false) },
            arOverlay?.phone?.ifEmpty { null }?.let { Triple(Icons.Rounded.LocalPhone, it, true) },
            arOverlay?.parking_info?.ifEmpty { null }?.let { Triple(Icons.Rounded.LocalParking, it, false) },
            arOverlay?.admission_fee?.ifEmpty { null }?.let { Triple(Icons.Rounded.ConfirmationNumber, it, false) },
            arOverlay?.halal_info?.ifEmpty { null }?.let { Triple(Icons.Rounded.Restaurant, it, false) },
        )
        if (gridItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(ScanPangSpacing.md))
            gridItems.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (hasImages) {
            Spacer(modifier = Modifier.height(ScanPangSpacing.md))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                coil.compose.AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
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
private fun ArPoiFloorsTabBody(
    floors: List<ArFloorSectionUi>,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onStoreClick: (String) -> Unit,
) {
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
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val isGreenDot = store.category.contains("할랄", ignoreCase = true) ||
                                    store.category.contains("비건", ignoreCase = true) ||
                                    store.category.contains("vegan", ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isGreenDot) DetailHalalChipFg
                                            else ScanPangColors.OnSurfacePlaceholder,
                                        ),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = store.name,
                                    style = ScanPangType.body15Medium,
                                    color = ScanPangColors.OnSurfaceStrong,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val chipLabel = store.category
                                    .substringAfterLast(">").trim()
                                    .ifBlank { store.category }
                                if (chipLabel.isNotBlank()) {
                                    val isGreen = store.category.contains("할랄", ignoreCase = true) ||
                                        store.category.contains("비건", ignoreCase = true) ||
                                        store.category.contains("vegan", ignoreCase = true)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = ScanPangShapes.badge6,
                                        color = if (isGreen) DetailHalalChipBg else ScanPangColors.PrimarySoft,
                                    ) {
                                        Text(
                                            text = chipLabel,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            style = ScanPangType.meta11Medium,
                                            color = if (isGreen) DetailHalalChipFg else ScanPangColors.Primary,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = ScanPangColors.OnSurfacePlaceholder,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * AR 마커 탭 매장 상세 플로팅 카드 (361×352, 상단 Y=230).
 *
 * 백엔드 `/place/store` 응답(StoreResponse) 풀필드를 받아서 표시.
 * 응답 도착 전에는 storeName만 표시되고, 메타 라인은 비어 있음.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    val categoryKey = storeResult?.category_key ?: ""
    val heroPhotoAllowed = categoryKey !in setOf(
        "atm", "subway", "subway_station", "restroom", "public_restroom", "lockers", "locker",
    )
    val canFullscreen = heroPhotoAllowed
    val imageUrls = if (heroPhotoAllowed)
        (storeResult?.image_urls ?: emptyList()).filter { it.isNotBlank() }.take(6)
    else emptyList()
    val displayCategory = resolveCategoryLabel(
        categoryKey = categoryKey,
        rawCategory = storeResult?.category ?: category,
        veganLevel = (storeResult?.details?.get("vegan_level") as? String).orEmpty(),
    )
    val displayOpenNow = storeResult?.is_open_now ?: isOpenNow
    val intro = (storeResult?.details?.get("intro") as? String)?.trim().orEmpty()
    val openHours = storeResult?.open_hours?.trim().orEmpty()
    val lastOrder = (storeResult?.details?.get("last_order") as? String).orEmpty()
    val addr = storeResult?.addr?.trim().orEmpty()
    val phone = storeResult?.phone?.trim().orEmpty()
    val floor = storeResult?.floor?.trim().orEmpty()
    val homepage = storeResult?.homepage?.trim().orEmpty()
    val showVisitStatus = openHours.isNotBlank() && categoryKey in setOf(
        "restaurant", "halal_restaurant", "cafe",
        "shopping", "mall", "convenience", "convenience_store",
        "exchange", "bank", "hospital", "pharmacy",
        "tourist", "tourist_spot", "attraction",
    )

    val pagerState = rememberPagerState(pageCount = { imageUrls.size })
    var isFullscreen by remember { mutableStateOf(false) }

    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    val bookmark = rememberDetailBookmark(
        placeId = storeResult?.id ?: storeName,
        placeName = storeName,
        category = displayCategory,
        distanceLine = distanceLabel,
        tags = emptyList(),
        categoryKey = categoryKey,
    )

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
            color = Color.White,
            shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (storeResult == null) {
                    com.scanpang.app.screens.PlaceLoadingScreen(
                        modifier = Modifier.fillMaxSize(),
                    )
                    return@Surface
                }
                // ① 사진 캐러셀 (최대 6장, 90dp 고정)
                if (imageUrls.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ScanPangDimens.detailArCarouselSmallHeight),
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            pageNestedScrollConnection = PagerDefaults.pageNestedScrollConnection(
                                state = pagerState,
                                orientation = Orientation.Horizontal,
                            ),
                        ) { page ->
                            coil.compose.AsyncImage(
                                model = imageUrls[page],
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                        }
                        if (canFullscreen) {
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable { isFullscreen = true },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.35f),
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        imageVector = Icons.Rounded.OpenInFull,
                                        contentDescription = "전체 보기",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                        if (imageUrls.size > 1) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                repeat(imageUrls.size) { i ->
                                    Box(
                                        modifier = Modifier
                                            .size(if (i == pagerState.currentPage) 6.dp else 4.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (i == pagerState.currentPage) Color.White
                                                else Color.White.copy(alpha = 0.45f),
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
                // 스크롤 가능 콘텐츠 영역
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = ScanPangSpacing.md, vertical = ScanPangSpacing.sm)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                ) {
                    // ② 매장명 + 북마크 + 닫기
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = storeName,
                            style = ScanPangType.title16SemiBold,
                            color = ScanPangColors.OnSurfaceStrong,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = bookmark.onToggle,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = if (bookmark.bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                contentDescription = if (bookmark.bookmarked) "저장됨" else "저장",
                                tint = if (bookmark.bookmarked) ScanPangColors.Primary else ScanPangColors.OnSurfaceStrong,
                                modifier = Modifier.size(ScanPangDimens.icon20),
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "닫기",
                                tint = ScanPangColors.OnSurfaceStrong,
                                modifier = Modifier.size(ScanPangDimens.icon20),
                            )
                        }
                    }
                    // ③ 카테고리 뱃지 + 거리 + 영업 여부
                    if (displayCategory.isNotBlank()) {
                        DetailCategoryTagDistanceRow(
                            categoryLabel = displayCategory,
                            distanceText = distanceLabel,
                            isOpen = displayOpenNow,
                        )
                    }
                    // ③-1 할랄 신뢰칩 (restaurant / halal_restaurant)
                    if (categoryKey in setOf("restaurant", "halal_restaurant")) {
                        val halalType = (storeResult?.details?.get("halal_type") as? String).orEmpty()
                        val halalTags = buildList {
                            if (storeResult?.details?.get("muslim_cooks_available") as? Boolean == true)
                                add(Pair("무슬림 조리사", Icons.Rounded.Verified))
                            if (storeResult?.details?.get("no_alcohol_sales") as? Boolean == true)
                                add(Pair("주류 미판매", Icons.Rounded.Star))
                        }
                        if (halalType.isNotBlank() || halalTags.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (halalType.isNotBlank()) StoreHalalCategoryChip(label = halalType)
                                halalTags.take(2).forEach { (tag, icon) ->
                                    StoreHalalTrustChip(text = tag, icon = icon)
                                }
                            }
                        }
                    }
                    // ④ 소개 — 파란 info 아이콘 카드 (Figma)
                    if (intro.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ScanPangShapes.radius12,
                            color = ScanPangColors.DetailVisitNeutralSurface,
                        ) {
                            Row(
                                modifier = Modifier.padding(ScanPangSpacing.md),
                                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = ScanPangColors.Primary,
                                    modifier = Modifier.size(ScanPangDimens.icon18),
                                )
                                Text(
                                    text = intro,
                                    style = ScanPangType.body14Regular,
                                    color = ScanPangColors.OnSurfaceStrong,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    // ⑤ 오늘 영업 상태 — 헤더 없는 compact 한 줄 (Figma)
                    if (showVisitStatus) {
                        val localIsOpen = remember(openHours) { OpenHoursUtils.isOpenNow(openHours) }
                        val effectiveIsOpen = localIsOpen ?: (displayOpenNow ?: false)
                        val statusColor = if (effectiveIsOpen) ScanPangColors.StatusOpen else ScanPangColors.Error
                        val todayHours = remember(openHours) { OpenHoursUtils.todayHoursText(openHours) }
                        DetailScreenDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor),
                            )
                            Icon(
                                imageVector = Icons.Rounded.AccessTime,
                                contentDescription = null,
                                tint = ScanPangColors.OnSurfaceMuted,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = if (todayHours.isNotBlank()) "오늘 $todayHours"
                                    else if (effectiveIsOpen) "영업 중" else "영업 종료",
                                style = ScanPangType.title14,
                                color = ScanPangColors.OnSurfaceStrong,
                                modifier = Modifier.weight(1f),
                            )
                            if (lastOrder.isNotBlank()) {
                                Text(
                                    text = "라스트오더 $lastOrder",
                                    style = ScanPangType.caption12Medium,
                                    color = ScanPangColors.OnSurfaceMuted,
                                )
                            }
                        }
                    }
                    // ⑥ 상세 정보 — 헤더 없이 아이콘 + 값 (detail screen과 동일 항목)
                    val facilityList = buildList {
                        if (storeResult?.details?.get("has_disabled") as? Boolean == true) add("장애인 화장실")
                        if (storeResult?.details?.get("has_child") as? Boolean == true) add("유아 화장실")
                        if (storeResult?.details?.get("has_diaper_table") as? Boolean == true) add("기저귀 교환대")
                        if (storeResult?.details?.get("wudu") as? Boolean == true) add("우두 시설")
                        if (storeResult?.details?.get("gender_separation") as? Boolean == true) add("남녀 분리")
                        if (storeResult?.details?.get("prayer_mat") as? Boolean == true) add("기도 매트")
                        if (storeResult?.details?.get("quran_available") as? Boolean == true) add("꾸란 비치")
                    }
                    val safetyList = buildList {
                        if (storeResult?.details?.get("has_cctv") as? Boolean == true) add("CCTV")
                        if (storeResult?.details?.get("has_emergency_bell") as? Boolean == true) add("비상벨")
                    }
                    val conveniences = (storeResult?.details?.get("conveniences") as? List<*>)
                        ?.filterIsInstance<String>() ?: emptyList()
                    val convenienceServices = conveniences.filter { !it.contains("주차") }.joinToString(", ")
                    val departments = (storeResult?.details?.get("departments") as? String)?.trim().orEmpty()
                    // 화장실 칸 수 — key 오타("toilt") 양쪽 모두 시도
                    val toiletMaleRaw = storeResult?.details?.get("male_toilet_cnt")
                        ?: storeResult?.details?.get("male_toilt_cnt")
                    val toiletFemaleRaw = storeResult?.details?.get("female_toilet_cnt")
                        ?: storeResult?.details?.get("female_toilt_cnt")
                    fun Any?.toToiletInt() = when (this) {
                        is Double -> toInt(); is Int -> this; is String -> toIntOrNull(); else -> null
                    }
                    val toiletStr = buildList {
                        val m = toiletMaleRaw.toToiletInt()
                        val f = toiletFemaleRaw.toToiletInt()
                        if ((m ?: 0) > 0) add("남성 ${m}칸")
                        if ((f ?: 0) > 0) add("여성 ${f}칸")
                    }.joinToString(", ")
                    val isRestroom = categoryKey in setOf("restroom", "public_restroom")
                    val infoLines = listOfNotNull(
                        addr.takeIf { it.isNotBlank() }?.let { Pair(Icons.Rounded.Place, it) },
                        phone.takeIf { it.isNotBlank() }?.let { Pair(Icons.Rounded.LocalPhone, it) },
                        floor.takeIf { it.isNotBlank() && !isRestroom }?.let { Pair(Icons.Rounded.Stairs, it) },
                        homepage.takeIf { it.isNotBlank() }?.let { Pair(Icons.Rounded.Language, it) },
                        toiletStr.takeIf { it.isNotBlank() }?.let { Pair(Icons.Rounded.Wc, it) },
                        facilityList.joinToString(", ").takeIf { it.isNotBlank() }
                            ?.let { Pair(Icons.AutoMirrored.Rounded.Accessible, it) },
                        safetyList.joinToString(", ").takeIf { it.isNotBlank() }
                            ?.let { Pair(Icons.Rounded.Security, it) },
                        convenienceServices.takeIf { it.isNotBlank() }
                            ?.let { Pair(Icons.Rounded.MiscellaneousServices, it) },
                        departments.takeIf { it.isNotBlank() }
                            ?.let { Pair(Icons.Rounded.Healing, it) },
                    )
                    if (infoLines.isNotEmpty()) {
                        DetailScreenDivider()
                        infoLines.forEach { (icon, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(ScanPangDimens.icon16),
                                    tint = ScanPangColors.OnSurfaceMuted,
                                )
                                Text(
                                    text = value,
                                    style = ScanPangType.caption12Medium,
                                    color = ScanPangColors.OnSurfaceMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    // ⑦ 카테고리별 부가 정보
                    when (categoryKey) {
                        "restaurant", "halal_restaurant", "cafe", "vegan_restaurant", "vegan_cafe" -> {
                            // Naver 크롤링: details.menu = [{name, price}]
                            // 할랄 식당: details.menu_examples = [{name_ko, name_en, price_krw}]
                            val menuPairs = remember(storeResult) {
                                val fromMenu = (storeResult?.details?.get("menu") as? List<*>)
                                    ?.mapNotNull { entry ->
                                        val m = entry as? Map<*, *> ?: return@mapNotNull null
                                        val name = (m["name"] as? String)?.trim().orEmpty()
                                        val price = (m["price"] as? String)?.trim().orEmpty()
                                        if (name.isBlank()) null else Pair(name, price)
                                    }
                                if (!fromMenu.isNullOrEmpty()) return@remember fromMenu
                                (storeResult?.details?.get("menu_examples") as? List<*>)
                                    ?.mapNotNull { entry ->
                                        val m = entry as? Map<*, *> ?: return@mapNotNull null
                                        val name = ((m["name_ko"] as? String)
                                            ?: (m["name_en"] as? String))?.trim().orEmpty()
                                        val priceRaw = m["price_krw"]
                                        val price = when (priceRaw) {
                                            is Number -> "%,d원".format(priceRaw.toInt())
                                            is String -> priceRaw.trim()
                                            else -> ""
                                        }
                                        if (name.isBlank()) null else Pair(name, price)
                                    } ?: emptyList()
                            }
                            if (menuPairs.isNotEmpty()) {
                                DetailScreenDivider()
                                DetailSectionHeader(title = "대표 메뉴")
                                Spacer(modifier = Modifier.height(ScanPangSpacing.xs))
                                Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs)) {
                                    menuPairs.take(3).forEach { (name, price) ->
                                        DetailMenuPriceRow(name = name, price = price)
                                    }
                                }
                            }
                        }
                        "exchange", "atm", "bank" -> {
                            val targetOrder = listOf("USD", "JPY", "EUR", "CNY")
                            val exchangeRows = remember(storeResult) {
                                (storeResult?.details?.get("rates_today") as? List<*>)
                                    ?.mapNotNull { entry ->
                                        val m = entry as? Map<*, *> ?: return@mapNotNull null
                                        val ccy = (m["ccy"] as? String)?.trim().orEmpty()
                                        if (ccy !in targetOrder) return@mapNotNull null
                                        val flag = (m["flag"] as? String).orEmpty()
                                        val baseRate = (m["base_rate"] as? String)?.trim().orEmpty()
                                        if (baseRate.isBlank()) return@mapNotNull null
                                        val rateText = baseRate.replace(",", "").toDoubleOrNull()
                                            ?.let { "%,d원".format(it.toInt()) } ?: "${baseRate}원"
                                        Triple(ccy, flag, rateText)
                                    }
                                    ?.sortedBy { triple ->
                                        val idx = targetOrder.indexOf(triple.first)
                                        if (idx >= 0) idx else Int.MAX_VALUE
                                    } ?: emptyList()
                            }
                            if (exchangeRows.isNotEmpty()) {
                                DetailScreenDivider()
                                DetailSectionHeader(title = "오늘의 환율")
                                Spacer(modifier = Modifier.height(ScanPangSpacing.xs))
                                Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs)) {
                                    exchangeRows.forEach { (ccy, flag, rateText) ->
                                        DetailMenuPriceRow(name = "$flag $ccy → KRW", price = rateText)
                                    }
                                }
                            }
                        }
                        "subway" -> {
                            val tags = (storeResult?.details?.get("facilities") as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList()
                            if (tags.isNotEmpty()) DetailFacilityTagRow(tags = tags)
                        }
                    }
                    Spacer(modifier = Modifier.height(ScanPangSpacing.sm))
                }
            }
        }
        // 풀스크린 사진 다이얼로그
        if (isFullscreen && imageUrls.isNotEmpty()) {
            DetailImageFullscreenDialog(
                gallery = imageUrls,
                pagerState = pagerState,
                onDismiss = { isFullscreen = false },
            )
        }
    }
}

@Composable
private fun StoreHalalCategoryChip(label: String) {
    val (bg, fg) = when (label) {
        "HALAL MEAT"  -> ScanPangColors.HalalMeatBadgeBackground  to ScanPangColors.HalalMeatBadgeText
        "SEAFOOD"     -> ScanPangColors.SeafoodBadgeBackground     to ScanPangColors.Primary
        "VEGGIE"      -> ScanPangColors.VeggieBadgeBackground      to ScanPangColors.VeggieBadgeText
        "SALAM SEOUL" -> ScanPangColors.SalamSeoulBadgeBackground  to ScanPangColors.SalamSeoulBadgeText
        else          -> ScanPangColors.HalalMeatBadgeBackground   to ScanPangColors.HalalMeatBadgeText
    }
    Surface(
        shape = ScanPangShapes.badge6,
        color = bg,
        border = BorderStroke(ScanPangDimens.borderHairline, ScanPangColors.OutlineSubtle),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = ScanPangDimens.trustChipHorizontal,
                vertical = ScanPangDimens.trustChipVertical,
            ),
            style = ScanPangType.badge9SemiBold,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StoreHalalTrustChip(text: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .clip(ScanPangShapes.badge6)
            .background(ScanPangColors.TrustPillBackground)
            .padding(
                horizontal = ScanPangDimens.trustChipHorizontal,
                vertical = ScanPangDimens.trustChipVertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangDimens.trustIconGap),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(ScanPangDimens.icon10),
            tint = ScanPangColors.TrustPillText,
        )
        Text(
            text = text,
            style = ScanPangType.badge9SemiBold,
            color = ScanPangColors.TrustPillText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
