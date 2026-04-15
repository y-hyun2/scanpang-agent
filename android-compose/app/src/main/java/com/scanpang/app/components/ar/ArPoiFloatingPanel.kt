package com.scanpang.app.components.ar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalPhone
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SmartToy
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
const val ArPoiTabAi = "ai"

private val DetailTabTrackGray = Color(0xFFEBEBEB)
private val DetailChipBg = Color(0xFFF3F4F6)
private val DetailAiSummaryBg = Color(0xFFE8F1FF)
private val DetailAiTipBg = Color(0xFFFFF4E5)
private val DetailAiTipFg = Color(0xFFB45309)
private val DetailHalalChipBg = Color(0xFFE8F5E9)
private val DetailHalalChipFg = Color(0xFF2E7D32)

private data class ArFloorStoreLine(val name: String, val category: String, val isHalal: Boolean)

private data class ArFloorSectionUi(
    val label: String,
    val storeCount: Int,
    val categoryLabel: String,
    val stores: List<ArFloorStoreLine>,
)

private fun noonSquareFloorSections(): List<ArFloorSectionUi> = listOf(
    ArFloorSectionUi("B2", 6, "식음료", emptyList()),
    ArFloorSectionUi(
        "B1",
        8,
        "식음료",
        listOf(
            ArFloorStoreLine("무궁화식당", "한식", false),
            ArFloorStoreLine("알리바바 케밥", "할랄", true),
            ArFloorStoreLine("올리브영", "뷰티", false),
        ),
    ),
    ArFloorSectionUi("1F", 12, "패션·잡화", emptyList()),
    ArFloorSectionUi("2F", 10, "뷰티·라이프", emptyList()),
    ArFloorSectionUi("3F", 9, "패션", emptyList()),
    ArFloorSectionUi("4F", 7, "잡화", emptyList()),
    ArFloorSectionUi("5F", 6, "F&B", emptyList()),
    ArFloorSectionUi("6F", 5, "문화", emptyList()),
    ArFloorSectionUi("7F", 4, "전망", emptyList()),
    ArFloorSectionUi("8F", 3, "루프탑", emptyList()),
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
    docent: Docent? = null,
) {
    var expandedFloors by remember { mutableStateOf(setOf("B1")) }
    val floorData = remember(arOverlay) {
        if (arOverlay != null && arOverlay.floor_info.isNotEmpty()) {
            arOverlay.floor_info.map { fi ->
                ArFloorSectionUi(
                    label = fi.floor,
                    storeCount = fi.stores.size,
                    categoryLabel = "",
                    stores = fi.stores.map { ArFloorStoreLine(it, "", false) },
                )
            }
        } else {
            noonSquareFloorSections()
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
                ArPoiStatusMetaRow()
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
                        ArPoiTabAi -> ArPoiAiGuideTabBody(docent = docent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ArPoiStatusMetaRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
    ) {
        Surface(
            shape = ScanPangShapes.badge6,
            color = ScanPangColors.PrimarySoft,
        ) {
            Text(
                text = "쇼핑몰",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = ScanPangType.caption12Medium,
                color = ScanPangColors.Primary,
            )
        }
        Text(
            text = "15m",
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

@Composable
private fun ArPoiDetailSegmentedTabs(
    active: String,
    onSelect: (String) -> Unit,
) {
    val tabs = listOf(
        ArPoiTabBuilding to "건물 정보",
        ArPoiTabFloors to "층별 정보",
        ArPoiTabAi to "AI 가이드",
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE8E8E8)),
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) {
            Text(
                text = "1/4",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = ScanPangType.meta11Medium,
                color = Color.White,
            )
        }
        IconButton(
            onClick = { },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.OpenInFull,
                contentDescription = "전체 보기",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .size(if (i == 0) 6.dp else 5.dp)
                        .clip(CircleShape)
                        .background(if (i == 0) Color.White else Color.White.copy(alpha = 0.45f)),
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(ScanPangSpacing.sm))
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
            text = arOverlay?.let { "${it.name} · ${it.category}" }
                ?: "명동 중심 대형 복합 쇼핑몰. 지하2층~지상8층, 패션·뷰티·F&B 입점.",
            style = ScanPangType.body14Regular,
            color = ScanPangColors.OnSurfaceStrong,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(ScanPangSpacing.md))
    val gridItems = listOf(
        Triple(Icons.Rounded.AccessTime, arOverlay?.open_hours?.ifEmpty { null } ?: "10:00–22:00", false),
        Triple(Icons.Rounded.Stairs, arOverlay?.floor_info?.let { floors ->
            if (floors.isNotEmpty()) "${floors.first().floor}~${floors.last().floor}" else "B2~8F"
        } ?: "B2~8F", false),
        Triple(Icons.Rounded.Place, "명동 중앙로 26", false),
        Triple(Icons.Rounded.LocalPhone, "02-778-1234", false),
        Triple(Icons.Rounded.LocalParking, arOverlay?.parking_info?.ifEmpty { null } ?: "주차 가능", false),
        Triple(Icons.Rounded.ConfirmationNumber, arOverlay?.admission_fee?.ifEmpty { null } ?: "무료 입장", false),
        Triple(Icons.Rounded.Language, if (arOverlay?.homepage?.isNotEmpty() == true) "홈페이지" else "홈페이지", true),
        Triple(Icons.Rounded.Restaurant, arOverlay?.halal_info?.ifEmpty { null } ?: "할랄 식당 有", false),
    )
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
        ?: "명동의 랜드마크 쇼핑몰이에요. 혼자 여행하기 좋고, B1층에 할랄 인증 식당이 있어 식사도 편리합니다."
    val suggestions = docent?.follow_up_suggestions ?: listOf(
        "B1 할랄 식당 정보",
        "1F 외국인 할인 안내",
        "8F 루프탑 전망",
    )

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
            ArPoiAiPointCard(
                icon = icon,
                title = suggestion,
                subtitle = "",
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
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

@Composable
fun ArFloorStoreGuideOverlay(
    storeName: String,
    onDismiss: () -> Unit,
    onStartNavigation: () -> Unit,
    modifier: Modifier = Modifier,
    storeCategory: String = "",
    storeAddress: String = "",
) {
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
                .padding(ScanPangSpacing.lg),
            shape = ScanPangShapes.radius16,
            color = ScanPangColors.Surface,
            shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
        ) {
            Column(modifier = Modifier.padding(ScanPangSpacing.lg)) {
                Text(
                    text = storeName,
                    style = ScanPangType.title16SemiBold,
                    color = ScanPangColors.OnSurfaceStrong,
                )
                Spacer(modifier = Modifier.height(ScanPangSpacing.sm))
                Text(
                    text = storeCategory.ifEmpty { "HALAL MEAT · 한식 · 영업 중" },
                    style = ScanPangType.caption12Medium,
                    color = ScanPangColors.OnSurfaceMuted,
                )
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
