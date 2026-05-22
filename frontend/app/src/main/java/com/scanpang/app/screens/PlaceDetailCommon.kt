@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.scanpang.app.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.runtime.LaunchedEffect
import com.scanpang.app.data.Place
import com.scanpang.app.data.RecentlyViewedEntry
import com.scanpang.app.data.RecentlyViewedStore
import com.scanpang.app.data.SavedPlaceEntry
import com.scanpang.app.data.SavedPlaceNavTarget
import com.scanpang.app.data.SavedPlacesStore
import com.scanpang.app.ui.ScanPangFigmaAssets
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType
import com.scanpang.app.util.OpenHoursUtils

/** Coil용 더미 갤러리 — API 연동 시 동일 시그니처로 교체 */
fun defaultPlaceDetailGallery(): List<String> = ScanPangFigmaAssets.RestaurantDetailGallery

fun Place.detailVisitCardsFromPlace(): List<DetailVisitCardUi> {
    val statusTitle = if (isOpen) "지금 방문 가능" else "운영 종료"
    val statusTone = if (isOpen) DetailVisitCardTone.Open else DetailVisitCardTone.Closed
    val hint = if (description.length > 56) description.take(56) + "…" else description
    return listOf(
        DetailVisitCardUi(statusTitle, openHours, statusTone),
        DetailVisitCardUi("안내", hint.ifBlank { "상세 정보는 매장에 문의해 주세요." }, DetailVisitCardTone.Neutral),
    )
}

enum class DetailVisitCardTone {
    Open,
    Closed,
    Neutral,
}

data class DetailVisitCardUi(
    val title: String,
    val subtitle: String,
    val tone: DetailVisitCardTone,
)

data class DetailBookmarkController(
    val bookmarked: Boolean,
    val onToggle: () -> Unit,
)

fun Context.openPhoneDialer(rawPhone: String) {
    val digits = rawPhone.filter { it.isDigit() || it == '+' }
    if (digits.isEmpty()) return
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))
    startActivity(intent)
}

@Composable
fun rememberDetailBookmark(
    placeId: String,
    placeName: String,
    category: String,
    distanceLine: String,
    tags: List<String>,
    categoryKey: String,
    lat: Double = 0.0,
    lng: Double = 0.0,
): DetailBookmarkController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val store = remember { SavedPlacesStore(context) }
    val recentlyViewedStore = remember { RecentlyViewedStore(context) }
    val target = remember(categoryKey) { SavedPlaceNavTarget.fromCategoryKey(categoryKey) }
    var bookmarked by remember(placeId) { mutableStateOf(store.isSaved(placeId)) }

    // store_details.id 패턴은 항상 '{place_id}__{store_name}' 또는 outdoor sentinel
    // ('restroom__...', '__outdoor__...') 형태로 '__' 를 포함. backend storeResult 가
    // 아직 안 도착해 frontend 가 매장명 fallback (예: 'BHC치킨 용인모현점') 으로 부르면
    // 그 invalid id 가 RecentlyViewed/Saved 에 들어가 다음 진입 시 /place/detail 404.
    val isValidId = "__" in placeId
    LaunchedEffect(placeId) {
        if (!isValidId) return@LaunchedEffect
        recentlyViewedStore.record(
            RecentlyViewedEntry(
                id = placeId,
                name = placeName,
                category = category,
                target = target,
                lat = lat,
                lng = lng,
            ),
        )
    }

    DisposableEffect(lifecycleOwner, placeId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                bookmarked = store.isSaved(placeId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val onToggle: () -> Unit = onToggle@{
        if (!isValidId) {
            Toast.makeText(context, "매장 정보를 불러오는 중입니다", Toast.LENGTH_SHORT).show()
            return@onToggle
        }
        if (bookmarked) {
            store.remove(placeId)
            bookmarked = false
            Toast.makeText(context, "저장이 해제되었습니다", Toast.LENGTH_SHORT).show()
        } else {
            store.save(
                SavedPlaceEntry(
                    id = placeId,
                    name = placeName,
                    category = category,
                    tags = tags,
                    target = target,
                    lat = lat,
                    lng = lng,
                ),
            )
            bookmarked = true
            Toast.makeText(context, "저장되었습니다", Toast.LENGTH_SHORT).show()
        }
    }

    return DetailBookmarkController(bookmarked, onToggle)
}

@Composable
fun DetailImageFullscreenDialog(
    gallery: List<Any>,
    pagerState: PagerState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
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
            ) { page ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(gallery[page])
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(ScanPangSpacing.md)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "닫기",
                    modifier = Modifier.size(24.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
fun DetailHeroPhotoPager(
    gallery: List<Any>,
    pagerState: PagerState,
    onBack: () -> Unit,
    onFullscreenClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ScanPangDimens.detailPhotoHeroHeight),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(gallery[page])
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 20.dp, top = 12.dp)
                .size(ScanPangDimens.arCircleBtn36)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.25f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "뒤로",
                modifier = Modifier.size(24.dp),
                tint = Color.White,
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(gallery.size) { index ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (index == pagerState.currentPage) 1f else 0.38f)),
                )
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = ScanPangSpacing.lg, bottom = ScanPangSpacing.lg),
            shape = ScanPangShapes.badge6,
            color = Color.Black.copy(alpha = 0.45f),
        ) {
            Text(
                text = "${pagerState.currentPage + 1}/${gallery.size}",
                modifier = Modifier.padding(horizontal = ScanPangSpacing.sm, vertical = 3.dp),
                style = ScanPangType.detailImageCount9,
                color = Color.White,
            )
        }
    }
}

