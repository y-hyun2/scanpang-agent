package com.scanpang.app.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanpang.app.data.remote.ScanPangViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

/**
 * Figma: 기도실 상세 (`290:1425`)
 */
@Composable
fun PrayerRoomDetailScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ScanPangViewModel = viewModel(),
) {
    val prayerRooms by viewModel.prayerRooms.collectAsState()
    val room = prayerRooms.firstOrNull()

    val roomName = room?.name ?: "명동 공중기도실"
    val roomSubtitle = room?.let {
        buildString {
            if (it.distance_m > 0) append("${it.distance_m}m")
            if (it.floor.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(it.floor) }
        }.ifEmpty { it.address }
    } ?: "도보 3분 · 지하 1층"
    val roomAddress = room?.address ?: "서울특별시 중구 명동8나길 27 지하 1층"
    val roomHours = room?.open_hours ?: "10:00 – 20:00"
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
                .height(ScanPangDimens.detailPhotoHeroHeight)
                .background(ScanPangColors.DetailHeroImagePlaceholder),
        ) {
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
        }
        Column(
            modifier = Modifier.padding(horizontal = ScanPangDimens.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(ScanPangDimens.detailSectionSpacing),
        ) {
            Spacer(modifier = Modifier.height(ScanPangSpacing.md))
            Text(
                text = roomName,
                style = ScanPangType.detailRestaurantTitle24,
                color = ScanPangColors.OnSurfaceStrong,
            )
            Text(
                text = roomSubtitle,
                style = ScanPangType.detailMetaSubtitle13,
                color = ScanPangColors.OnSurfaceMuted,
            )
            Surface(
                shape = ScanPangShapes.badge6,
                color = ScanPangColors.TrustPillBackground,
                border = BorderStroke(
                    ScanPangDimens.borderHairline,
                    ScanPangColors.OutlineSubtle,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = ScanPangSpacing.sm,
                        vertical = ScanPangDimens.badgePadVertical,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(ScanPangDimens.icon14),
                        tint = ScanPangColors.StatusOpen,
                    )
                    Text(
                        text = "이용 가능",
                        style = ScanPangType.badge9SemiBold,
                        color = ScanPangColors.TrustPillText,
                    )
                }
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
            HorizontalDivider(color = ScanPangColors.OutlineSubtle)
            Text(
                text = "소개",
                style = ScanPangType.detailSectionTitle15,
                color = ScanPangColors.OnSurfaceStrong,
            )
            Text(
                text = room?.notes?.ifEmpty { null }
                    ?: "명동 거리 중심부에 위치한 무슬림 방문객을 위한 기도 공간입니다. 세정 시설과 키블라 표시가 준비되어 있습니다.",
                style = ScanPangType.detailIntro13,
                color = ScanPangColors.OnSurfaceMuted,
            )
            room?.facilities?.let { fac ->
                val facilityList = buildList {
                    if (fac.wudu) add("세정시설(우두)")
                    if (fac.prayer_mat) add("기도 매트")
                    if (fac.gender_separation) add("남녀 분리")
                    if (fac.quran_available) add("꾸란 비치")
                }
                if (facilityList.isNotEmpty()) {
                    Text(
                        text = "시설 안내",
                        style = ScanPangType.detailSectionTitle15,
                        color = ScanPangColors.OnSurfaceStrong,
                    )
                    Text(
                        text = facilityList.joinToString(" · "),
                        style = ScanPangType.detailIntro13,
                        color = ScanPangColors.OnSurfaceMuted,
                    )
                }
            }
            Text(
                text = "상세 정보",
                style = ScanPangType.detailSectionTitle15,
                color = ScanPangColors.OnSurfaceStrong,
            )
            PrayerDetailInfoLine(
                icon = Icons.Rounded.Place,
                label = "주소",
                value = roomAddress,
            )
            PrayerDetailInfoLine(
                icon = Icons.Rounded.AccessTime,
                label = "이용 시간",
                value = roomHours,
            )
            Spacer(modifier = Modifier.height(ScanPangDimens.detailContentBottomPad))
        }
    }
}

@Composable
private fun PrayerDetailInfoLine(
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
