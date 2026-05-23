@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.scanpang.app.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.scanpang.app.data.ExchangeRate
import com.scanpang.app.data.MenuItem
import com.scanpang.app.data.Place
import com.scanpang.app.data.RestaurantPlace
import com.scanpang.app.data.ScheduleDay
import com.scanpang.app.data.SubwayDetail
import com.scanpang.app.util.OpenHoursUtils
import com.scanpang.app.data.SubwayExit
import com.scanpang.app.data.SubwayFastAlight
import com.scanpang.app.data.SubwayScheduleDir
import com.scanpang.app.data.remote.PlaceDetailResponse
import com.scanpang.app.data.remote.ScanPangViewModel
import com.scanpang.app.data.toSubwayDetail
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.i18n.AppStrings
import com.scanpang.app.i18n.LocalStrings
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

private val INFO_ROW_SPACING = 14.dp
private val SECTION_INNER_SPACING = 12.dp

// 카테고리 맵/셋은 PlaceDetailCommon.kt 의 PLACE_CATEGORY_KO / PLACE_USE_RAW_CATEGORY 공유.

/**
 * 카테고리 14종을 한 화면에서 처리하는 통합 상세 화면.
 *
 * categoryKey 는 백엔드 store_details.category_key (cafe/restaurant/...) 또는
 * [com.scanpang.app.data.SavedPlaceNavTarget.toCategoryKey] 값.
 * 백엔드 응답 도착 전 로딩 중, 응답 후 place == null 이면 즉시 pop back.
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
    val s = LocalStrings.current
    val context = LocalContext.current
    // GPS — NavHost destination 마다 viewModel() 인스턴스가 달라 다른 화면의
    // setUserLocation 이 이 화면 인스턴스에 안 닿음. SearchDefaultScreen 처럼
    // 자체적으로 lastLocation 받아 백엔드 거리 계산에 사용.
    var userLat by remember { mutableStateOf<Double?>(null) }
    var userLng by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(Unit) {
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
                userLat = loc.latitude
                userLng = loc.longitude
            } else {
                // lastLocation null (GPS cold start 등) → getCurrentLocation 으로
                // 능동 1회 fix. PRIORITY_BALANCED 면 셀룰러/와이파이도 활용해
                // 빨리 반환됨. 안 그러면 distance_m 영원히 null.
                client.getCurrentLocation(
                    com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    null,
                ).addOnSuccessListener { fresh ->
                    if (fresh != null) {
                        userLat = fresh.latitude
                        userLng = fresh.longitude
                    }
                }
            }
        }
    }

    // 1) 백엔드 store_details 조회 — placeId/userLat/userLng 변경 시 재호출
    //    (캐시 키에 lat/lng 포함). 화면 떠날 때 placeDetail 초기화로 stale 방지.
    LaunchedEffect(placeId, userLat, userLng) {
        viewModel.loadPlaceDetail(placeId, userLat = userLat, userLng = userLng)
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.clearPlaceDetail() }
    }
    val backend by viewModel.placeDetail.collectAsState()

    val place = remember(backend, s) { backend?.mergeOnto(null, categoryKey, s) }
    if (place == null) {
        if (backend != null) {
            LaunchedEffect(Unit) { navController.popBackStack() }
        } else {
            PlaceLoadingScreen()
        }
        return
    }

    val displayCategory = resolveCategoryLabel(
        categoryKey = categoryKey,
        rawCategory = place.category,
        veganLevel = (backend?.details?.get("vegan_level") as? String).orEmpty(),
    )

    val restaurantExtra = remember(categoryKey, backend, s) {
        if (categoryKey !in setOf("restaurant", "halal_restaurant")) return@remember null
        backend?.toRestaurantPlaceOrNull(s)
    }
    val menuItems = remember(backend, categoryKey) {
        if (categoryKey !in setOf("restaurant", "halal_restaurant", "cafe", "vegan_restaurant", "vegan_cafe")) return@remember emptyList()
        val backendMenu = backend?.extractMenuItems().orEmpty()
        if (backendMenu.isNotEmpty()) backendMenu
        else when (categoryKey) {
            "restaurant", "halal_restaurant" -> restaurantExtra?.menuItems.orEmpty()
            else -> emptyList()
        }
    }
    val exchangeRates = remember(categoryKey, backend) {
        if (categoryKey !in setOf("exchange", "atm", "bank")) return@remember emptyList()
        backend?.extractExchangeRates().orEmpty()
    }

    // 지하철 카테고리는 백엔드 details(exits/schedule/fast_alights)에서 추출
    val subwayDetail: SubwayDetail? = remember(backend) { backend?.toSubwayDetail() }

    val heroPhotoAllowed = categoryKey !in setOf("atm", "subway", "subway_station", "restroom", "public_restroom", "lockers", "locker")

    // 갤러리: 백엔드 image_urls 가 1개 이상 있을 때만 표시. URL 없으면 사진 영역 자체를 숨김.
    val imageModels = remember(backend, place.id, heroPhotoAllowed) {
        if (!heroPhotoAllowed) emptyList<Any>()
        else backend?.image_urls.orEmpty().filter { it.isNotBlank() }
    }
    val hasHeroPhoto = heroPhotoAllowed && imageModels.isNotEmpty()
    val canFullscreen = hasHeroPhoto
    val pagerState = if (hasHeroPhoto) rememberPagerState(pageCount = { imageModels.size }) else null
    var fullscreenOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = fullscreenOpen) { fullscreenOpen = false }

    val bookmark = rememberDetailBookmark(
        placeId = place.id,
        placeName = place.name,
        category = displayCategory,
        distanceLine = "${displayCategory} · ${place.distance}",
        tags = place.tags,
        categoryKey = categoryKey,
        lat = place.latitude,
        lng = place.longitude,
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
                "restaurant", "halal_restaurant" -> DetailCategoryTagDistanceRow(
                    categoryLabel = displayCategory,
                    distanceText = place.distance,
                    isOpen = if (place.openHours.isNotBlank()) place.isOpen else null,
                )
                "atm" -> DetailCategoryTagDistanceRow(
                    categoryLabel = displayCategory,
                    distanceText = place.distance,
                    trailing = { AtmOperationBadge(place) },
                )
                "lockers", "locker", "restroom", "public_restroom" -> DetailCategoryTagDistanceRow(
                    categoryLabel = displayCategory,
                    distanceText = place.distance,
                    isOpen = null,
                )
                else -> DetailCategoryTagDistanceRow(
                    categoryLabel = displayCategory,
                    distanceText = place.distance,
                    isOpen = if (place.openHours.isNotBlank()) place.isOpen else null,
                )
            }

            if (categoryKey in setOf("restaurant", "halal_restaurant") && restaurantExtra != null) {
                PlaceHalalChipsRow(restaurantExtra)
            }

            DetailScreenDivider()

            DetailCtaRow(
                onNavigate = {
                    navController.navigate(
                        AppRoutes.arNavMapRoute(place.name, place.latitude, place.longitude)
                    ) { launchSingleTop = true }
                },
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
    val s = LocalStrings.current
    val isSubway = subwayDetail != null
    // 화장실 카테고리는 소개·웹사이트·매장 층수 숨김.
    val isRestroom = place.categoryKey in setOf("restroom", "public_restroom")

    if (menuItems.isNotEmpty()) {
        var isMenuExpanded by remember { mutableStateOf(false) }
        val visibleMenus = if (isMenuExpanded) menuItems else menuItems.take(5)
        DetailSection(title = s.placeFeaturedMenu) {
            Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm)) {
                visibleMenus.forEach { m -> DetailMenuPriceRow(name = m.name, price = m.price) }
                if (menuItems.size > 5) {
                    TextButton(
                        onClick = { isMenuExpanded = !isMenuExpanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = if (isMenuExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(ScanPangDimens.icon16),
                        )
                        Text(
                            text = if (isMenuExpanded) s.detailCollapse else s.detailExpandMore(menuItems.size - 5),
                            style = ScanPangType.caption12Medium,
                        )
                    }
                }
            }
        }
        DetailScreenDivider()
    }

    if (!isRestroom && place.description.isNotBlank()) {
        DetailSection(title = s.detailIntro) {
            DetailIntroBody(text = place.description)
        }
        DetailScreenDivider()
    }

    // 상세 정보(공통) — 지하철 섹션 위로. 피그마 시안 순서.
    // 지하철은 영업시간을 별도 '열차 시간표' 섹션으로 표시하므로 여기선 숨김.
    // Kakao place.map URL(homepage)은 공식 웹사이트가 아니라 카카오 자체 페이지라 지하철엔 숨김.
    DetailSection(title = s.detailInfo) {
        Column(verticalArrangement = Arrangement.spacedBy(INFO_ROW_SPACING)) {
            if (!isSubway && place.openHours.isNotBlank()) {
                val groupedHours = remember(place.openHours) {
                    OpenHoursUtils.groupedHoursText(place.openHours)
                }
                DetailInfoLine(Icons.Rounded.AccessTime, s.detailOpenHours, groupedHours)
            }
            if (place.address.isNotBlank()) DetailInfoLine(Icons.Rounded.Place, s.detailAddress, place.address)
            if (place.phone.isNotBlank()) DetailInfoLine(Icons.Rounded.Phone, s.detailPhone, place.phone)
            if (!isRestroom && place.floor.isNotBlank())
                DetailInfoLine(Icons.Rounded.Store, s.detailFloor, place.floor)
            val showParking = place.parking.isNotBlank() &&
                place.categoryKey !in setOf("prayer_room", "prayer", "lockers", "locker", "restroom", "public_restroom", "subway", "subway_station")
            if (showParking) DetailInfoLine(Icons.Rounded.LocalParking, s.detailParking, place.parking)
            if (!isSubway && !isRestroom && place.website.isNotBlank())
                DetailInfoLine(Icons.Rounded.Language, s.detailWebsite, place.website)
            // 화장실 카테고리 — 칸 수 / 편의시설 / 안전시설 (피그마 상세-매장(화장실))
            // DB에 0/공란/문자 인 row 도 있어서 "남성 0칸" 같이 무의미한 표시 회피.
            // 숫자 파싱해서 양수만 노출, 둘 다 0/없으면 칸 수 행 자체 숨김.
            val maleCnt   = place.toiletMale.toIntOrNull() ?: 0
            val femaleCnt = place.toiletFemale.toIntOrNull() ?: 0
            val toiletStr = buildList {
                if (maleCnt > 0)   add(s.placeMaleStalls(maleCnt))
                if (femaleCnt > 0) add(s.placeFemaleStalls(femaleCnt))
            }.joinToString(", ")
            if (toiletStr.isNotBlank())
                DetailInfoLine(Icons.Rounded.Wc, s.detailToiletStalls, toiletStr)
            if (place.facilityTags.isNotBlank())
                DetailInfoLine(Icons.AutoMirrored.Rounded.Accessible, s.detailFacility, place.facilityTags)
            if (place.safetyTags.isNotBlank())
                DetailInfoLine(Icons.Rounded.Security, s.detailSafety, place.safetyTags)
            if (place.convenienceServices.isNotBlank()) DetailInfoLine(Icons.Rounded.MiscellaneousServices, s.detailFacility, place.convenienceServices)
            if (place.departments.isNotBlank()) DetailInfoLine(Icons.Rounded.Healing, s.detailDepartments, place.departments)
        }
    }

    // 지하철역 전용 섹션 — 열차 시간표 → 빠른 하차 → 출구 정보 순서
    if (subwayDetail != null) {
        if (subwayDetail.scheduleUp != null || subwayDetail.scheduleDown != null) {
            DetailScreenDivider()
            DetailSection(title = s.detailSubwaySchedule) {
                SubwayScheduleSection(subwayDetail)
            }
        }
        if (subwayDetail.fastAlights.isNotEmpty()) {
            DetailScreenDivider()
            DetailSection(title = s.detailSubwayFastAlight) {
                SubwayFastAlightsSection(subwayDetail.fastAlights)
            }
        }
        if (subwayDetail.exits.isNotEmpty()) {
            DetailScreenDivider()
            DetailSection(title = s.detailSubwayExits) {
                SubwayExitsSection(subwayDetail.exits)
            }
        }
    }

    if (exchangeRates.isNotEmpty()) {
        DetailScreenDivider()
        DetailSection(title = s.placeTodayExchangeRate) {
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
private fun AtmOperationBadge(place: Place) {
    val s = LocalStrings.current
    val is24h = place.openHours.contains("24") || place.tags.any { it.contains("24") }
    Surface(
        shape = ScanPangShapes.badge6,
        color = if (is24h) ScanPangColors.DetailVisitOpenSurface else ScanPangColors.DetailFacilityTagBackground,
    ) {
        Text(
            text = if (is24h) s.detailAtm24h else s.detailAtmHourly,
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
private fun formatDistanceMeters(m: Double?): String = when {
    m == null -> ""
    m < 1000  -> "${m.toInt()}m"
    else      -> "%.1fkm".format(m / 1000.0)
}

private fun PlaceDetailResponse.mergeOnto(fallback: Place?, categoryKey: String, strings: AppStrings): Place {
    val backendDistance = formatDistanceMeters(distance_m)
    val base = fallback ?: Place(
        id = id,
        name = store_name,
        category = category.orEmpty(),
        distance = backendDistance,
        address = addr.orEmpty(),
    )

    // 화장실 카테고리 — backend details 의 boolean / 칸 수를 Place 필드로 평탄화.
    val toiletMale   = (details["male_toilt_cnt"]   as? String).orEmpty()
    val toiletFemale = (details["female_toilt_cnt"] as? String).orEmpty()
    val facilityList = buildList {
        if (details["has_disabled"]     as? Boolean == true) add(strings.placeDisabledRestroom)
        if (details["has_child"]        as? Boolean == true) add(strings.placeChildRestroom)
        if (details["has_diaper_table"] as? Boolean == true) add(strings.placeDiaperTable)
        // 기도실 카테고리 — facilities 객체 평탄화
        if (details["wudu"]              as? Boolean == true) add(strings.placeWudu)
        if (details["gender_separation"] as? Boolean == true) add(strings.placeGenderSeparated)
        if (details["prayer_mat"]        as? Boolean == true) add(strings.placePrayerMat)
        if (details["quran_available"]   as? Boolean == true) add(strings.placeQuran)
    }
    val safetyList = buildList {
        if (details["has_cctv"]           as? Boolean == true) add("CCTV")
        if (details["has_emergency_bell"] as? Boolean == true) add(strings.placeEmergencyBell)
    }

    // conveniences 배열 — 주차 여부 + 편의시설 텍스트
    val conveniences = (details["conveniences"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    val parkingValue = if (conveniences.any { it.contains("주차") }) "가능" else ""
    val convenienceServicesValue = conveniences.filter { !it.contains("주차") }.joinToString(", ")

    // 소개 — 할랄 식당: short_description_ko / naver scraper: intro / 기도실: notes.
    val backendDesc = (details["short_description_ko"] as? String)?.trim().orEmpty()
        .ifBlank { (details["intro"] as? String)?.trim().orEmpty() }
        .ifBlank { (details["notes"] as? String)?.trim().orEmpty() }

    // subCategory 결정:
    //  - 할랄 식당: details.cuisine_type ("한식·해산물" 같은 정제된 큐레이션 데이터)
    //  - 그 외: backend.category 의 마지막 토큰 ("음식점 > 치킨,닭강정" → "치킨,닭강정",
    //    단일 토큰이면 그대로 "치킨"). 일반 식당은 cuisine_type 이 비어 있다.
    val subCategory = if (categoryKey == "halal_restaurant") {
        val cuisineList = (details["cuisine_type"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        cuisineList.joinToString("·").ifBlank { base.subCategory }
    } else {
        (category ?: "").substringAfterLast(">").trim().ifBlank { base.subCategory }
    }

    // 할랄 신뢰 태그 — details 에 있으면 덮어씀
    val halalTags = buildList {
        if (details["muslim_cooks_available"] as? Boolean == true) add(strings.placeMuslimChef)
        if (details["no_alcohol_sales"]       as? Boolean == true) add(strings.placeNoAlcohol)
    }

    return base.copy(
        id = id.ifBlank { base.id },
        name = store_name.ifBlank { base.name },
        category = category ?: base.category,
        subCategory = subCategory,
        // 백엔드가 거리 계산해 보내면 그걸 우선 (request 에 user_lat/lng 있을 때만).
        distance = backendDistance.ifBlank { base.distance },
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
        description = backendDesc.ifBlank { base.description },
        tags = halalTags.ifEmpty { base.tags },
        toiletMale   = toiletMale.ifBlank   { base.toiletMale },
        toiletFemale = toiletFemale.ifBlank { base.toiletFemale },
        facilityTags = if (facilityList.isNotEmpty()) facilityList.joinToString(", ") else base.facilityTags,
        safetyTags   = if (safetyList.isNotEmpty())   safetyList.joinToString(", ")   else base.safetyTags,
        parking            = parkingValue.ifBlank { base.parking },
        convenienceServices = convenienceServicesValue.ifBlank { base.convenienceServices },
    )
}

/**
 * details.rates_today (한국수출입은행 OpenAPI) → 화면용 [ExchangeRate] 리스트.
 * 백엔드 format: [{ccy, name, flag, base_rate, ...}]. base_rate 는 매매기준율
 * 문자열 ("1320.50") — "1,320원" 으로 천 단위 콤마 + 정수 변환.
 */