@Composable
fun DetailScrollTopBackRow(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(ScanPangSpacing.sm),
        horizontalArrangement = Arrangement.Start,
    ) {
        IconButton(onClick = onBack) {
            Surface(
                shape = CircleShape,
                color = ScanPangColors.ArOverlayWhite93,
                shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "뒤로",
                    modifier = Modifier.padding(ScanPangSpacing.sm),
                    tint = ScanPangColors.OnSurfaceStrong,
                )
            }
        }
    }
}

@Composable
fun DetailTitleBookmarkRow(
    title: String,
    bookmarked: Boolean,
    onBookmarkClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = ScanPangType.detailRestaurantTitle24,
            color = ScanPangColors.OnSurfaceStrong,
            modifier = Modifier.weight(1f).padding(end = ScanPangSpacing.sm),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
        ) {
            trailingContent?.invoke()
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ScanPangColors.Background)
                    .clickable(onClick = onBookmarkClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = if (bookmarked) "저장됨" else "저장",
                    modifier = Modifier.size(20.dp),
                    tint = if (bookmarked) ScanPangColors.Primary else ScanPangColors.OnSurfaceMuted,
                )
            }
        }
    }
}

// ── 카테고리 표시 공유 로직 ────────────────────────────────────────────────────

val PLACE_CATEGORY_KO = mapOf(
    "cafe"              to "카페",
    "restaurant"        to "식당",
    "shopping"          to "쇼핑",
    "convenience_store" to "편의점",
    "pharmacy"          to "약국",
    "hospital"          to "병원",
    "bank"              to "은행",
    "atm"               to "ATM",
    "exchange"          to "환전소",
    "subway"            to "지하철역",
    "subway_station"    to "지하철역",
    "restroom"          to "화장실",
    "public_restroom"   to "화장실",
    "locker"            to "물품보관함",
    "lockers"           to "물품보관함",
    "prayer_room"       to "기도실",
    "accommodation"     to "호텔",
    "cultural"          to "문화시설",
    "tourist"           to "관광지",
    "tourist_spot"      to "관광지",
    "halal_restaurant"  to "할랄 식당",
    "vegan_restaurant"  to "비건 식당",
    "vegan_cafe"        to "비건 카페",
)

val PLACE_USE_RAW_CATEGORY = setOf(
    "restaurant", "shopping", "hospital", "cultural", "accommodation",
)

