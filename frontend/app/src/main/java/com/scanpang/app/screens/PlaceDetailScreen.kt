@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.scanpang.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Accessible
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.MiscellaneousServices
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.scanpang.app.data.DummyData
import com.scanpang.app.data.ExchangeRate
import com.scanpang.app.data.MenuItem
import com.scanpang.app.data.Place
import com.scanpang.app.data.RestaurantPlace
import com.scanpang.app.data.ScheduleDay
import com.scanpang.app.data.SubwayDetail
import com.scanpang.app.data.SubwayExit
import com.scanpang.app.data.SubwayFastAlight
import com.scanpang.app.data.SubwayScheduleDir
import com.scanpang.app.data.galleryModels
import com.scanpang.app.data.remote.PlaceDetailResponse
import com.scanpang.app.data.remote.ScanPangViewModel
import com.scanpang.app.data.toSubwayDetail
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

private val INFO_ROW_SPACING = 14.dp
private val SECTION_INNER_SPACING = 12.dp

/**
 * 카테고리 14종을 한 화면에서 처리하는 통합 상세 화면.
 *
 * categoryKey 는 백엔드 store_details.category_key (cafe/restaurant/...) 또는
 * [com.scanpang.app.data.SavedPlaceNavTarget.toCategoryKey] 값.
 * 데이터는 [DummyData.findPlaceById] 로 조회 — 매칭 실패 시 즉시 pop back.
 *
 * 카테고리 분기는 메타행/할랄 신뢰칩/CTA 까지만이고, 본문 ([PlaceDetailContent]) 는
 * Place 필드 유무로 자동 표시 — openHours/floor/parking/website/convenienceServices/departments.
 */