private fun PlaceDetailResponse.extractExchangeRates(): List<ExchangeRate> {
    val raw = details["rates_today"] as? List<*> ?: return emptyList()
    return raw.mapNotNull { entry ->
        val m = entry as? Map<*, *> ?: return@mapNotNull null
        val ccy = (m["ccy"] as? String)?.trim().orEmpty()
        val flag = (m["flag"] as? String).orEmpty()
        val baseRate = (m["base_rate"] as? String)?.trim().orEmpty()
        if (ccy.isBlank() || baseRate.isBlank()) return@mapNotNull null
        // "1,320.50" 같은 문자열 → 천 단위 콤마 제거 후 Double → 정수 + "원"
        val rateText = baseRate.replace(",", "").toDoubleOrNull()
            ?.let { "%,d원".format(it.toInt()) }
            ?: "${baseRate}원"
        ExchangeRate(currency = ccy, rate = rateText, flag = flag)
    }
}

/**
 * details.menu (Naver Place 크롤링) → 화면용 MenuItem 리스트.
 * 백엔드는 `details: {"menu": [{"name": "...", "price": "..."}]}` 형태로 저장.
 * Gson 디폴트로 LinkedTreeMap 이라 Map 캐스팅으로 안전 추출.
 */
private fun PlaceDetailResponse.extractMenuItems(): List<MenuItem> {
    // 1) Naver 크롤링 결과 — details.menu = [{name, price}, ...] (cafe/restaurant 등)
    (details["menu"] as? List<*>)?.let { raw ->
        val items = raw.mapNotNull { entry ->
            val m = entry as? Map<*, *> ?: return@mapNotNull null
            val name = (m["name"] as? String)?.trim().orEmpty()
            val price = (m["price"] as? String)?.trim().orEmpty()
            if (name.isBlank()) null else MenuItem(name = name, price = price)
        }
        if (items.isNotEmpty()) return items
    }
    // 2) 할랄 식당 — details.menu_examples = [{name_ko, name_en, price_krw}, ...]
    (details["menu_examples"] as? List<*>)?.let { raw ->
        return raw.mapNotNull { entry ->
            val m = entry as? Map<*, *> ?: return@mapNotNull null
            val name = ((m["name_ko"] as? String) ?: (m["name_en"] as? String))?.trim().orEmpty()
            val priceRaw = m["price_krw"]
            val price = when (priceRaw) {
                is Number -> "%,d원".format(priceRaw.toInt())
                is String -> priceRaw.trim()
                else -> ""
            }
            if (name.isBlank()) null else MenuItem(name = name, price = price)
        }
    }
    return emptyList()
}


