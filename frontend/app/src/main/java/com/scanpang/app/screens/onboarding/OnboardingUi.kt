package com.scanpang.app.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

private val OnboardingCardRadiusDefault = RoundedCornerShape(12.dp)
private val OnboardingBlue = ScanPangColors.Primary
private val OnboardingCardBorderGray = ScanPangColors.OutlineSubtle

private val ProgressBarHeight = 4.dp
private val ProgressBarShape = RoundedCornerShape(2.dp)
private val SelectionRadioSize = 22.dp

/**
 * 상단 진행 인디케이터 — "X / total" 텍스트와 세그먼트 막대 조합.
 * Figma 온보딩 스펙: 현재 단계까지 Primary, 이후 단계는 OnSurfaceStrong 으로 강한 대비를 준다.
 */
@Composable
fun OnboardingProgressHeader(
    step: Int,
    total: Int = 3,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "$step / $total",
            style = ScanPangType.caption12Medium,
            color = ScanPangColors.OnSurfaceMuted,
        )
        Spacer(modifier = Modifier.height(ScanPangSpacing.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs)) {
            repeat(total) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(ProgressBarHeight)
                        .clip(ProgressBarShape)
                        .background(
                            if (index < step) OnboardingBlue
                            else ScanPangColors.OnSurfaceStrong,
                        ),
                )
            }
        }
    }
}

@Composable
fun OnboardingPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = OnboardingBlue,
            contentColor = Color.White,
            disabledContainerColor = ScanPangColors.OnSurfacePlaceholder.copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.7f),
        ),
    ) {
        Text(text = text, style = ScanPangType.body15Medium)
    }
}

/**
 * 온보딩 선택 카드 — 라디오 인디케이터가 오른쪽 중앙에 항상 자리한다.
 * 슬롯(content)은 RowScope 로 노출되므로 좌측 영역에 `Modifier.weight(1f)` 을 적용해
 * 인디케이터와 자연스럽게 정렬한다.
 */
@Composable
fun OnboardingSelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = OnboardingCardRadiusDefault,
    horizontalPadding: Dp = ScanPangSpacing.lg,
    verticalPadding: Dp = ScanPangSpacing.md,
    horizontalGap: Dp = ScanPangSpacing.md,
    content: @Composable RowScope.() -> Unit,
) {
    val borderColor = if (selected) OnboardingBlue else OnboardingCardBorderGray
    val borderWidth = if (selected) 2.dp else 1.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(borderWidth, borderColor, shape)
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(horizontalGap),
    ) {
        content()
        OnboardingSelectionRadio(selected = selected)
    }
}

@Composable
private fun OnboardingSelectionRadio(selected: Boolean) {
    if (selected) {
        Box(
            modifier = Modifier
                .size(SelectionRadioSize)
                .clip(CircleShape)
                .background(OnboardingBlue),
        )
    } else {
        Box(
            modifier = Modifier
                .size(SelectionRadioSize)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.5.dp, ScanPangColors.OutlineSubtle, CircleShape),
        )
    }
}

/**
 * 이모지·아이콘 + 제목 + 부제 로 구성되는 표준 선택 카드 본문.
 * Language / Preference 카드가 동일한 슬롯 구조를 공유한다.
 */
@Composable
fun RowScope.OnboardingChoiceContent(
    leading: String,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leadingTextStyle: TextStyle = ScanPangType.titleLarge,
    subtitleTextStyle: TextStyle = ScanPangType.caption12Medium,
    titleSubtitleSpacing: Dp = 2.dp,
) {
    Text(
        text = leading,
        style = leadingTextStyle,
    )
    Column(modifier = modifier.weight(1f)) {
        Text(
            text = title,
            style = ScanPangType.title16SemiBold,
            color = ScanPangColors.OnSurfaceStrong,
        )
        if (!subtitle.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(titleSubtitleSpacing))
            Text(
                text = subtitle,
                style = subtitleTextStyle,
                color = ScanPangColors.OnSurfaceMuted,
            )
        }
    }
}