@Composable
fun PlaceDetailScreen(
    navController: NavController,
    categoryKey: String,
    placeId: String,
    modifier: Modifier = Modifier,
    viewModel: ScanPangViewModel = viewModel(),
) {
    // 1) 백엔드 store_details 조회 — placeId 가 있으면 자동, 비면 skip.
    //    화면 떠날 때 placeDetail 상태 초기화해서 다음 진입 시 stale 데이터 방지.
    LaunchedEffect(placeId) {
        viewModel.loadPlaceDetail(placeId)
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.clearPlaceDetail() }
    }
    val backend by viewModel.placeDetail.collectAsState()

    // 2) DummyData fallback — backend 가 비어있어도(또는 도착 전) 화면이 비지 않게.
    val dummyPlace = remember(categoryKey, placeId) { DummyData.findPlaceById(categoryKey, placeId) }

    // 3) 머지 — 백엔드 필드를 우선, 비면 DummyData. 둘 다 없으면 pop back.
    val place = remember(backend, dummyPlace) { backend?.mergeOnto(dummyPlace, categoryKey) ?: dummyPlace }
    if (place == null) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    val context = LocalContext.current

    val restaurantExtra = remember(categoryKey, placeId) {
        if (categoryKey in setOf("restaurant", "halal_restaurant"))
            DummyData.halalRestaurants.firstOrNull { it.place.id == placeId }
                ?: DummyData.halalRestaurants.firstOrNull()
        else null
    }
    val menuItems = remember(backend, categoryKey, placeId) {
        // 백엔드 details.menu 가 있으면 1순위 (Naver Place 크롤링 결과).
        // 비면 DummyData (cafeRepresentativeMenus / halalRestaurants[*].menuItems).
        val backendMenu = backend?.extractMenuItems().orEmpty()
        if (backendMenu.isNotEmpty()) backendMenu
        else when (categoryKey) {
            "restaurant", "halal_restaurant" -> restaurantExtra?.menuItems.orEmpty()
            "cafe" -> DummyData.cafeRepresentativeMenus[placeId].orEmpty()
            else -> emptyList()
        }
    }
    val exchangeRates = remember(categoryKey) {
        if (categoryKey in setOf("exchange", "atm", "bank")) DummyData.exchangeRates else emptyList()
    }

    // 지하철 카테고리는 백엔드 details(exits/schedule/fast_alights)에서 추출
    val subwayDetail: SubwayDetail? = remember(backend) { backend?.toSubwayDetail() }

    val hasHeroPhoto = categoryKey !in setOf("atm", "subway", "subway_station", "restroom", "public_restroom", "lockers", "locker")
    val canFullscreen = categoryKey in setOf("restaurant", "halal_restaurant", "tourist", "tourist_spot", "attraction")

    // 갤러리: 백엔드 image_urls (HTTP URL) 가 있으면 그걸 Coil 모델로 노출.
    // 비면 DummyData galleryModels (drawable Int → URL 폴백 순) 으로 대체.
    val imageModels = remember(backend, place.id, hasHeroPhoto) {
        if (!hasHeroPhoto) emptyList<Any>()
        else {
            val urls = backend?.image_urls.orEmpty().filter { it.isNotBlank() }
            if (urls.isNotEmpty()) urls
            else place.galleryModels(defaultPlaceDetailGallery())
        }
    }
    val pagerState = if (hasHeroPhoto) rememberPagerState(pageCount = { imageModels.size.coerceAtLeast(1) }) else null
    var fullscreenOpen by remember { mutableStateOf(false) }

    val bookmark = rememberDetailBookmark(
        placeId = place.id,
        placeName = place.name,
        category = place.category,
        distanceLine = "${place.category} · ${place.distance}",
        tags = place.tags,
        categoryKey = categoryKey,
    )

    if (fullscreenOpen && pagerState != null) {
        DetailImageFullscreenDialog(
            gallery = imageModels,
            pagerState = pagerState,
            onDismiss = { fullscreenOpen = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScanPangColors.Surface)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        if (hasHeroPhoto && pagerState != null) {
            DetailHeroPhotoPager(
                gallery = imageModels,
                pagerState = pagerState,
                onBack = { navController.popBackStack() },
                onFullscreenClick = if (canFullscreen) ({ fullscreenOpen = true }) else null,
            )
        } else {
            DetailBackOnlyArea(onBack = { navController.popBackStack() })
        }

        Column(
            modifier = Modifier
                .padding(horizontal = ScanPangDimens.screenHorizontal)
                .padding(top = ScanPangSpacing.md, bottom = ScanPangDimens.detailContentBottomPad),
            verticalArrangement = Arrangement.spacedBy(ScanPangDimens.detailSectionSpacing),
        ) {
            val subwayLine = if (categoryKey in setOf("subway", "subway_station"))
                place.tags.firstOrNull { it.contains("호선") } else null
            val displayTitle = if (subwayLine != null) "${place.name} $subwayLine" else place.name
            DetailTitleBookmarkRow(
                title = displayTitle,
                bookmarked = bookmark.bookmarked,
                onBookmarkClick = bookmark.onToggle,
                trailingContent = subwayLine?.let { line ->
                    {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(ScanPangColors.Primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = line.replace("호선", ""),
                                style = ScanPangType.detailSectionTitle15,
                                color = Color.White,
                            )
                        }
                    }
                },
            )

            when (categoryKey) {
                "restaurant", "halal_restaurant" -> RestaurantMetaRow(place)
                "atm" -> DetailCategoryTagDistanceRow(
                    categoryLabel = place.category,
                    distanceText = place.distance,
                    trailing = { AtmOperationBadge(place) },
                )
                "lockers", "locker", "restroom", "public_restroom" -> DetailCategoryTagDistanceRow(
                    categoryLabel = place.category,
                    distanceText = place.distance,
                    isOpen = null,
                )
                else -> DetailCategoryTagDistanceRow(
                    categoryLabel = place.category,
                    distanceText = place.distance,
                    isOpen = if (place.openHours.isNotBlank()) place.isOpen else null,
                )
            }

            if (categoryKey in setOf("restaurant", "halal_restaurant") && restaurantExtra != null) {
                PlaceHalalChipsRow(restaurantExtra)
            }

            DetailScreenDivider()

            DetailCtaRow(
                onNavigate = { navController.navigate(AppRoutes.ArNavMap) { launchSingleTop = true } },
                onPhoneClick = { if (place.phone.isNotBlank()) context.openPhoneDialer(place.phone) },
            )

            // 오늘 방문 가능 여부 — 영업시간이 있는 카테고리만
            val showVisitStatus = categoryKey in setOf(
                "restaurant", "halal_restaurant", "cafe", "shopping", "mall",
                "convenience", "convenience_store", "exchange",
                "bank", "hospital", "pharmacy", "tourist", "tourist_spot", "attraction",
            )
            if (showVisitStatus && place.openHours.isNotBlank()) {
                DetailScreenDivider()
                DetailTodayVisitStatus(
                    isOpen = place.isOpen,
                    openHours = place.openHours,
                    lastOrder = restaurantExtra?.lastOrder ?: "",
                )
            }

            DetailScreenDivider()

            PlaceDetailContent(
                place = place,
                menuItems = menuItems,
                exchangeRates = exchangeRates,
                subwayDetail = subwayDetail,
            )
        }
    }
}

