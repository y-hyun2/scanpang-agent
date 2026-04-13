package com.scanpang.app.screens.home

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanpang.app.data.HalalViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.scanpang.app.navigation.HomeRoutes
import com.scanpang.app.navigation.TabRoutes
import com.scanpang.app.navigation.navigateToTab
import com.scanpang.app.ui.theme.ScanPangElevation
import com.scanpang.app.ui.theme.ScanPangRadius
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangThemeAccessor
import com.scanpang.app.ui.theme.ScanPangTypeScale

private object HomeFigma {
    const val CardImage1 =
        "https://www.figma.com/api/mcp/asset/39c6975b-8b44-4928-99ce-953b3d8b57ca"
    const val CardImage2 =
        "https://www.figma.com/api/mcp/asset/6a921240-8b75-4d26-bc66-4c3022a3acf1"
    const val QiblaChevron =
        "https://www.figma.com/api/mcp/asset/fe340857-a612-4452-8682-36d5bc903601"
}

private val Radius14 = ScanPangRadius.Lg - 2.dp

@Composable
fun HomeScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    halalVm: HalalViewModel = viewModel(),
) {
    val c = ScanPangThemeAccessor.colors
    val scroll = rememberScrollState()
    val prayerTimes by halalVm.prayerTimes.collectAsState()
    val qibla by halalVm.qibla.collectAsState()

    // 키블라 방향 텍스트
    val qiblaDir = qibla?.direction?.toInt() ?: 232
    val qiblaCompass = when {
        qiblaDir >= 292 -> "북서"
        qiblaDir >= 247 -> "서"
        qiblaDir >= 202 -> "남서"
        qiblaDir >= 157 -> "남"
        qiblaDir >= 112 -> "남동"
        qiblaDir >= 67 -> "동"
        qiblaDir >= 22 -> "북동"
        else -> "북"
    }
    val qiblaText = "키블라 방향: $qiblaCompass ${qiblaDir}°"

    // 다음 기도 시간
    val nextPrayerText = run {
        val pt = prayerTimes ?: return@run "다음 기도: 로딩 중..."
        val now = java.util.Calendar.getInstance()
        val hhmm = "%02d:%02d".format(now.get(java.util.Calendar.HOUR_OF_DAY), now.get(java.util.Calendar.MINUTE))
        val prayers = listOf("Fajr" to pt.fajr, "Dhuhr" to pt.dhuhr, "Asr" to pt.asr, "Maghrib" to pt.maghrib, "Isha" to pt.isha)
        val next = prayers.firstOrNull { it.second > hhmm }
        if (next != null) "다음 기도: ${next.first} ${next.second}" else "다음 기도: Fajr ${pt.fajr} (내일)"
    }

    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(scroll),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScanPangSpacing.S4),
        ) {
            Spacer(Modifier.height(ScanPangSpacing.S2 + 2.dp))
            Text(
                text = "안녕하세요, 아미나님!\n오늘 명동을 탐험해볼까요?",
                fontSize = 22.sp,
                fontWeight = ScanPangTypeScale.W700,
                lineHeight = (22f * ScanPangTypeScale.LineTight).sp,
                color = c.textPrimary,
            )
            Spacer(Modifier.height(ScanPangSpacing.S2))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.S1),
            ) {
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "현재 위치: 명동역 6번 출구 근처",
                    fontSize = ScanPangTypeScale.Sm,
                    color = c.textSecondary,
                )
            }
            Spacer(Modifier.height(ScanPangSpacing.S1))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(c.surfaceSubtle, RoundedCornerShape(Radius14))
                    .clickable {
                        navController.navigateToTab(TabRoutes.SEARCH_TAB)
                    }
                    .padding(horizontal = ScanPangSpacing.S4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.S2),
            ) {
                Icon(
                    Icons.Filled.NearMe,
                    contentDescription = null,
                    tint = c.iconMuted,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    "목적지 검색",
                    fontSize = ScanPangTypeScale.Base,
                    fontWeight = ScanPangTypeScale.W500,
                    color = c.textPlaceholder,
                )
            }
            Spacer(Modifier.height(ScanPangSpacing.S1))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.backgroundOverlay, RoundedCornerShape(Radius14))
                    .clickable { navController.navigate(HomeRoutes.QIBLA) }
                    .padding(
                        horizontal = ScanPangSpacing.S4,
                        vertical = ScanPangSpacing.S3 + 2.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.S3),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.S1)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.S1),
                    ) {
                        Icon(
                            Icons.Filled.Explore,
                            contentDescription = null,
                            tint = c.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            qiblaText,
                            fontSize = 14.sp,
                            fontWeight = ScanPangTypeScale.W600,
                            color = c.textPrimary,
                        )
                    }
                    Text(
                        nextPrayerText,
                        fontSize = 12.sp,
                        fontWeight = ScanPangTypeScale.W500,
                        color = c.textSecondary,
                    )
                }
                AsyncImage(
                    model = HomeFigma.QiblaChevron,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.height(ScanPangSpacing.S1))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.S3),
            ) {
                QuickAction(
                    icon = { Icon(Icons.Filled.Restaurant, null, tint = c.primary, modifier = Modifier.size(24.dp)) },
                    label = "할랄 식당",
                    onClick = { navController.navigate(HomeRoutes.NEARBY_HALAL) },
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    icon = { Icon(Icons.Filled.Mosque, null, tint = c.primary, modifier = Modifier.size(24.dp)) },
                    label = "기도실",
                    onClick = { navController.navigate(HomeRoutes.NEARBY_PRAYER) },
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    icon = { Icon(Icons.Filled.Translate, null, tint = c.iconMuted, modifier = Modifier.size(24.dp)) },
                    label = "실시간 번역",
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = ScanPangSpacing.S5,
                    end = ScanPangSpacing.S5,
                    top = ScanPangSpacing.S4,
                    bottom = ScanPangSpacing.S2,
                ),
            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.S3 + 2.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "최근 길찾기",
                    fontSize = ScanPangTypeScale.Md,
                    fontWeight = ScanPangTypeScale.W700,
                    color = c.textPrimary,
                )
                Text(
                    "더보기",
                    modifier = Modifier.clickable { navController.navigateToTab(TabRoutes.SEARCH_TAB) },
                    fontSize = ScanPangTypeScale.Sm,
                    fontWeight = ScanPangTypeScale.W500,
                    color = c.primary,
                )
            }
            RecentRow(
                title = "롯데백화점 명동본점",
                meta = "도보 3분 · 오늘 14:20",
                icon = {
                    Icon(Icons.Filled.LocalMall, null, tint = c.primary, modifier = Modifier.size(24.dp))
                },
                onClick = {
                    navController.navigate(
                        "${HomeRoutes.RESTAURANT_DETAIL}/${Uri.encode("롯데백화점 명동본점")}",
                    )
                },
            )
            RecentRow(
                title = "CU 뉴명동YWCA점 ATM",
                meta = "도보 8분 · 어제 09:45",
                icon = {
                    Icon(Icons.Filled.LocalAtm, null, tint = c.primary, modifier = Modifier.size(24.dp))
                },
                onClick = {
                    navController.navigate(
                        "${HomeRoutes.RESTAURANT_DETAIL}/${Uri.encode("CU 뉴명동YWCA점 ATM")}",
                    )
                },
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "추천 장소",
                    fontSize = ScanPangTypeScale.Md,
                    fontWeight = ScanPangTypeScale.W700,
                    color = c.textPrimary,
                )
                Text(
                    "더보기",
                    modifier = Modifier.clickable { navController.navigate(HomeRoutes.NEARBY_HALAL) },
                    fontSize = ScanPangTypeScale.Sm,
                    fontWeight = ScanPangTypeScale.W500,
                    color = c.primary,
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.S3),
            ) {
                PlaceCard(
                    imageUrl = HomeFigma.CardImage1,
                    title = "봉추찜닭 명동점",
                    meta = "할랄 인증 · 도보 5분",
                    onClick = {
                        navController.navigate(
                            "${HomeRoutes.RESTAURANT_DETAIL}/${Uri.encode("봉추찜닭 명동점")}",
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                PlaceCard(
                    imageUrl = HomeFigma.CardImage2,
                    title = "남산타워",
                    meta = "도보 15분 · 인기 명소",
                    onClick = {
                        navController.navigate(
                            "${HomeRoutes.RESTAURANT_DETAIL}/${Uri.encode("남산타워")}",
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(ScanPangSpacing.S4))
    }
}

@Composable
private fun QuickAction(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = ScanPangThemeAccessor.colors
    Column(
        modifier
            .alpha(if (enabled) 1f else 0.55f)
            .background(c.surfaceSubtle, RoundedCornerShape(Radius14))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = ScanPangSpacing.S3,
                vertical = ScanPangSpacing.S4,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.S2),
    ) {
        Row(
            Modifier.height(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { icon() }
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = ScanPangTypeScale.W600,
            color = if (enabled) c.textPrimary else c.textSecondary,
        )
    }
}

@Composable
private fun RecentRow(
    title: String,
    meta: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val c = ScanPangThemeAccessor.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surfaceSubtle, RoundedCornerShape(Radius14))
            .clickable(onClick = onClick)
            .padding(
                horizontal = ScanPangSpacing.S4,
                vertical = ScanPangSpacing.S3 + 2.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.S3),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(c.backgroundOverlay, RoundedCornerShape(ScanPangRadius.Xl + 2.dp)),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = ScanPangTypeScale.W600, color = c.textPrimary)
            Text(meta, fontSize = 12.sp, color = c.textSecondary)
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = c.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PlaceCard(
    imageUrl: String,
    title: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ScanPangThemeAccessor.colors
    Column(
        modifier
            .shadow(ScanPangElevation.Sm, RoundedCornerShape(ScanPangRadius.Lg))
            .background(c.surface, RoundedCornerShape(ScanPangRadius.Lg))
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(ScanPangRadius.Lg))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(ScanPangSpacing.S20),
            contentScale = ContentScale.Crop,
        )
        Column(
            Modifier.padding(ScanPangSpacing.S3),
            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.S1),
        ) {
            Text(title, fontSize = 14.sp, fontWeight = ScanPangTypeScale.W600, color = c.textPrimary)
            Text(meta, fontSize = 12.sp, color = c.textSecondary)
        }
    }
}
