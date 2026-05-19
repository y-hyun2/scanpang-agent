package com.scanpang.app.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.scanpang.app.components.RecentlyViewedRow
import com.scanpang.app.components.toDetailRoute
import com.scanpang.app.data.OnboardingPreferences
import com.scanpang.app.data.RecentlyViewedEntry
import com.scanpang.app.data.RecentlyViewedStore
import com.scanpang.app.data.ValueAdded
import com.scanpang.app.data.remote.ScanPangViewModel
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.navigation.navigateToSearchWithQuery
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType
import java.util.Locale

/**
 * 홈 빠른액션 카드 한 장의 스펙. 라벨/아이콘과, NavController 가 들어오면 어디로 보낼지 결정한다.
 * ValueAdded 분기별로 [quickActionsFor] 가 3장을 만들어 [HomeScreen] 으로 흘려준다.
 */
private data class HomeQuickAction(
    val title: String,
    val icon: ImageVector,
    val navigate: (NavController) -> Unit,
)

private fun quickActionsFor(value: ValueAdded): List<HomeQuickAction> = when (value) {
    ValueAdded.HALAL -> listOf(
        HomeQuickAction("할랄 식당", Icons.Rounded.Restaurant) { it.navigateToSearchWithQuery("할랄 식당") },
        HomeQuickAction("기도실", Icons.Rounded.Mosque) { it.navigateToSearchWithQuery("기도실") },
        HomeQuickAction("관광명소", Icons.Rounded.Whatshot) { it.navigateToSearchWithQuery("관광지") },
    )
    ValueAdded.VEGAN -> listOf(
        HomeQuickAction("비건 식당", Icons.Rounded.Restaurant) { it.navigateToSearchWithQuery("비건 식당") },
        HomeQuickAction("비건 카페", Icons.Rounded.Coffee) { it.navigateToSearchWithQuery("비건 카페") },
        HomeQuickAction("관광명소", Icons.Rounded.Whatshot) { it.navigateToSearchWithQuery("관광지") },
    )
    ValueAdded.GENERAL -> listOf(
        HomeQuickAction("식당", Icons.Rounded.Restaurant) { it.navigateToSearchWithQuery("식당") },
        HomeQuickAction("카페", Icons.Rounded.Coffee) { it.navigateToSearchWithQuery("카페") },
        HomeQuickAction("관광명소", Icons.Rounded.Whatshot) { it.navigateToSearchWithQuery("관광지") },
    )
}