// ── 통합 본문 — 카테고리 분기 없이 데이터 유무로 섹션 표시 결정 ────────────

@Composable
private fun PlaceDetailContent(
    place: Place,
    menuItems: List<MenuItem>,
    exchangeRates: List<ExchangeRate>,
    subwayDetail: SubwayDetail? = null,
) {
    if (menuItems.isNotEmpty()) {
        DetailSection(title = "대표 메뉴") {
            Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm)) {
                menuItems.forEach { m -> DetailMenuPriceRow(name = m.name, price = m.price) }
            }
        }
        DetailScreenDivider()
    }

    if (place.description.isNotBlank()) {
        DetailSection(title = "소개") {
            DetailIntroBody(text = place.description)
        }
        DetailScreenDivider()
    }

    // 상세 정보(공통) — 지하철 섹션 위로. 피그마 시안 순서.
    // 지하철은 영업시간을 별도 '열차 시간표' 섹션으로 표시하므로 여기선 숨김.
    // Kakao place.map URL(homepage)은 공식 웹사이트가 아니라 카카오 자체 페이지라 지하철엔 숨김.
    val isSubway = subwayDetail != null
    DetailSection(title = "상세 정보") {
        Column(verticalArrangement = Arrangement.spacedBy(INFO_ROW_SPACING)) {
            if (!isSubway && place.openHours.isNotBlank())
                DetailInfoLine(Icons.Rounded.AccessTime, "영업시간", place.openHours)
            if (place.address.isNotBlank()) DetailInfoLine(Icons.Rounded.Place, "주소", place.address)
            if (place.phone.isNotBlank()) DetailInfoLine(Icons.Rounded.Phone, "전화", place.phone)
            if (place.floor.isNotBlank()) DetailInfoLine(Icons.Rounded.Store, "매장 층수", place.floor)
            if (place.parking.isNotBlank()) DetailInfoLine(Icons.Rounded.LocalParking, "주차 가능 여부", place.parking)
            if (!isSubway && place.website.isNotBlank())
                DetailInfoLine(Icons.Rounded.Language, "웹사이트", place.website)
            // 화장실 카테고리 — 칸 수 / 편의시설 / 안전시설 (피그마 상세-매장(화장실))
            val toiletStr = buildList {
                if (place.toiletMale.isNotBlank())   add("남성 ${place.toiletMale}칸")
                if (place.toiletFemale.isNotBlank()) add("여성 ${place.toiletFemale}칸")
            }.joinToString(", ")
            if (toiletStr.isNotBlank())
                DetailInfoLine(Icons.Rounded.Wc, "칸 수", toiletStr)
            if (place.facilityTags.isNotBlank())
                DetailInfoLine(Icons.AutoMirrored.Rounded.Accessible, "편의시설", place.facilityTags)
            if (place.safetyTags.isNotBlank())
                DetailInfoLine(Icons.Rounded.Security, "안전시설", place.safetyTags)
            if (place.convenienceServices.isNotBlank()) DetailInfoLine(Icons.Rounded.MiscellaneousServices, "편의시설", place.convenienceServices)
            if (place.departments.isNotBlank()) DetailInfoLine(Icons.Rounded.Healing, "진료과목", place.departments)
        }
    }

    // 지하철역 전용 섹션 — 열차 시간표 → 빠른 하차 → 출구 정보 순서
    if (subwayDetail != null) {
        if (subwayDetail.scheduleUp != null || subwayDetail.scheduleDown != null) {
            DetailScreenDivider()
            DetailSection(title = "열차 시간표") {
                SubwayScheduleSection(subwayDetail)
            }
        }
        if (subwayDetail.fastAlights.isNotEmpty()) {
            DetailScreenDivider()
            DetailSection(title = "빠른 하차") {
                SubwayFastAlightsSection(subwayDetail.fastAlights)
            }
        }
        if (subwayDetail.exits.isNotEmpty()) {
            DetailScreenDivider()
            DetailSection(title = "출구 정보") {
                SubwayExitsSection(subwayDetail.exits)
            }
        }
    }

    if (exchangeRates.isNotEmpty()) {
        DetailScreenDivider()
        DetailSection(title = "오늘의 환율") {
            Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm)) {
                exchangeRates.forEach { row -> ExchangeRateRow(row) }
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SECTION_INNER_SPACING)) {
        DetailSectionHeader(title = title)
        content()
    }
}

