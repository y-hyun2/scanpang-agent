package com.scanpang.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.scanpang.app.i18n.LocalStrings
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
        if (remainingLabel.isNotBlank()) {
            Text(
                text = remainingLabel,
                style = ScanPangType.link13,
                color = ScanPangColors.OnPrimaryMuted,
            )
        }
    }
}

@Composable
fun PrayerTimeListCard(
    times: List<Pair<String, String>>,
    nextPrayer: String,
    modifier: Modifier = Modifier,
) {
    val s = LocalStrings.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ScanPangShapes.radius16)
            .background(ScanPangColors.Background),
    ) {
        Text(
            text = s.prayerTimeTitle,
            style = ScanPangType.caption12Medium,
            color = ScanPangColors.OnSurfaceMuted,
            modifier = Modifier.padding(
                horizontal = ScanPangSpacing.xl,
                vertical = ScanPangSpacing.md,
            ),
        )
        HorizontalDivider(color = ScanPangColors.OutlineSubtle)
        times.forEachIndexed { index, (name, time) ->
            val isNext = name.equals(nextPrayer, ignoreCase = true)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isNext) ScanPangColors.PrimarySoft else Color.Transparent)
                    .padding(horizontal = ScanPangSpacing.xl, vertical = ScanPangSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = name,
                    style = ScanPangType.body14Regular,
                    color = if (isNext) ScanPangColors.Primary else ScanPangColors.OnSurfaceStrong,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = time,
                        style = if (isNext) ScanPangType.title14 else ScanPangType.body14Regular,
                        color = if (isNext) ScanPangColors.Primary else ScanPangColors.OnSurfaceMuted,
                    )
                    if (isNext) {
                        Text(
                            text = s.prayerTimeNext,
                            style = ScanPangType.caption12Medium,
                            color = ScanPangColors.Primary,
                        )
                    }
                }
            }
            if (index < times.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = ScanPangSpacing.xl),
                    color = ScanPangColors.OutlineSubtle,
                )
            }
        }
    }
}
