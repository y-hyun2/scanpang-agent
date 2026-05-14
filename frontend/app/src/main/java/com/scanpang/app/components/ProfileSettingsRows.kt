package com.scanpang.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scanpang.app.ui.theme.ScanPangColors
import kotlin.math.roundToInt
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

/** Switch 전체를 비율 유지(원형 썸)로 축소. scaleX≠scaleY 는 썸이 타원으로 찌그러지므로 금지. */
private const val SettingsSwitchScale = 0.72f

/** Switch 를 축소하면서 **레이아웃 높이·너비도 함께 줄인다**. (graphicsLayer 만 쓰면 행 높이가 기본 Switch 만큼 남음) */
private fun Modifier.settingsSwitchScale(scale: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val outW = (placeable.width * scale).roundToInt().coerceAtLeast(1)
        val outH = (placeable.height * scale).roundToInt().coerceAtLeast(1)
        layout(outW, outH) {
            // 전체 크기 위젯을 축소된 슬롯 안에서 가운데에 두고 스케일해야 행의 CenterVertically 와 광학적 중앙이 맞는다.
            val px = ((outW - placeable.width) / 2f).roundToInt()
            val py = ((outH - placeable.height) / 2f).roundToInt()
            placeable.placeWithLayer(px, py) {
                this.scaleX = scale
                this.scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
        }
    },
)

@Composable
fun ProfileSettingsSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = ScanPangType.sectionLabelSemiBold13,
        color = ScanPangColors.OnSurfaceMuted,
        modifier = modifier,
    )
}

@Composable
fun ProfileSettingsCard(
    modifier: Modifier = Modifier,
    bordered: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScanPangShapes.radius16)
            .border(
                width = if (bordered) ScanPangDimens.borderHairline else 0.dp,
                color = if (bordered) ScanPangColors.OutlineSubtle else Color.Transparent,
                shape = ScanPangShapes.radius16,
            )
            .background(ScanPangColors.Surface),
    ) {
        content()
    }
}

@Composable
fun ProfileSettingsRow(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    labelColor: Color = ScanPangColors.OnSurfaceStrong,
    showDividerBelow: Boolean,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = ScanPangSpacing.lg, vertical = ScanPangSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ScanPangDimens.settingsLeadingIcon),
                tint = iconTint,
            )
            Text(
                text = label,
                style = ScanPangType.body15Medium,
                color = labelColor,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(ScanPangDimens.tabIcon),
                tint = ScanPangColors.OnSurfacePlaceholder,
            )
        }
        if (showDividerBelow) {
            HorizontalDivider(
                thickness = ScanPangDimens.borderHairline,
                color = ScanPangColors.OutlineSubtle,
            )
        }
    }
}

/**
 * 토글 형태(우측 Switch)의 설정 행. 알림 설정 화면과 프로필의 "TTS 음성 안내" 행에서 사용한다.
 * - 토글은 [Switch] 를 눌렀을 때만 바뀐다. (라벨·아이콘 영역 탭으로는 바뀌지 않음)
 * - 한 줄(부제 없음)일 때는 [ProfileSettingsRow] 와 같은 레이아웃·패딩으로 높이를 맞춘다.
 * - [subtitle] 이 있으면 알림 설정 등 두 줄 행 — 세로 패딩만 조금 넓힌다.
 * - [contentPaddingVertical] 을 넘기면 세로 패딩을 직접 지정할 수 있다.
 */
@Composable
fun ProfileSettingsToggleRow(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDividerBelow: Boolean,
    subtitle: String? = null,
    labelColor: Color = ScanPangColors.OnSurfaceStrong,
    contentPaddingVertical: Dp? = null,
) {
    val verticalPad = contentPaddingVertical
        ?: if (!subtitle.isNullOrBlank()) 14.dp else ScanPangSpacing.md
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ScanPangSpacing.lg, vertical = verticalPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ScanPangDimens.settingsLeadingIcon),
                tint = iconTint,
            )
            if (!subtitle.isNullOrBlank()) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = label,
                        style = ScanPangType.body15Medium,
                        color = labelColor,
                    )
                    Text(
                        text = subtitle,
                        style = ScanPangType.caption12,
                        color = ScanPangColors.OnSurfacePlaceholder,
                    )
                }
            } else {
                Text(
                    text = label,
                    style = ScanPangType.body15Medium,
                    color = labelColor,
                    modifier = Modifier.weight(1f),
                )
            }
            // 쉐브론 행과 동일한 24dp 슬롯에 스위치를 세로 가운데 정렬 (축소 레이아웃만 쓰면 행 높이·정렬이 어긋난다)
            Box(
                modifier = Modifier
                    .height(ScanPangDimens.tabIcon)
                    .wrapContentWidth(align = Alignment.End),
                contentAlignment = Alignment.Center,
            ) {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.settingsSwitchScale(SettingsSwitchScale),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ScanPangColors.Primary,
                        checkedBorderColor = ScanPangColors.Primary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = ScanPangColors.OutlineSubtle,
                        uncheckedBorderColor = ScanPangColors.OnSurfacePlaceholder.copy(alpha = 0.55f),
                    ),
                )
            }
        }
        if (showDividerBelow) {
            HorizontalDivider(
                thickness = ScanPangDimens.borderHairline,
                color = ScanPangColors.OutlineSubtle,
            )
        }
    }
}
