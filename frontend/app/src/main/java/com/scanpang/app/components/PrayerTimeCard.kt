package com.scanpang.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

@Composable
fun PrayerTimeCard(
    subtitle: String,
    prayerNameTime: String,
    remainingLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScanPangShapes.radius16)
            .background(ScanPangColors.Primary)
            .padding(horizontal = ScanPangSpacing.xl, vertical = ScanPangSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
    ) {
        Text(
            text = subtitle,
            style = ScanPangType.link13,
            color = ScanPangColors.Surface,
        )
        Text(
            text = prayerNameTime,
            style = ScanPangType.prayerTimeLarge,
            color = ScanPangColors.Surface,
        )
        Text(
            text = remainingLabel,
            style = ScanPangType.link13,
            color = ScanPangColors.OnPrimaryMuted,
        )
    }
}
