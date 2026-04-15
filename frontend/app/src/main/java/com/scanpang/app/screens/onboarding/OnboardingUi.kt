package com.scanpang.app.screens.onboarding

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

private val OnboardingCardRadius = RoundedCornerShape(12.dp)
private val OnboardingBlue = ScanPangColors.Primary
private val OnboardingCardBorderGray = ScanPangColors.OutlineSubtle

@Composable
fun OnboardingProgressHeader(
    step: Int,
    total: Int = 3,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "$step/$total",
        modifier = modifier.fillMaxWidth(),
        style = ScanPangType.body14Regular,
        color = ScanPangColors.OnSurfaceMuted,
        textAlign = TextAlign.Center,
    )
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

@Composable
fun OnboardingSelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val borderColor = if (selected) OnboardingBlue else OnboardingCardBorderGray
    val borderWidth = if (selected) 2.dp else 1.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(OnboardingCardRadius)
            .border(borderWidth, borderColor, OnboardingCardRadius)
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(ScanPangSpacing.md),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(OnboardingBlue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White,
                )
            }
        }
        content()
    }
}

@Composable
fun OnboardingLanguageCardContent(
    flagEmoji: String,
    label: String,
    selected: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = if (selected) 28.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
    ) {
        Text(text = flagEmoji, style = ScanPangType.title16SemiBold)
        Text(
            text = label,
            style = ScanPangType.body15Medium,
            color = ScanPangColors.OnSurfaceStrong,
            modifier = Modifier.weight(1f),
        )
    }
}