@Composable
fun HomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ScanPangViewModel = viewModel(),
) {
    val context = LocalContext.current
    val onboardingPrefs = remember(context) { OnboardingPreferences(context) }
    val recentlyViewedStore = remember(context) { RecentlyViewedStore(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 미설정 사용자(예: 신규 데모) 는 GENERAL 로 본다 — 키블라 카드 미노출 + 일반 quick actions.
    val valueAdded = remember(context) { onboardingPrefs.getValueAdded() ?: ValueAdded.GENERAL }
    val displayName = remember(context) { onboardingPrefs.getDisplayName() }
    val quickActions = remember(valueAdded) { quickActionsFor(valueAdded) }
    val showQiblaCard = valueAdded == ValueAdded.HALAL

    // ── 우리 백엔드 통합 ─────────────────────────────────────────────────────
    // 기도시간·키블라는 API에서 실시간 fetch (DummyData 사용 안 함).
    LaunchedEffect(Unit) {
        viewModel.loadPrayerTimesAndQibla()
    }
    val prayerTimes by viewModel.prayerTimes.collectAsState()
    val qibla by viewModel.qibla.collectAsState()
    val qiblaText = qibla?.let { "키블라 방향: ${it.direction.toInt()}°" } ?: "키블라 방향: 292°"
    val nextPrayerText = prayerTimes
        ?.takeIf { it.next_prayer.isNotBlank() }
        ?.let { "다음 기도: ${it.next_prayer} ${it.next_prayer_time}" }
        ?: "기도 시간을 불러올 수 없습니다. 잠시 후 다시 시도해 주세요"

    // 현재 위치 — GPS + 역지오코딩 (권한 없거나 실패 시 fallback 문구).
    var locationText by remember { mutableStateOf("현재 위치를 가져오는 중...") }
    LaunchedEffect(Unit) {
        val hasPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            locationText = "위치 권한이 필요합니다"
            return@LaunchedEffect
        }
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                // SearchDefaultScreen 등 다른 화면이 outdoor 카테고리 거리 검색에 사용.
                viewModel.setUserLocation(loc.latitude, loc.longitude)
                try {
                    @Suppress("DEPRECATION")
                    val geocoder = Geocoder(context, Locale.KOREAN)
                    val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        val dong = addr.subLocality ?: addr.thoroughfare ?: ""
                        val detail = addr.featureName ?: ""
                        locationText = "현재 위치: ${dong} ${detail} 근처".trim()
                    } else {
                        locationText = "현재 위치: %.4f, %.4f".format(loc.latitude, loc.longitude)
                    }
                } catch (_: Exception) {
                    locationText = "현재 위치: %.4f, %.4f".format(loc.latitude, loc.longitude)
                }
            } else {
                locationText = "현재 위치를 확인할 수 없습니다"
            }
        }.addOnFailureListener {
            locationText = "위치 가져오기 실패"
        }
    }

    // ── 최근 본 매장 ─────────────────────────────────────────────────────────
    // Home 미리보기는 최신 2건만. 나머지는 RecentlyViewedListScreen 에서 본다.
    var recentlyViewed by remember {
        mutableStateOf(recentlyViewedStore.getAll().take(MAX_RECENT_HOME))
    }
    var hasMoreRecent by remember { mutableStateOf(recentlyViewedStore.getAll().size > MAX_RECENT_HOME) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val all = recentlyViewedStore.getAll()
                recentlyViewed = all.take(MAX_RECENT_HOME)
                hasMoreRecent = all.size > MAX_RECENT_HOME
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = ScanPangDimens.mainTabContentBottomInset),
        ) {
            HomeTopSection(
                navController = navController,
                displayName = displayName,
                showQiblaCard = showQiblaCard,
                qiblaText = qiblaText,
                nextPrayerText = nextPrayerText,
                locationText = locationText,
                quickActions = quickActions,
                recentlyViewed = recentlyViewed,
                showMoreRecent = hasMoreRecent,
            )
        }
    }
}

@Composable
private fun HomeTopSection(
    navController: NavController,
    displayName: String?,
    showQiblaCard: Boolean,
    qiblaText: String,
    nextPrayerText: String,
    locationText: String,
    quickActions: List<HomeQuickAction>,
    recentlyViewed: List<RecentlyViewedEntry>,
    showMoreRecent: Boolean,
) {
    val greetingLine = if (!displayName.isNullOrBlank()) {
        "안녕하세요, ${displayName}님!"
    } else {
        "안녕하세요!"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScanPangSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ScanPangDimens.homeSectionGap),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = ScanPangDimens.homeHeaderInset,
                    end = ScanPangDimens.homeHeaderInset,
                    top = ScanPangDimens.homeHeaderTop,
                    bottom = ScanPangDimens.homeHeaderInset,
                ),
            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
        ) {
            Text(
                text = "$greetingLine\n오늘 명동을 탐험해볼까요?",
                style = ScanPangType.homeGreeting,
                color = ScanPangColors.OnSurfaceStrong,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.AltRoute,
                    contentDescription = null,
                    modifier = Modifier.size(ScanPangDimens.icon20),
                    tint = ScanPangColors.OnSurfaceMuted,
                )
                Text(
                    text = locationText,
                    style = ScanPangType.meta13,
                    color = ScanPangColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ScanPangDimens.homeSectionGap),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ScanPangDimens.homeSearchBarHeight)
                    .clip(ScanPangShapes.radius14)
                    .background(ScanPangColors.Background)
                    .clickable { navController.navigate(AppRoutes.Search) }
                    .padding(horizontal = ScanPangSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Rounded.NearMe,
                    contentDescription = null,
                    modifier = Modifier.size(ScanPangDimens.tabIcon),
                    tint = ScanPangColors.OnSurfacePlaceholder,
                )
                Text(
                    text = "목적지 검색",
                    style = ScanPangType.searchPlaceholder,
                    color = ScanPangColors.OnSurfacePlaceholder,
                )
            }
        }

        if (showQiblaCard) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = ScanPangDimens.homeSectionGap),
            ) {
                QiblaSummaryCard(
                    qiblaText = qiblaText,
                    nextPrayerText = nextPrayerText,
                    onClick = { navController.navigate(AppRoutes.Qibla) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ScanPangDimens.homeSectionGap),
            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
        ) {
            quickActions.forEach { action ->
                QuickActionChip(
                    title = action.title,
                    icon = action.icon,
                    modifier = Modifier.weight(1f),
                    onClick = { action.navigate(navController) },
                )
            }
        }

        RecentlyViewedSection(
            recentlyViewed = recentlyViewed,
            showMore = showMoreRecent,
            onMoreClick = { navController.navigate(AppRoutes.RecentlyViewed) },
            onItemClick = { entry ->
                navController.navigate(entry.toDetailRoute())
            },
        )
    }
}

