package com.scanpang.app.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.scanpang.app.data.DummyData
import com.scanpang.app.data.RestaurantPlace
import com.scanpang.app.data.SavedPlaceNavTarget
import com.scanpang.app.data.galleryModels
import com.scanpang.app.data.remote.GeneralRestaurantDetail
import com.scanpang.app.data.remote.ScanPangViewModel
import com.scanpang.app.data.toRestaurantPlace
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

/**
 * Figma: 식당 상세 (`290:1325`) — [DummyData.halalRestaurants] 첫 항목 연동
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RestaurantDetailScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    placeName: String = "",
    placeAddress: String = "",
) {
    val viewModel: ScanPangViewModel = viewModel()
    val apiRestaurants by viewModel.restaurants.collectAsState()
    val generalRestaurant by viewModel.generalRestaurant.collectAsState()
    val isLoading by viewModel.loading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadRestaurants()
        if (placeName.isNotBlank()) viewModel.loadGeneralRestaurant(placeName)
    }

    val rp: RestaurantPlace? = remember(generalRestaurant, apiRestaurants, placeName) {
        // 1순위: 일반 식당 RAG 데이터
        generalRestaurant?.toRestaurantPlace()
            // 2순위: 할랄 식당 API 데이터
            ?: run {
                val fromApi = apiRestaurants.map { it.toRestaurantPlace() }
                if (placeName.isNotBlank()) fromApi.firstOrNull { it.place.name == placeName }
                else fromApi.firstOrNull()
            }
    }

    // API 로딩 중이면 로딩 표시 (DummyData 플래시 방지)
    if (rp == null && isLoading) {
        Box(
            modifier = modifier.fillMaxSize().background(ScanPangColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = ScanPangColors.Primary)
        }
        return
    }

    // API 실패 시 DummyData 폴백
    val finalRp = rp
        ?: DummyData.halalRestaurants.firstOrNull { it.place.name == placeName }
        ?: DummyData.halalRestaurants.first()
    val place = finalRp.place
    val imageModels = remember(place.id) { place.galleryModels(defaultPlaceDetailGallery()) }
    val pagerState = rememberPagerState(pageCount = { imageModels.size })
    var fullscreenOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val bookmark = rememberDetailBookmark(
        placeId = place.id,
        placeName = place.name,
        category = place.category,
        distanceLine = "${place.subCategory.ifBlank { "한식" }} · ${place.distance}",
        tags = place.tags,
        target = SavedPlaceNavTarget.Restaurant,
    )

    if (fullscreenOpen) {
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
        DetailHeroPhotoPager(
            gallery = imageModels,
            pagerState = pagerState,
            onBack = { navController.popBackStack() },
            onFullscreenClick = { fullscreenOpen = true },
        )
        Column(
            modifier = Modifier.padding(horizontal = ScanPangDimens.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(ScanPangDimens.detailSectionSpacing),
        ) {
            Spacer(modifier = Modifier.height(ScanPangSpacing.md))
            DetailTitleBookmarkRow(
                title = place.name,
                bookmarked = bookmark.bookmarked,
                onBookmarkClick = bookmark.onToggle,
            )
            Text(
                text = "${place.subCategory.ifBlank { "한식" }} · ${place.distance}",
                style = ScanPangType.detailMetaSubtitle13,
                color = ScanPangColors.OnSurfaceMuted,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(ScanPangDimens.stackGap6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RestaurantHalalCategoryChip(label = finalRp.halalCategory)
                place.tags.take(2).forEach { tag ->
                    val icon = when {
                        tag.contains("인증") || tag.contains("살람") -> Icons.Rounded.Verified
                        else -> Icons.Rounded.Star
                    }
                    RestaurantTrustChip(text = tag, icon = icon)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        navController.navigate(AppRoutes.arNavMapRoute(place.name)) { launchSingleTop = true }
                    },
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
                    onClick = { context.openPhoneDialer(place.phone) },
                    modifier = Modifier.size(ScanPangDimens.detailCtaSide),
                    shape = ScanPangShapes.radius12,
                    border = BorderStroke(
                        ScanPangDimens.borderHairline,
                        ScanPangColors.OutlineSubtle,
                    ),
                    contentPadding = PaddingValues(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Phone,
                        contentDescription = "전화",
                        tint = ScanPangColors.OnSurfaceStrong,
                    )
                }
            }
            DetailVisitCardsHorizontalPager(cards = place.detailVisitCardsFromPlace())
            HorizontalDivider(color = ScanPangColors.OutlineSubtle)
            DetailSectionHeader(title = "소개")
            DetailIntroBody(text = place.description)
            if (finalRp.menuItems.isNotEmpty()) {
                DetailSectionHeader(title = "대표 메뉴")
                Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm)) {
                    finalRp.menuItems.forEach { m ->
                        DetailMenuPriceRow(name = m.name, price = m.price)
                    }
                }
            }
            if (finalRp.lastOrder.isNotBlank()) {
                Text(
                    text = "라스트오더 ${finalRp.lastOrder}",
                    style = ScanPangType.caption12Medium,
                    color = ScanPangColors.OnSurfaceMuted,
                )
            }
            DetailSectionHeader(title = "상세 정보")
            DetailInfoLine(Icons.Rounded.Place, "주소", place.address)
            DetailInfoLine(Icons.Rounded.Phone, "전화", place.phone)
            DetailInfoLine(Icons.Rounded.AccessTime, "영업시간", place.openHours)
            DetailContentBottomSpacer()
        }
    }
}

@Composable
private fun RestaurantHalalCategoryChip(label: String) {
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
private fun RestaurantTrustChip(
    text: String,
    icon: ImageVector,
) {
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