/**
 * `/place/detail` 응답 → RestaurantPlace.
 * halal_type 이 없고 category_key 도 halal_restaurant 이 아니면 null 반환.
 */
private fun PlaceDetailResponse.toRestaurantPlaceOrNull(strings: AppStrings): RestaurantPlace? {
    val halalType = (details["halal_type"] as? String).orEmpty()
    if (halalType.isEmpty() && category_key != "halal_restaurant") return null

    val cuisineList = (details["cuisine_type"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
    val tags = buildList {
        if (details["muslim_cooks_available"] as? Boolean == true) add(strings.placeMuslimChef)
        if (details["no_alcohol_sales"]       as? Boolean == true) add(strings.placeNoAlcohol)
    }
    val place = com.scanpang.app.data.Place(
        id = id,
        name = store_name,
        category = category.orEmpty(),
        subCategory = cuisineList.joinToString("·"),
        categoryKey = category_key.orEmpty(),
        distance = formatDistanceMeters(distance_m),
        address = addr.orEmpty(),
        phone = phone.orEmpty(),
        openHours = open_hours.orEmpty(),
        isOpen = is_open_now ?: true,
        tags = tags,
        latitude = lat ?: 37.5636,
        longitude = lng ?: 126.9869,
    )
    val lastOrderRaw = details["last_order"]
    val lastOrderStr = when (lastOrderRaw) {
        is String -> lastOrderRaw
        is Map<*, *> -> {
            val days = listOf("mon","tue","wed","thu","fri","sat","sun")
            val todayIdx = (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
            (lastOrderRaw[days.getOrNull(todayIdx)] as? String).orEmpty()
        }
        else -> ""
    }
    return com.scanpang.app.data.RestaurantPlace(
        place = place,
        halalCategory = halalType,
        menuItems = extractMenuItems(),
        lastOrder = lastOrderStr,
        isMoslemChef = details["muslim_cooks_available"] as? Boolean ?: false,
        noAlcohol = details["no_alcohol_sales"] as? Boolean ?: false,
    )
}


// ── 지하철역 전용 섹션 (백엔드 seoul_metro fetcher 응답 기반) ──────────────

@Composable
private fun SubwayScheduleSection(detail: SubwayDetail) {
    val s = LocalStrings.current
    var selectedDay by remember { mutableStateOf(detail.todayKind()) }
    val (up, down) = detail.scheduleFor(selectedDay)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SubwayScheduleDayTabs(selected = selectedDay, onSelect = { selectedDay = it })
        up?.let   { SubwayScheduleRow(s.detailSubwayUp, it) }
        down?.let { SubwayScheduleRow(s.detailSubwayDown, it) }
    }
}

@Composable
private fun SubwayScheduleDayTabs(
    selected: ScheduleDay,
    onSelect: (ScheduleDay) -> Unit,
) {
    val s = LocalStrings.current
    val items = listOf(
        ScheduleDay.WEEKDAY  to s.detailSubwayWeekday,
        ScheduleDay.SATURDAY to s.detailSubwaySaturday,
        ScheduleDay.HOLIDAY  to s.detailSubwayHoliday,
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
    val s = LocalStrings.current
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
                text = "  ${s.detailSubwayToward(dir.toward)}",
                modifier = Modifier.weight(1f),
                style = ScanPangType.caption12,
                color = ScanPangColors.OnSurfaceMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(s.detailSubwayFirst, style = ScanPangType.tag11Medium, color = ScanPangColors.OnSurfaceMuted)
                    Text(dir.first, style = ScanPangType.detailSectionTitle15, color = ScanPangColors.OnSurfaceStrong)
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(s.detailSubwayLast, style = ScanPangType.tag11Medium, color = ScanPangColors.OnSurfaceMuted)
                    Text(dir.last, style = ScanPangType.detailSectionTitle15, color = ScanPangColors.OnSurfaceStrong)
                }
            }
        }
    }
}

@Composable
private fun SubwayExitsSection(exits: List<SubwayExit>) {
    val s = LocalStrings.current
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
                        text = s.detailSubwayExitLabel(exit.exitNo),
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
                        text = s.detailSubwayExitAround(exit.exitNo),
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
    val s = LocalStrings.current
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
                        text = s.detailSubwayToward(item.direction),
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