@Composable
private fun QiblaSummaryCard(
    qiblaText: String,
    nextPrayerText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ScanPangShapes.radius14)
            .background(ScanPangColors.PrimarySoft)
            .clickable(onClick = onClick)
            .padding(horizontal = ScanPangSpacing.lg, vertical = ScanPangDimens.homeQiblaRowVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Explore,
                    contentDescription = null,
                    modifier = Modifier.size(ScanPangDimens.tabIcon),
                    tint = ScanPangColors.Primary,
                )
                Text(
                    text = qiblaText,
                    style = ScanPangType.title14,
                    color = ScanPangColors.OnSurfaceStrong,
                )
            }
            Text(
                text = nextPrayerText,
                style = ScanPangType.caption12Medium,
                color = ScanPangColors.OnSurfaceMuted,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(ScanPangDimens.icon20),
            tint = ScanPangColors.Primary,
        )
    }
}

@Composable
private fun QuickActionChip(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(ScanPangShapes.radius14)
            .background(ScanPangColors.Background)
            .clickable(onClick = onClick)
            .padding(horizontal = ScanPangDimens.homeQuickChipHorizontal, vertical = ScanPangSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(ScanPangDimens.tabIcon),
            tint = ScanPangColors.Primary,
        )
        Text(
            text = title,
            style = ScanPangType.quickLabel12,
            color = ScanPangColors.OnSurfaceStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentlyViewedSection(
    recentlyViewed: List<RecentlyViewedEntry>,
    showMore: Boolean,
    onMoreClick: () -> Unit,
    onItemClick: (RecentlyViewedEntry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ScanPangSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "최근 본 장소",
                style = ScanPangType.sectionTitle16,
                color = ScanPangColors.OnSurfaceStrong,
            )
            // "더보기" 는 누적 기록이 미리보기(2건)보다 많을 때만 의미가 있어 그때만 노출.
            if (showMore) {
                Text(
                    text = "더보기",
                    style = ScanPangType.caption12Medium,
                    color = ScanPangColors.Primary,
                    modifier = Modifier.clickable(onClick = onMoreClick),
                )
            }
        }
        if (recentlyViewed.isEmpty()) {
            RecentlyViewedEmpty()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm)) {
                recentlyViewed.forEach { entry ->
                    RecentlyViewedRow(entry = entry, onClick = { onItemClick(entry) })
                }
            }
        }
    }
}

@Composable
private fun RecentlyViewedEmpty() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ScanPangShapes.radius14)
            .background(ScanPangColors.Background)
            .padding(horizontal = ScanPangSpacing.lg, vertical = ScanPangSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "아직 본 장소가 없어요",
            style = ScanPangType.body14Regular,
            color = ScanPangColors.OnSurfaceMuted,
        )
    }
}

private const val MAX_RECENT_HOME = 2