@Composable
private fun DetailInfoLine(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(ScanPangDimens.icon16),
            tint = ScanPangColors.OnSurfaceMuted,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(text = label, style = ScanPangType.quickLabel12, color = ScanPangColors.OnSurfaceStrong)
            Text(text = value, style = ScanPangType.caption12, color = ScanPangColors.OnSurfaceMuted)
        }
    }
}

@Composable
private fun RestaurantMetaRow(place: Place) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${place.subCategory.ifBlank { "한식" }} · ${place.distance}",
            style = ScanPangType.detailMetaSubtitle13,
            color = ScanPangColors.OnSurfaceMuted,
        )
        Box(
            modifier = Modifier
                .size(ScanPangDimens.icon5)
                .clip(CircleShape)
                .background(if (place.isOpen) ScanPangColors.StatusOpen else ScanPangColors.Error),
        )
        Text(
            text = if (place.isOpen) "영업 중" else "영업 종료",
            style = ScanPangType.meta11SemiBold,
            color = if (place.isOpen) ScanPangColors.StatusOpen else ScanPangColors.Error,
        )
    }
}

@Composable
private fun AtmOperationBadge(place: Place) {
    val is24h = place.openHours.contains("24") || place.tags.any { it.contains("24") }
    Surface(
        shape = ScanPangShapes.badge6,
        color = if (is24h) ScanPangColors.DetailVisitOpenSurface else ScanPangColors.DetailFacilityTagBackground,
    ) {
        Text(
            text = if (is24h) "24시간" else "시간제",
            modifier = Modifier.padding(horizontal = ScanPangSpacing.sm, vertical = ScanPangDimens.chipPadVertical),
            style = ScanPangType.category11SemiBold,
            color = if (is24h) ScanPangColors.TrustPillText else ScanPangColors.OnSurfaceMuted,
        )
    }
}

// ── 할랄 신뢰 칩 ─────────────────────────────────────────────────────────────

@Composable
private fun PlaceHalalChipsRow(rp: RestaurantPlace) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HalalCategoryChip(label = rp.halalCategory)
        rp.place.tags.take(2).forEach { tag ->
            val icon: ImageVector = if (tag.contains("인증") || tag.contains("살람")) Icons.Rounded.Verified else Icons.Rounded.Star
            HalalTrustChip(text = tag, icon = icon)
        }
    }
}

