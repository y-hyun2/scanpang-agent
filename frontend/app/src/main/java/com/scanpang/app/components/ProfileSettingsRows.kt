package com.scanpang.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

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
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScanPangShapes.radius16)
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
                modifier = Modifier.size(ScanPangDimens.icon18),
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
