package com.scanpang.app.screens

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LocalAtm
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.scanpang.app.components.ScanPangBottomBar
import com.scanpang.app.components.ScanPangMainTab
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.ScanPangFigmaAssets
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

@Composable
fun HomeScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    vm: com.scanpang.app.data.HalalViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
) {
    val prayerTimes by vm.prayerTimes.collectAsState()
    val qibla by vm.qibla.collectAsState()

    val qiblaDir = qibla?.direction?.toInt() ?: 232
    val qiblaCompass = when {
        qiblaDir >= 292 -> "북서"; qiblaDir >= 247 -> "서"; qiblaDir >= 202 -> "남서"
        qiblaDir >= 157 -> "남"; qiblaDir >= 112 -> "남동"; qiblaDir >= 67 -> "동"
        qiblaDir >= 22 -> "북동"; else -> "북"
    }
    val qiblaText = "키블라 방향: $qiblaCompass ${qiblaDir}°"

    val nextPrayerText = run {
        val pt = prayerTimes ?: return@run "다음 기도: 로딩 중..."
        val now = java.util.Calendar.getInstance()
        val hhmm = "%02d:%02d".format(now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE))
        val prayers = listOf("Fajr" to pt.fajr, "Dhuhr" to pt.dhuhr, "Asr" to pt.asr, "Maghrib" to pt.maghrib, "Isha" to pt.isha)
        val next = prayers.firstOrNull { it.second > hhmm }
        if (next != null) "다음 기도: ${next.first} ${next.second}" else "다음 기도: Fajr ${pt.fajr} (내일)"
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScanPangColors.Surface,
        bottomBar = {
            ScanPangBottomBar(
                selectedTab = ScanPangMainTab.Home,
                onHomeClick = { },
                onSearchClick = { navController.navigate(AppRoutes.Search) { launchSingleTop = true } },
                onSavedClick = { navController.navigate(AppRoutes.Saved) { launchSingleTop = true } },
                onProfileClick = { navController.navigate(AppRoutes.Profile) { launchSingleTop = true } },
                onExploreClick = {
                    navController.navigate(AppRoutes.ArDefault) { launchSingleTop = true }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ScanPangColors.Surface)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            HomeTopSection(navController = navController)
            HomeBottomScrollSection()
        }
    }
}

@Composable
private fun HomeTopSection(navController: NavController) {
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
                text = "안녕하세요, 아미나님!\n오늘 명동을 탐험해볼까요?",
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
                    text = "현재 위치: 명동역 6번 출구 근처",
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ScanPangDimens.homeSectionGap),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ScanPangShapes.radius14)
                    .background(ScanPangColors.PrimarySoft)
                    .clickable { navController.navigate(AppRoutes.Qibla) }
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = ScanPangDimens.homeSectionGap),
            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
        ) {
            QuickActionChip(
                title = "할랄 식당",
                icon = Icons.Rounded.Restaurant,
                modifier = Modifier.weight(1f),
            )
            QuickActionChip(
                title = "기도실",
                icon = Icons.Rounded.Mosque,
                modifier = Modifier.weight(1f),
            )
            QuickActionChip(
                title = "실시간 번역",
                icon = Icons.Rounded.Translate,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickActionChip(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(ScanPangShapes.radius14)
            .background(ScanPangColors.Background)
            .clickable(enabled = false) { }
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
private fun HomeBottomScrollSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScanPangDimens.screenHorizontal)
            .padding(top = ScanPangSpacing.lg, bottom = ScanPangDimens.bottomSectionBottom),
        verticalArrangement = Arrangement.spacedBy(ScanPangDimens.listBlockGap),
    ) {
        RecentSection()
        RecommendSection()
    }
}

@Composable
private fun RecentSection() {
    Column(verticalArrangement = Arrangement.spacedBy(ScanPangDimens.sectionHeaderGap)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "최근 길찾기",
                style = ScanPangType.sectionTitle,
                color = ScanPangColors.OnSurfaceStrong,
            )
            Text(
                text = "더보기",
                style = ScanPangType.link13,
                color = ScanPangColors.Primary,
                modifier = Modifier.clickable(enabled = false) { },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ScanPangDimens.sectionHeaderGap)) {
            RecentRow(
                title = "롯데백화점 명동본점",
                subtitle = "도보 3분 · 오늘 14:20",
                icon = Icons.Rounded.LocalMall,
            )
            RecentRow(
                title = "CU 뉴명동YWCA점 ATM",
                subtitle = "도보 8분 · 어제 09:45",
                icon = Icons.Rounded.LocalAtm,
            )
        }
    }
}

@Composable
private fun RecentRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ScanPangShapes.radius14)
            .background(ScanPangColors.Background)
            .clickable(enabled = false) { }
            .padding(horizontal = ScanPangSpacing.lg, vertical = ScanPangDimens.recentRowVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(ScanPangDimens.recentIconCircle)
                .clip(CircleShape)
                .background(ScanPangColors.PrimarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ScanPangDimens.tabIcon),
                tint = ScanPangColors.Primary,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ScanPangDimens.homeMetaGap),
        ) {
            Text(
                text = title,
                style = ScanPangType.title14,
                color = ScanPangColors.OnSurfaceStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = ScanPangType.caption12,
                color = ScanPangColors.OnSurfaceMuted,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(ScanPangDimens.chevronEnd),
            tint = ScanPangColors.OnSurfacePlaceholder,
        )
    }
}

@Composable
private fun RecommendSection() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(ScanPangDimens.recommendSectionGap)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "추천 장소",
                style = ScanPangType.sectionTitle,
                color = ScanPangColors.OnSurfaceStrong,
            )
            Text(
                text = "더보기",
                style = ScanPangType.link13,
                color = ScanPangColors.Primary,
                modifier = Modifier.clickable(enabled = false) { },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ScanPangDimens.listBlockGap),
        ) {
            PlaceCard(
                imageUrl = ScanPangFigmaAssets.HomePlaceCard1,
                title = "봉추찜닭 명동점",
                subtitle = "할랄 인증 · 도보 5분",
                context = context,
                modifier = Modifier.weight(1f),
            )
            PlaceCard(
                imageUrl = ScanPangFigmaAssets.HomePlaceCard2,
                title = "남산타워",
                subtitle = "도보 15분 · 인기 명소",
                context = context,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlaceCard(
    imageUrl: String,
    title: String,
    subtitle: String,
    context: android.content.Context,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(ScanPangShapes.radius16)
            .border(
                ScanPangDimens.borderHairline,
                ScanPangColors.OutlineSubtle,
                ScanPangShapes.radius16,
            )
            .background(ScanPangColors.Surface),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ScanPangDimens.placeImageHeight)
                .clip(
                    RoundedCornerShape(
                        topStart = ScanPangDimens.cardRadiusLarge,
                        topEnd = ScanPangDimens.cardRadiusLarge,
                    ),
                ),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier.padding(ScanPangDimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
        ) {
            Text(
                text = title,
                style = ScanPangType.title14,
                color = ScanPangColors.OnSurfaceStrong,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = ScanPangType.caption12,
                color = ScanPangColors.OnSurfaceMuted,
            )
        }
    }
}
