package com.scanpang.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Atm
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.LocalConvenienceStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.scanpang.app.components.RecentSearchRow
import com.scanpang.app.components.ScanPangCategoryTile
import com.scanpang.app.components.ScanPangInlineSearchField
import com.scanpang.app.data.OnboardingPreferences
import com.scanpang.app.data.SearchHistoryPreferences
import com.scanpang.app.data.ValueAdded
import com.scanpang.app.data.remote.ScanPangViewModel
import com.scanpang.app.data.remote.SearchResultItem
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

/**
 * 추천 카테고리 타일 하나의 메타데이터.
 * `targetRoute` 가 있으면 그 라우트로 직접 이동, 없으면 `searchQuery` 로 검색을 실행한다.
 */
private data class SearchRecommendCategory(
    val label: String,
    val icon: ImageVector,
    val iconTint: Color,
    val targetRoute: String? = null,
    val searchQuery: String? = null,
)

private fun pinnedRecommendsFor(valueAdded: ValueAdded?): List<SearchRecommendCategory> = when (valueAdded) {
    ValueAdded.HALAL -> listOf(
        SearchRecommendCategory(
            label = "할랄 식당",
            icon = Icons.Rounded.Restaurant,
            iconTint = ScanPangColors.CategoryRestaurant,
            targetRoute = AppRoutes.NearbyHalal,
        ),
        SearchRecommendCategory(
            label = "기도실",
            icon = Icons.Rounded.Mosque,
            iconTint = ScanPangColors.Primary,
            targetRoute = AppRoutes.NearbyPrayer,
        ),
    )
    ValueAdded.VEGAN -> listOf(
        SearchRecommendCategory(
            label = "비건 식당",
            icon = Icons.Rounded.Restaurant,
            iconTint = ScanPangColors.CategoryRestaurant,
            searchQuery = "비건 식당",
        ),
    )
    ValueAdded.GENERAL, null -> emptyList()
}

private fun commonRecommendPool(): List<SearchRecommendCategory> = listOf(
    SearchRecommendCategory("카페", Icons.Rounded.Coffee, ScanPangColors.CategoryCafe, searchQuery = "카페"),
    SearchRecommendCategory("쇼핑", Icons.Rounded.LocalMall, ScanPangColors.CategoryMall, searchQuery = "쇼핑"),
    SearchRecommendCategory("병원", Icons.Rounded.LocalHospital, ScanPangColors.CategoryMedical, searchQuery = "병원"),
    SearchRecommendCategory("약국", Icons.Rounded.Medication, ScanPangColors.CategoryMedical, searchQuery = "약국"),
    SearchRecommendCategory("환전소", Icons.Rounded.CurrencyExchange, ScanPangColors.CategoryExchange, searchQuery = "환전소"),
    SearchRecommendCategory("관광지", Icons.Rounded.Place, ScanPangColors.Primary, searchQuery = "관광지"),
    SearchRecommendCategory("편의점", Icons.Rounded.LocalConvenienceStore, ScanPangColors.CategoryRestaurant, searchQuery = "편의점"),
    SearchRecommendCategory("ATM", Icons.Rounded.Atm, ScanPangColors.CategoryExchange, searchQuery = "ATM"),
    SearchRecommendCategory("은행", Icons.Rounded.AccountBalance, ScanPangColors.CategoryExchange, searchQuery = "은행"),
    SearchRecommendCategory("지하철역", Icons.Rounded.Train, ScanPangColors.Primary, searchQuery = "지하철역"),
    SearchRecommendCategory("화장실", Icons.Rounded.Wc, ScanPangColors.Primary, searchQuery = "화장실"),
    SearchRecommendCategory("물품보관함", Icons.Rounded.Lock, ScanPangColors.Primary, searchQuery = "물품보관함"),
)

/** 추천 카테고리는 2행 × 4열 그리드 — 8개를 노출. (pinned + 셔플된 공통풀로 채움) */
private const val SEARCH_RECOMMEND_COUNT = 8