/**
 * categoryKey + rawCategory + (옵션) veganLevel → 화면 표시 레이블.
 * PlaceDetailScreen / SearchDefaultScreen / ArPoiFloatingPanel 에서 공통 사용.
 */
fun resolveCategoryLabel(
    categoryKey: String,
    rawCategory: String,
    veganLevel: String = "",
): String = when {
    categoryKey == "vegan_restaurant" ->
        if (veganLevel == "채식가능") "채식가능" else "비건 식당"
    categoryKey in PLACE_USE_RAW_CATEGORY ->
        rawCategory.substringAfterLast(">").trim().ifBlank { PLACE_CATEGORY_KO[categoryKey] ?: categoryKey }
    else ->
        PLACE_CATEGORY_KO[categoryKey] ?: rawCategory.substringAfterLast(">").trim().ifBlank { "—" }
}

@Composable
fun DetailCategoryDistanceLine(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = ScanPangType.detailMetaSubtitle13,
        color = ScanPangColors.OnSurfaceMuted,
    )
}

@Composable
fun DetailCategoryTagDistanceRow(
    categoryLabel: String,
    distanceText: String,
    modifier: Modifier = Modifier,
    isOpen: Boolean? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            color = ScanPangColors.PrimarySoft,
        ) {
            Text(
                text = categoryLabel,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = ScanPangType.trust10SemiBold,
                color = ScanPangColors.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = distanceText,
            style = ScanPangType.detailMetaSubtitle13,
            color = ScanPangColors.OnSurfaceMuted,
            maxLines = 1,
        )
        if (isOpen != null) {
            Box(
                modifier = Modifier
                    .size(ScanPangDimens.icon5)
                    .clip(CircleShape)
                    .background(if (isOpen) ScanPangColors.StatusOpen else ScanPangColors.Error),
            )
            Text(
                text = if (isOpen) "영업 중" else "영업 종료",
                style = ScanPangType.meta11SemiBold,
                color = if (isOpen) ScanPangColors.StatusOpen else ScanPangColors.Error,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun DetailNavigateWideButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "길안내 시작",
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(ScanPangDimens.detailCtaHeight),
        shape = ScanPangShapes.radius12,
        colors = ButtonDefaults.buttonColors(
            containerColor = ScanPangColors.Primary,
            contentColor = Color.White,
        ),
    ) {
        Text(text = label, style = ScanPangType.body15Medium)
    }
}

@Composable
fun DetailNavigateAndSideIconRow(
    onNavigate: () -> Unit,
    sideIcon: ImageVector,
    sideContentDescription: String,
    onSideClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onNavigate,
            modifier = Modifier
                .weight(1f)
                .height(ScanPangDimens.detailCtaHeight),
            shape = ScanPangShapes.radius12,
            colors = ButtonDefaults.buttonColors(
                containerColor = ScanPangColors.Primary,
                contentColor = Color.White,
            ),
        ) {
            Text(text = "길안내 시작", style = ScanPangType.body15Medium)
        }
        OutlinedButton(
            onClick = onSideClick,
            modifier = Modifier.size(ScanPangDimens.detailCtaSide),
            shape = ScanPangShapes.radius12,
            border = BorderStroke(
                ScanPangDimens.borderHairline,
                ScanPangColors.OutlineSubtle,
            ),
            contentPadding = PaddingValues(),
        ) {
            Icon(
                imageVector = sideIcon,
                contentDescription = sideContentDescription,
                tint = ScanPangColors.OnSurfaceStrong,
            )
        }
    }
}

