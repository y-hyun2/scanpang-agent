package com.scanpang.app.components.ar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.TurnLeft
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.scanpang.app.ui.theme.ScanPangColors

/** AR 월드 공간에 떠다닐 배지의 방향 종류. */
enum class BadgeDirection { LEFT, RIGHT, STRAIGHT, DESTINATION }

fun BadgeDirection.icon(): ImageVector = when (this) {
    BadgeDirection.LEFT -> Icons.Rounded.TurnLeft
    BadgeDirection.RIGHT -> Icons.Rounded.TurnRight
    BadgeDirection.STRAIGHT -> Icons.Rounded.ArrowUpward
    BadgeDirection.DESTINATION -> Icons.Rounded.Place
}

/**
 * AR scene 안의 ViewNode2에 입혀 3D 평면 빌보드로 렌더되는 Figma 스타일 배지.
 * 원형 + Material 아이콘. hufs-cdp의 ArNavTurnBadge와 동일한 룩.
 *
 * @param isArrived 도착 상태. true이면 DESTINATION 배지가 초록 + CheckCircle로 전환.
 *                  다른 방향에서는 무시됨.
 */
@Composable
fun ArInWorldBadgeContent(
    direction: BadgeDirection,
    isArrived: Boolean = false,
) {
    // 도착 상태: 모든 방향에서 배경이 초록.
    // DESTINATION일 때는 아이콘도 CheckCircle로 교체. 그 외 방향은 원래 화살표 유지.
    val color = if (isArrived) ScanPangColors.Success else ScanPangColors.ArNavPrimaryBadge90
    val icon = if (direction == BadgeDirection.DESTINATION && isArrived)
        Icons.Rounded.CheckCircle
    else
        direction.icon()
    Surface(
        modifier = Modifier.size(120.dp),
        shape = CircleShape,
        color = color,
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = Color.White,
            )
        }
    }
}