/**
 * 최근 검색 섹션의 가시 영역 — 행 약 44dp + 행간 12dp 기준으로 5건이 한 번에 보이는 높이.
 * (RecentSearchRow 내부 vertical padding 12dp × 2 + 콘텐츠 ≒ 44dp, gap 12dp × 4 = 48dp)
 * 데이터는 [com.scanpang.app.data.SearchHistoryPreferences] 가 이미 최대 30건으로 캡한 상태.
 */
private val SEARCH_RECENT_VISIBLE_HEIGHT = 268.dp

/**
 * 검색 탭의 단일 화면.
 *
 * 검색바의 query 상태가 모드를 결정한다:
 * - 비어있을 때 → 기본 검색창 (최근 검색 + 추천 카테고리)
 * - 검색어가 있을 때 → 같은 화면에서 결과 카드 리스트로 전환
 *
 * 검색바(좌측 돋보기 + 입력 + 우측 X) 자체는 mount 상태를 유지해 모드 전환 시에도
 * 포커스/키보드가 끊기지 않는다. X 는 그냥 query 를 비울 뿐 어디로도 navigate 하지 않는다.
 *
 * Home 의 quick action 처럼 외부에서 사전입력값(prefill)을 주고 싶으면
 * [AppRoutes.SearchSavedStatePendingQueryKey] 에 문자열을 실어 두면 진입 시 자동으로 채워진다.
 */