@Composable
private fun HalalCategoryChip(label: String) {
    val (bg, fg) = when (label) {
        "HALAL MEAT" -> ScanPangColors.HalalMeatBadgeBackground to ScanPangColors.HalalMeatBadgeText
        "SEAFOOD" -> ScanPangColors.SeafoodBadgeBackground to ScanPangColors.Primary
        "VEGGIE" -> ScanPangColors.VeggieBadgeBackground to ScanPangColors.VeggieBadgeText
        "SALAM SEOUL" -> ScanPangColors.SalamSeoulBadgeBackground to ScanPangColors.SalamSeoulBadgeText
        else -> ScanPangColors.HalalMeatBadgeBackground to ScanPangColors.HalalMeatBadgeText
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
private fun HalalTrustChip(text: String, icon: ImageVector) {
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
        androidx.compose.material3.Icon(
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

// ── 환율 행 ───────────────────────────────────────────────────────────────────

@Composable
private fun ExchangeRateRow(row: ExchangeRate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ScanPangShapes.detailMenuRow)
            .background(ScanPangColors.DetailMenuRowBackground)
            .padding(horizontal = ScanPangSpacing.md, vertical = ScanPangSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${row.flag} ${row.currency} → KRW",
            style = ScanPangType.caption12Medium,
            color = ScanPangColors.OnSurfaceStrong,
        )
        Text(
            text = row.rate,
            style = ScanPangType.detailMenuPrice14,
            color = ScanPangColors.OnSurfaceStrong,
        )
    }
}

// ── Backend → 화면 모델 머지 / 추출 헬퍼 ──────────────────────────────────────

/**
 * [PlaceDetailResponse] 를 [Place] 위에 머지. 백엔드 필드가 있으면 우선, 없으면 fallback.
 * fallback 이 null 이면 백엔드 단독으로 Place 를 합성 — 거리/이미지/태그처럼 백엔드에
 * 없는 정보는 빈값.
 *
 * categoryKey 는 백엔드 응답의 category_key 보다 항상 우선 — 라우트에서 들어온 값이
 * 화면 분기의 기준이기 때문.
 */
private fun PlaceDetailResponse.mergeOnto(fallback: Place?, categoryKey: String): Place {
    val base = fallback ?: Place(
        id = id,
        name = store_name,
        category = category.orEmpty(),
        distance = "",
        address = addr.orEmpty(),
    )

    // 화장실 카테고리 — backend details 의 boolean / 칸 수를 Place 필드로 평탄화.
    val toiletMale   = (details["male_toilt_cnt"]   as? String).orEmpty()
    val toiletFemale = (details["female_toilt_cnt"] as? String).orEmpty()
    val facilityList = buildList {
        if (details["has_disabled"]     as? Boolean == true) add("장애인 화장실")
        if (details["has_child"]        as? Boolean == true) add("유아 화장실")
        if (details["has_diaper_table"] as? Boolean == true) add("기저귀 교환대")
    }
    val safetyList = buildList {
        if (details["has_cctv"]           as? Boolean == true) add("CCTV")
        if (details["has_emergency_bell"] as? Boolean == true) add("비상벨")
    }

    return base.copy(
        id = id.ifBlank { base.id },
        name = store_name.ifBlank { base.name },
        category = category ?: base.category,
        address = addr ?: base.address,
        phone = phone ?: base.phone,
        openHours = open_hours ?: base.openHours,
        // 백엔드 is_open_now=null 이면 fallback 유지(휴리스틱 실패).
        isOpen = is_open_now ?: base.isOpen,
        floor = floor ?: base.floor,
        website = homepage ?: base.website,
        categoryKey = categoryKey,
        latitude = lat ?: base.latitude,
        longitude = lng ?: base.longitude,
        toiletMale   = toiletMale.ifBlank   { base.toiletMale },
        toiletFemale = toiletFemale.ifBlank { base.toiletFemale },
        facilityTags = if (facilityList.isNotEmpty()) facilityList.joinToString(", ") else base.facilityTags,
        safetyTags   = if (safetyList.isNotEmpty())   safetyList.joinToString(", ")   else base.safetyTags,
    )
}

/**
 * details.menu (Naver Place 크롤링) → 화면용 MenuItem 리스트.
 * 백엔드는 `details: {"menu": [{"name": "...", "price": "..."}]}` 형태로 저장.
 * Gson 디폴트로 LinkedTreeMap 이라 Map 캐스팅으로 안전 추출.
 */
private fun PlaceDetailResponse.extractMenuItems(): List<MenuItem> {
    val raw = details["menu"] as? List<*> ?: return emptyList()
    return raw.mapNotNull { entry ->
        val m = entry as? Map<*, *> ?: return@mapNotNull null
        val name = (m["name"] as? String)?.trim().orEmpty()
        val price = (m["price"] as? String)?.trim().orEmpty()
        if (name.isBlank()) null else MenuItem(name = name, price = price)
    }
}


// ── 지하철역 전용 섹션 (백엔드 seoul_metro fetcher 응답 기반) ──────────────

@Composable
private fun SubwayScheduleSection(detail: SubwayDetail) {
    // 오늘 요일을 default 선택. 사용자가 다른 요일도 볼 수 있게 토글 칩 제공.
    var selectedDay by remember { mutableStateOf(detail.todayKind()) }
    val (up, down) = detail.scheduleFor(selectedDay)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SubwayScheduleDayTabs(selected = selectedDay, onSelect = { selectedDay = it })
        up?.let   { SubwayScheduleRow("상행", it) }
        down?.let { SubwayScheduleRow("하행", it) }
    }
}

@Composable
private fun SubwayScheduleDayTabs(
    selected: ScheduleDay,
    onSelect: (ScheduleDay) -> Unit,
) {
    val items = listOf(
        ScheduleDay.WEEKDAY  to "평일",
        ScheduleDay.SATURDAY to "토요일",
        ScheduleDay.HOLIDAY  to "일·공휴일",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (day, label) ->
            val isSelected = day == selected
            Surface(
                modifier = Modifier.clickable { onSelect(day) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) ScanPangColors.Primary else ScanPangColors.Background,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = ScanPangType.quickLabel12,
                    color = if (isSelected) Color.White else ScanPangColors.OnSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun SubwayScheduleRow(label: String, dir: SubwayScheduleDir) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ScanPangShapes.radius12,
        color = ScanPangColors.Background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(4.dp), color = ScanPangColors.PrimarySoft) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    style = ScanPangType.badge9SemiBold,
                    color = ScanPangColors.Primary,
                )
            }
            Text(
                text = "  ${dir.toward} 방면",
                modifier = Modifier.weight(1f),
                style = ScanPangType.caption12,
                color = ScanPangColors.OnSurfaceMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("첫차", style = ScanPangType.tag11Medium, color = ScanPangColors.OnSurfaceMuted)
                    Text(dir.first, style = ScanPangType.detailSectionTitle15, color = ScanPangColors.OnSurfaceStrong)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("막차", style = ScanPangType.tag11Medium, color = ScanPangColors.OnSurfaceMuted)
                    Text(dir.last, style = ScanPangType.detailSectionTitle15, color = ScanPangColors.OnSurfaceStrong)
                }
            }
        }
    }
}

