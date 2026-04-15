package com.scanpang.app.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanpang.app.data.remote.ScanPangViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.scanpang.app.data.SavedPlaceEntry
import com.scanpang.app.data.SavedPlaceNavTarget
import com.scanpang.app.data.SavedPlacesStore
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.ScanPangFigmaAssets
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

private const val DefaultPlaceId = "place_halal_garden_myeongdong"
private const val DefaultPlaceName = "할랄가든 명동점"
private const val DefaultPlaceCategory = "할랄 식당"
private const val DefaultPlaceDistanceLine = "명동 · 도보 2분"
private val DefaultPlaceTags = listOf("할랄 인증", "방문자 추천")

/**
 * Figma: 식당 상세 (`290:1325`)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RestaurantDetailScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ScanPangViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val gallery = ScanPangFigmaAssets.RestaurantDetailGallery
    val pagerState = rememberPagerState(pageCount = { gallery.size })
    var fullscreenOpen by remember { mutableStateOf(false) }
    val savedStore = remember { SavedPlacesStore(context) }

    val restaurants by viewModel.restaurants.collectAsState()
    val restaurant = restaurants.firstOrNull()

    val DetailPlaceId = restaurant?.restaurant_id ?: DefaultPlaceId
    val DetailPlaceName = restaurant?.name_ko ?: DefaultPlaceName
    val DetailPlaceCategory = restaurant?.cuisine?.ifEmpty { DefaultPlaceCategory } ?: DefaultPlaceCategory
    val DetailPlaceDistanceLine = restaurant?.let { "${it.cuisine} · ${it.distance_m}m" } ?: DefaultPlaceDistanceLine
    val DetailPlaceTags = DefaultPlaceTags

    var bookmarked by remember { mutableStateOf(savedStore.isSaved(DetailPlaceId)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                bookmarked = savedStore.isSaved(DetailPlaceId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (fullscreenOpen) {
        Dialog(
            onDismissRequest = { fullscreenOpen = false },
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
                IconButton(
                    onClick = { fullscreenOpen = false },
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
        modifier = modifier
            .fillMaxSize()
            .background(ScanPangColors.Surface)
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        Box(
            modifier = Modifier
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
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(ScanPangSpacing.sm),
            ) {
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
            IconButton(
                onClick = { fullscreenOpen = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(ScanPangSpacing.sm),
            ) {
                Surface(
                    shape = CircleShape,
                    color = ScanPangColors.ArOverlayWhite93,
                    shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Fullscreen,
                        contentDescription = "전체 화면",
                        modifier = Modifier.padding(ScanPangSpacing.sm),
                        tint = ScanPangColors.OnSurfaceStrong,
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(ScanPangSpacing.lg),
                shape = ScanPangShapes.badge6,
                color = ScanPangColors.DetailImageCountScrim,
            ) {
                Text(
                    text = "${pagerState.currentPage + 1}/${gallery.size}",
                    modifier = Modifier.padding(
                        horizontal = ScanPangSpacing.sm,
                        vertical = ScanPangDimens.badgePadVertical,
                    ),
                    style = ScanPangType.detailImageCount9,
                    color = Color.White,
                )
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = ScanPangDimens.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(ScanPangDimens.detailSectionSpacing),
        ) {
            Spacer(modifier = Modifier.height(ScanPangSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
            ) {
                Text(
                    text = DetailPlaceName,
                    style = ScanPangType.detailRestaurantTitle24,
                    color = ScanPangColors.OnSurfaceStrong,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        if (bookmarked) {
                            savedStore.remove(DetailPlaceId)
                            bookmarked = false
                            Toast.makeText(context, "저장이 해제되었습니다", Toast.LENGTH_SHORT).show()
                        } else {
                            savedStore.save(
                                SavedPlaceEntry(
                                    id = DetailPlaceId,
                                    name = DetailPlaceName,
                                    category = DetailPlaceCategory,
                                    distanceLine = DetailPlaceDistanceLine,
                                    tags = DetailPlaceTags,
                                    target = SavedPlaceNavTarget.Restaurant,
                                ),
                            )
                            bookmarked = true
                            Toast.makeText(context, "저장되었습니다", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) {
                    Icon(
                        imageVector = if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (bookmarked) "저장됨" else "저장",
                        tint = if (bookmarked) {
                            ScanPangColors.Primary
                        } else {
                            ScanPangColors.OnSurfacePlaceholder
                        },
                    )
                }
            }
            Text(
                text = DetailPlaceDistanceLine,
                style = ScanPangType.detailMetaSubtitle13,
                color = ScanPangColors.OnSurfaceMuted,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(ScanPangDimens.stackGap6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RestaurantTrustChip(text = "할랄 인증", icon = Icons.Rounded.Verified)
                RestaurantTrustChip(text = "방문자 추천", icon = Icons.Rounded.Star)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        navController.navigate(AppRoutes.ArNavMap) { launchSingleTop = true }
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
                    Text(
                        text = "길안내 시작",
                        style = ScanPangType.body15Medium,
                    )
                }
                OutlinedButton(
                    onClick = { },
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
            Surface(
                shape = ScanPangShapes.detailVisitCard,
                color = ScanPangColors.DetailVisitOpenSurface,
                border = BorderStroke(
                    ScanPangDimens.borderHairline,
                    ScanPangColors.DetailVisitOpenBorder,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(ScanPangSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = ScanPangColors.StatusOpen,
                        modifier = Modifier.size(ScanPangDimens.icon18),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(ScanPangDimens.icon5)) {
                        Text(
                            text = "지금 방문 가능",
                            style = ScanPangType.title14,
                            color = ScanPangColors.OnSurfaceStrong,
                        )
                        Text(
                            text = restaurant?.open_hours?.let { "영업 중 · $it" } ?: "영업 중 · 월–일 11:00–22:00",
                            style = ScanPangType.caption12Medium,
                            color = ScanPangColors.OnSurfaceMuted,
                        )
                    }
                }
            }
            HorizontalDivider(color = ScanPangColors.OutlineSubtle)
            Text(
                text = "소개",
                style = ScanPangType.detailSectionTitle15,
                color = ScanPangColors.OnSurfaceStrong,
            )
            Text(
                text = restaurant?.short_description_ko?.ifEmpty { null }
                    ?: "명동 한복판에서 한우와 전통 한식을 할랄 기준으로 즐길 수 있는 공간입니다. 가족 단위 방문에 적합합니다.",
                style = ScanPangType.detailIntro13,
                color = ScanPangColors.OnSurfaceMuted,
            )
            Text(
                text = "대표 메뉴",
                style = ScanPangType.detailSectionTitle15,
                color = ScanPangColors.OnSurfaceStrong,
            )
            if (restaurant != null && restaurant.menu_examples.isNotEmpty()) {
                restaurant.menu_examples.forEach { menu ->
                    DetailMenuLine(
                        name = menu.name_ko,
                        price = if (menu.price_krw > 0) "${String.format("%,d", menu.price_krw)}원" else "",
                    )
                }
            } else {
                DetailMenuLine(name = "한우 불고기 정식", price = "15,000원")
                DetailMenuLine(name = "된장찌개 세트", price = "9,000원")
            }
            Text(
                text = "상세 정보",
                style = ScanPangType.detailSectionTitle15,
                color = ScanPangColors.OnSurfaceStrong,
            )
            DetailInfoLine(
                icon = Icons.Rounded.Place,
                label = "주소",
                value = restaurant?.address ?: "서울특별시 중구 명동길 26",
            )
            DetailInfoLine(
                icon = Icons.Rounded.Phone,
                label = "전화",
                value = restaurant?.phone ?: "02-1234-5678",
            )
            DetailInfoLine(
                icon = Icons.Rounded.AccessTime,
                label = "영업시간",
                value = restaurant?.open_hours ?: "11:00 – 22:00 (연중무휴)",
            )
            Spacer(modifier = Modifier.height(ScanPangDimens.detailContentBottomPad))
        }
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

@Composable
private fun DetailMenuLine(
    name: String,
    price: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ScanPangShapes.detailMenuRow)
            .background(ScanPangColors.DetailMenuRowBackground)
            .padding(
                horizontal = ScanPangSpacing.md,
                vertical = ScanPangSpacing.sm,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = ScanPangType.caption12Medium,
            color = ScanPangColors.OnSurfaceStrong,
        )
        Text(
            text = price,
            style = ScanPangType.detailMenuPrice14,
            color = ScanPangColors.OnSurfaceStrong,
        )
    }
}

@Composable
private fun DetailInfoLine(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(ScanPangDimens.icon18),
            tint = ScanPangColors.OnSurfaceMuted,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ScanPangDimens.icon5),
        ) {
            Text(
                text = label,
                style = ScanPangType.meta11Medium,
                color = ScanPangColors.OnSurfacePlaceholder,
            )
            Text(
                text = value,
                style = ScanPangType.detailIntro13,
                color = ScanPangColors.OnSurfaceStrong,
            )
        }
    }
}