@Composable
fun DetailVisitCardsHorizontalPager(
    cards: List<DetailVisitCardUi>,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { cards.size })
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = ScanPangDimens.screenHorizontal),
        pageSpacing = ScanPangSpacing.md,
    ) { page ->
        val card = cards[page]
        val style = when (card.tone) {
            DetailVisitCardTone.Open -> VisitCardVisual(
                surface = ScanPangColors.DetailVisitOpenSurface,
                border = ScanPangColors.DetailVisitOpenBorder,
                icon = Icons.Rounded.CheckCircle,
                iconTint = ScanPangColors.StatusOpen,
            )
            DetailVisitCardTone.Closed -> VisitCardVisual(
                surface = ScanPangColors.DetailVisitClosedSurface,
                border = ScanPangColors.DetailVisitClosedBorder,
                icon = Icons.Rounded.Cancel,
                iconTint = ScanPangColors.Error,
            )
            DetailVisitCardTone.Neutral -> VisitCardVisual(
                surface = ScanPangColors.DetailVisitNeutralSurface,
                border = ScanPangColors.DetailVisitNeutralBorder,
                icon = Icons.Rounded.Info,
                iconTint = ScanPangColors.Primary,
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(ScanPangDimens.detailVisitPagerCardMinHeight),
            shape = ScanPangShapes.detailVisitCard,
            color = style.surface,
            border = BorderStroke(ScanPangDimens.borderHairline, style.border),
        ) {
            Row(
                modifier = Modifier.padding(ScanPangSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.iconTint,
                    modifier = Modifier.size(ScanPangDimens.icon18),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(ScanPangDimens.icon5),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = card.title,
                        style = ScanPangType.title14,
                        color = ScanPangColors.OnSurfaceStrong,
                    )
                    Text(
                        text = card.subtitle,
                        style = ScanPangType.caption12Medium,
                        color = ScanPangColors.OnSurfaceMuted,
                    )
                }
            }
        }
    }
}

private data class VisitCardVisual(
    val surface: Color,
    val border: Color,
    val icon: ImageVector,
    val iconTint: Color,
)

@Composable
fun DetailSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        modifier = modifier,
        style = ScanPangType.detailSectionTitle15,
        color = ScanPangColors.OnSurfaceStrong,
    )
}

@Composable
fun DetailIntroBody(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = ScanPangType.detailIntro13,
        color = ScanPangColors.OnSurfaceMuted,
    )
}

@Composable
fun DetailInfoLine(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(ScanPangDimens.icon16),
            tint = ScanPangColors.OnSurfaceMuted,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = label,
                style = ScanPangType.quickLabel12,
                color = ScanPangColors.OnSurfaceStrong,
            )
            Text(
                text = value,
                style = ScanPangType.caption12,
                color = ScanPangColors.OnSurfaceMuted,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailFacilityTagRow(
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
    ) {
        tags.forEach { tag ->
            DetailFacilityTagChip(text = tag)
        }
    }
}

@Composable
private fun DetailFacilityTagChip(text: String) {
    Surface(
        shape = ScanPangShapes.badge6,
        color = ScanPangColors.DetailFacilityTagBackground,
        border = BorderStroke(ScanPangDimens.borderHairline, ScanPangColors.OutlineSubtle),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = ScanPangSpacing.sm,
                vertical = ScanPangDimens.chipPadVertical,
            ),
            style = ScanPangType.tag11Medium,
            color = ScanPangColors.OnSurfaceStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun DetailMenuPriceRow(
    name: String,
    price: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScanPangShapes.detailMenuRow)
            .background(ScanPangColors.DetailMenuRowBackground)
            .padding(
                horizontal = ScanPangSpacing.md,
                vertical = ScanPangSpacing.sm,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = name,
            style = ScanPangType.caption12Medium,
            color = ScanPangColors.OnSurfaceStrong,
            modifier = Modifier.weight(1f).padding(end = ScanPangSpacing.sm),
        )
        Text(
            text = price,
            style = ScanPangType.detailMenuPrice14,
            color = ScanPangColors.OnSurfaceStrong,
        )
    }
}

@Composable
fun DetailScreenDivider() {
    HorizontalDivider(color = ScanPangColors.OutlineSubtle)
}

@Composable
fun DetailContentBottomSpacer() {
    Spacer(modifier = Modifier.height(ScanPangDimens.detailContentBottomPad))
}