@Composable
fun SearchDefaultScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ScanPangViewModel = viewModel(),
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val historyPrefs = remember { SearchHistoryPreferences(context) }
    val onboardingPrefs = remember { OnboardingPreferences(context) }
    val valueAdded = remember { onboardingPrefs.getValueAdded() }

    // 화면을 떠났다 돌아와도(상세 화면 등) 입력값을 유지.
    var query by rememberSaveable { mutableStateOf("") }
    var recent by remember { mutableStateOf(historyPrefs.getRecent()) }
    val backendResults by viewModel.searchResults.collectAsState()

    // Home quick action 등 외부에서 사전입력 query 를 흘려보내면 진입 시 한 번 반영.
    LaunchedEffect(Unit) {
        val entry = runCatching { navController.getBackStackEntry(AppRoutes.Search) }.getOrNull()
            ?: return@LaunchedEffect
        entry.savedStateHandle
            .getStateFlow<String?>(AppRoutes.SearchSavedStatePendingQueryKey, null)
            .collect { pending ->
                if (!pending.isNullOrEmpty()) {
                    query = pending
                    historyPrefs.add(pending)
                    recent = historyPrefs.getRecent()
                    viewModel.searchPlaces(pending)
                    entry.savedStateHandle[AppRoutes.SearchSavedStatePendingQueryKey] = null
                }
            }
    }

    // 추천 카테고리 — 검색 탭 진입 시점에 한 번 섞고 동일 셔플 유지.
    val recommendCategories = remember(valueAdded) {
        val pinned = pinnedRecommendsFor(valueAdded)
        val fillCount = (SEARCH_RECOMMEND_COUNT - pinned.size).coerceAtLeast(0)
        val randomFill = commonRecommendPool().shuffled().take(fillCount)
        pinned + randomFill
    }

    fun submitSearch(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) return
        keyboard?.hide()
        historyPrefs.add(q)
        recent = historyPrefs.getRecent()
        // 별도 navigation 없이 query 상태만 갱신 → 같은 화면에서 결과 모드로 전환.
        query = q
        viewModel.searchPlaces(q)
    }

    val trimmedQuery = query.trim()
    val isResultsMode = trimmedQuery.isNotEmpty()
    // 백엔드 검색 결과(SearchResultItem) → 화면용 ResultRow.
    // 검색어가 비면 backend 호출도 하지 않으므로 emptyList.
    val resultRows = remember(trimmedQuery, backendResults) {
        if (trimmedQuery.isEmpty()) emptyList() else backendResults.map { it.toResultRow() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScanPangColors.Surface,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScanPangColors.Surface)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = ScanPangDimens.screenHorizontal),
        ) {
            Spacer(modifier = Modifier.height(ScanPangSpacing.xl))
            // 검색바는 두 모드에서 동일 컴포지션을 유지(키보드/포커스가 모드 전환 시 끊기지 않음).
            ScanPangInlineSearchField(
                value = query,
                onValueChange = { query = it },
                onSubmit = { submitSearch(query) },
                onTrailingClick = {
                    // X 는 결과 모드 → 기본 모드로 돌아가는 유일한 트리거. 단순히 query 만 비운다.
                    query = ""
                },
                placeholder = "장소, 식당, 카테고리 검색",
            )
            Spacer(modifier = Modifier.height(ScanPangSpacing.xl))

            // weight(1f) 로 남은 세로 공간을 모두 차지 → 내부 LazyColumn 이 bounded height 를 얻음.
            if (isResultsMode) {
                SearchResultsBody(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = ScanPangDimens.mainTabContentBottomInset + ScanPangSpacing.lg),
                    query = trimmedQuery,
                    rows = resultRows,
                    onRowClick = { row ->
                        keyboard?.hide()
                        navController.navigate(row.detailRoute) { launchSingleTop = true }
                    },
                )
            } else {
                SearchDefaultBody(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = ScanPangDimens.mainTabContentBottomInset + ScanPangSpacing.lg),
                    recent = recent,
                    recommendCategories = recommendCategories,
                    onRecentClick = { submitSearch(it) },
                    onRecentRemove = {
                        historyPrefs.remove(it)
                        recent = historyPrefs.getRecent()
                    },
                    onClearAll = {
                        historyPrefs.clearAll()
                        recent = emptyList()
                    },
                    onCategoryClick = { category ->
                        when {
                            category.targetRoute != null -> navController.navigate(category.targetRoute)
                            category.searchQuery != null -> submitSearch(category.searchQuery)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchDefaultBody(
    modifier: Modifier,
    recent: List<String>,
    recommendCategories: List<SearchRecommendCategory>,
    onRecentClick: (String) -> Unit,
    onRecentRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onCategoryClick: (SearchRecommendCategory) -> Unit,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.xl),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "최근 검색",
                    style = ScanPangType.sectionTitle16,
                    color = ScanPangColors.OnSurfaceStrong,
                )
                if (recent.isNotEmpty()) {
                    Text(
                        text = "전체 삭제",
                        style = ScanPangType.caption12Medium,
                        color = ScanPangColors.OnSurfacePlaceholder,
                        modifier = Modifier.clickable(onClick = onClearAll),
                    )
                }
            }
            if (recent.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ScanPangShapes.radius14)
                        .background(ScanPangColors.Background)
                        .padding(horizontal = ScanPangSpacing.lg, vertical = ScanPangSpacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "최근 검색 기록이 없어요",
                        style = ScanPangType.body14Regular,
                        color = ScanPangColors.OnSurfaceMuted,
                    )
                }
            } else {
                // 최대 5건 가시 + 내부 스크롤. 외부 verticalScroll 충돌 회피용 heightIn(max).
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = SEARCH_RECENT_VISIBLE_HEIGHT),
                    verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
                ) {
                    items(items = recent, key = { it }) { item ->
                        RecentSearchRow(
                            query = item,
                            onRowClick = { onRecentClick(item) },
                            onRemoveClick = { onRecentRemove(item) },
                        )
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.lg)) {
            Text(
                text = "추천 카테고리",
                style = ScanPangType.sectionTitle16,
                color = ScanPangColors.OnSurfaceStrong,
            )
            Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.rowGap10)) {
                recommendCategories.chunked(4).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.rowGap10),
                    ) {
                        rowItems.forEach { category ->
                            ScanPangCategoryTile(
                                label = category.label,
                                icon = category.icon,
                                iconTint = category.iconTint,
                                onClick = { onCategoryClick(category) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // 마지막 행이 4 미만일 때 좌측 정렬 + 균등 폭 유지.
                        repeat(4 - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultsBody(
    modifier: Modifier,
    query: String,
    rows: List<ResultRow>,
    onRowClick: (ResultRow) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.lg),
    ) {
        item {
            Text(
                text = "‘$query’ 검색 결과 ${rows.size}개",
                style = ScanPangType.link13,
                color = ScanPangColors.OnSurfaceMuted,
            )
        }
        if (rows.isEmpty()) {
            item {
                Text(
                    text = "조건에 맞는 장소가 없습니다. 다른 검색어를 시도해 보세요.",
                    style = ScanPangType.body15Medium,
                    color = ScanPangColors.OnSurfaceMuted,
                    modifier = Modifier.padding(top = ScanPangSpacing.md),
                )
            }
        } else {
            items(items = rows, key = { it.id }) { row ->
                val r = row.item
                SearchResultSimpleCard(
                    title = r.title,
                    category = r.category,
                    distance = r.distance,
                    isOpen = r.isOpen,
                    onClick = { onRowClick(row) },
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────
// 검색 결과 행 데이터 + 변환 + 매칭 로직 (옛 SearchResultsScreen 에서 머지)
// ────────────────────────────────────────────────────────────

private data class ResultItem(
    val id: String,
    val title: String,
    val category: String,
    val distance: String,
    val isOpen: Boolean,
)

private data class ResultRow(
    val id: String,
    val item: ResultItem,
    val detailRoute: String,
)

/**
 * 백엔드 store_details 검색 결과 → 화면 표시용 [ResultRow] 로 변환.
 * - category 우선순위: category(Kakao 원문) → category_key(분류기 키) → "—"
 * - 거리: 백엔드 응답에 없으니 placeholder. 추후 클라이언트에서 lat/lng 로 계산.
 * - isOpen: 백엔드에 open_hours 만 있고 즉시 판정 비용이 커서 false 기본.
 */
private fun SearchResultItem.toResultRow(): ResultRow {
    val secondary = when {
        !category.isNullOrBlank() -> category
        !category_key.isNullOrBlank() -> category_key
        else -> "—"
    }
    return ResultRow(
        id = "${category_key ?: "etc"}:$id",
        item = ResultItem(
            id = id,
            title = store_name,
            category = secondary,
            distance = "",
            // backend 의 is_open_now 가 null 이면 "정보 없음" 의미 — 화면에서 영업중 표시 자체를 안 그림.
            isOpen = is_open_now == true,
        ),
        detailRoute = AppRoutes.placeDetailRoute(category_key ?: "", id),
    )
}

@Composable
private fun SearchResultSimpleCard(
    title: String,
    category: String,
    distance: String,
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScanPangShapes.radius14)
            .border(ScanPangDimens.borderHairline, ScanPangColors.OutlineSubtle, ScanPangShapes.radius14)
            .background(ScanPangColors.Surface)
            .clickable(onClick = onClick)
            .padding(ScanPangSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
    ) {
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(ScanPangDimens.stackGap6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(ScanPangShapes.badge6)
                        .background(ScanPangColors.PrimarySoft)
                        .padding(
                            horizontal = ScanPangDimens.cuisineBadgeHorizontal,
                            vertical = ScanPangDimens.badgePadVertical,
                        ),
                ) {
                    Text(
                        text = category,
                        style = ScanPangType.badge9SemiBold,
                        color = ScanPangColors.Primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = distance,
                    style = ScanPangType.meta11Medium,
                    color = ScanPangColors.OnSurfaceMuted,
                )
                if (isOpen) {
                    Box(
                        modifier = Modifier
                            .size(ScanPangDimens.icon5)
                            .clip(CircleShape)
                            .background(ScanPangColors.StatusOpen),
                    )
                    Text(
                        text = "영업 중",
                        style = ScanPangType.meta11SemiBold,
                        color = ScanPangColors.StatusOpen,
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(ScanPangDimens.icon18),
            tint = ScanPangColors.OnSurfacePlaceholder,
        )
    }
}