@Composable
private fun SubwayExitsSection(exits: List<SubwayExit>) {
    var selectedExitNo by remember { mutableStateOf(exits.firstOrNull()?.exitNo ?: "") }
    val selectedExit = exits.firstOrNull { it.exitNo == selectedExitNo }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(exits) { exit ->
                val selected = exit.exitNo == selectedExitNo
                Surface(
                    modifier = Modifier.clickable { selectedExitNo = exit.exitNo },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) ScanPangColors.Primary else ScanPangColors.Background,
                ) {
                    Text(
                        text = "${exit.exitNo}번",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = ScanPangType.quickLabel12,
                        color = if (selected) Color.White else ScanPangColors.OnSurfaceMuted,
                    )
                }
            }
        }
        selectedExit?.let { exit ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = ScanPangShapes.radius12,
                color = ScanPangColors.Background,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${exit.exitNo}번 출구 주변",
                        style = ScanPangType.quickLabel12,
                        color = ScanPangColors.OnSurfaceStrong,
                    )
                    exit.facilities.forEach { fac ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(ScanPangColors.OnSurfaceMuted),
                            )
                            Text(fac, style = ScanPangType.caption12, color = ScanPangColors.OnSurfaceMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubwayFastAlightsSection(fastAlights: List<SubwayFastAlight>) {
    // direction(예: 회현/충무로)별로 첫 항목만 — 백엔드 응답엔 동일 방면이 차량문·
    // 이동설비별로 중복돼 들어오므로 화면엔 방면당 1건만 (피그마 시안과 동일).
    val perDirection = fastAlights
        .filter { it.direction.isNotBlank() && it.door.isNotBlank() }
        .groupBy { it.direction }
        .map { (_, items) -> items.first() }
        .take(2)
    if (perDirection.isEmpty()) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ScanPangShapes.radius12,
        color = ScanPangColors.Background,
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            perDirection.forEachIndexed { index, item ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${item.direction} 방면",
                        style = ScanPangType.caption12,
                        color = ScanPangColors.OnSurfaceMuted,
                    )
                    Text(
                        text = item.door,
                        style = ScanPangType.detailSectionTitle15,
                        color = ScanPangColors.OnSurfaceStrong,
                    )
                }
                if (index == 0 && perDirection.size >= 2) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(width = 1.dp, height = 40.dp)
                            .background(ScanPangColors.OutlineSubtle),
                    )
                }
            }
        }
    }
}