// ── 통합 PlaceDetailScreen 전용 헬퍼 3종 (외부 hufs-cdp 통합본에서 가져옴) ──

/**
 * 히어로 사진 없는 카테고리(atm/subway/restroom/locker) 용 — 뒤로가기 버튼만 있는 상단 영역.
 */
@Composable
fun DetailBackOnlyArea(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ScanPangColors.Surface)
            .statusBarsPadding()
            .height(56.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(start = 16.dp)
                .size(ScanPangDimens.arCircleBtn36)
                .clip(CircleShape)
                .background(ScanPangColors.Background)
                .clickable(onClick = onBack)
                .align(Alignment.CenterStart),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "뒤로",
                modifier = Modifier.size(20.dp),
                tint = ScanPangColors.OnSurfaceStrong,
            )
        }
    }
}

/** 길안내(메인 CTA) + 전화(보조 사이드 버튼) 한 행 — 모든 Detail 화면 공통. */
@Composable
fun DetailCtaRow(
    onNavigate: () -> Unit,
    onPhoneClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasPhone: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(ScanPangDimens.detailCtaHeight)
                .clip(ScanPangShapes.radius14)
                .background(ScanPangColors.Primary)
                .clickable(onClick = onNavigate),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.NearMe,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.White,
                )
                Text(
                    text = "길안내 시작",
                    style = ScanPangType.detailSectionTitle15,
                    color = Color.White,
                )
            }
        }
        if (hasPhone) {
            Box(
                modifier = Modifier
                    .size(ScanPangDimens.detailCtaSide)
                    .clip(ScanPangShapes.radius14)
                    .background(ScanPangColors.Background)
                    .clickable(onClick = onPhoneClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Phone,
                    contentDescription = "전화",
                    modifier = Modifier.size(22.dp),
                    tint = ScanPangColors.OnSurfaceMuted,
                )
            }
        }
    }
}

/** 오늘 영업 상태 카드 — isOpen + open_hours 문자열을 한 줄로, 식당이면 라스트오더까지. */
@Composable
fun DetailTodayVisitStatus(
    isOpen: Boolean,
    openHours: String,
    lastOrder: String = "",
    modifier: Modifier = Modifier,
) {
    // 로컬에서 현재 시각과 open_hours 를 직접 비교해 영업 상태를 판정.
    // 파싱 불가(null)이면 서버에서 받은 isOpen 값을 fallback 으로 사용.
    val localIsOpen = remember(openHours) { OpenHoursUtils.isOpenNow(openHours) }
    val effectiveIsOpen = localIsOpen ?: isOpen
    val statusColor = if (effectiveIsOpen) ScanPangColors.StatusOpen else ScanPangColors.Error
    val cardBg = if (effectiveIsOpen) ScanPangColors.DetailVisitOpenSurface else ScanPangColors.DetailVisitClosedSurface
    val todayHours = remember(openHours) { OpenHoursUtils.todayHoursText(openHours) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
    ) {
        DetailSectionHeader(title = "오늘 방문 가능 여부")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = ScanPangShapes.radius12,
            color = cardBg,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                    Text(
                        text = if (effectiveIsOpen) "지금 영업 중" else "지금 영업 종료",
                        style = ScanPangType.caption12Medium,
                        color = statusColor,
                    )
                    if (todayHours.isNotBlank()) {
                        Text(
                            text = "·",
                            style = ScanPangType.caption12Medium,
                            color = ScanPangColors.OnSurfaceStrong,
                        )
                        Text(
                            text = todayHours,
                            style = ScanPangType.caption12Medium,
                            color = ScanPangColors.OnSurfaceStrong,
                        )
                    }
                }
                if (lastOrder.isNotBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = ScanPangColors.OnSurfaceMuted,
                        )
                        Text(
                            text = "라스트오더 $lastOrder",
                            style = ScanPangType.caption12Medium,
                            color = ScanPangColors.OnSurfaceMuted,
                        )
                    }
                }
            }
        }
    }
}
