package com.scanpang.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

enum class ScanPangMainTab {
    Home,
    Search,
    Saved,
    Profile,
}

/**
 * 하단 메인 탭: 컨테이너는 투명, 흰 pill + 중앙 FAB가 pill 위로 살짝 올라온 형태.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPangTabBar(
    selectedTab: ScanPangMainTab,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onSavedClick: () -> Unit,
    onProfileClick: () -> Unit,
    onExploreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(
                ScanPangDimens.bottomBarContainerHeight +
                    ScanPangDimens.tabBarFabCenterOffsetUp,
            )
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = ScanPangDimens.bottomPillHorizontalInset)
                .padding(bottom = ScanPangSpacing.xs)
                .height(ScanPangDimens.bottomPillHeight)
                .border(
                    ScanPangDimens.borderHairline,
                    ScanPangColors.OutlineSubtle,
                    ScanPangShapes.pill36,
                )
                .clip(ScanPangShapes.pill36)
                .background(ScanPangColors.Surface)
                .padding(ScanPangDimens.bottomPillInnerPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomTabSlot(
                label = "홈",
                icon = Icons.Rounded.Home,
                selected = selectedTab == ScanPangMainTab.Home,
                onClick = onHomeClick,
            )
            BottomTabSlot(
                label = "검색",
                icon = Icons.Rounded.Search,
                selected = selectedTab == ScanPangMainTab.Search,
                onClick = onSearchClick,
            )
            Spacer(modifier = Modifier.weight(1f))
            BottomTabSlot(
                label = "저장",
                icon = Icons.Rounded.BookmarkBorder,
                selected = selectedTab == ScanPangMainTab.Saved,
                onClick = onSavedClick,
            )
            BottomTabSlot(
                label = "내 정보",
                icon = Icons.Rounded.AccountCircle,
                selected = selectedTab == ScanPangMainTab.Profile,
                onClick = onProfileClick,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(ScanPangDimens.fabSize)
                .offset(y = -ScanPangDimens.tabBarFabCenterOffsetUp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Surface(
                modifier = Modifier.size(ScanPangDimens.fabSize),
                shape = CircleShape,
                color = ScanPangColors.Primary,
                shadowElevation = 0.dp,
                onClick = onExploreClick,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CropFree,
                        contentDescription = "AR 탐색",
                        modifier = Modifier.size(ScanPangDimens.fabIcon),
                        tint = Color.White,
                    )
                }
            }
            Text(
                text = "탐색",
                style = ScanPangType.tabLabelActive,
                color = ScanPangColors.Primary,
            )
        }
    }
}

@Composable
private fun RowScope.BottomTabSlot(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val activeColor = ScanPangColors.Primary
    val inactiveColor = ScanPangColors.OnSurfacePlaceholder
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(ScanPangDimens.tabIcon),
            tint = if (selected) activeColor else inactiveColor,
        )
        Text(
            text = label,
            style = if (selected) ScanPangType.tabLabelActive else ScanPangType.tabLabelInactive,
            color = if (selected) activeColor else inactiveColor,
        )
    }
}
