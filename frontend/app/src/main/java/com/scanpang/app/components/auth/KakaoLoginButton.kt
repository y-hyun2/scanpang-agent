package com.scanpang.app.components.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangTheme
import com.scanpang.app.ui.theme.ScanPangType

/**
 * 카카오 로그인 버튼.
 *
 * - bg: [ScanPangColors.KakaoYellow], pressed 시 [ScanPangColors.KakaoYellowPressed]
 * - height 56dp, fillMaxWidth, RoundedCornerShape(12dp)
 * - 좌측에 말풍선 아이콘(24dp, 24dp inset), 라벨은 버튼 중앙
 * - [isLoading] 시 라벨/아이콘 hide + 가운데 CircularProgressIndicator(20dp)
 * - [enabled] false 시 alpha 0.5 + 클릭 비활성
 */
@Composable
fun KakaoLoginButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bg = if (isPressed && enabled && !isLoading) {
        ScanPangColors.KakaoYellowPressed
    } else {
        ScanPangColors.KakaoYellow
    }
    val canClick = enabled && !isLoading

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = canClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = ScanPangColors.KakaoLabel,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Chat,
                contentDescription = null,
                tint = ScanPangColors.KakaoLabel,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .size(20.dp),
            )
            Text(
                text = "카카오로 시작하기",
                style = ScanPangType.title16SemiBold,
                color = ScanPangColors.KakaoLabel,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 360, name = "Default")
@Composable
private fun KakaoLoginButtonPreviewDefault() {
    ScanPangTheme {
        Box(modifier = Modifier.padding(20.dp)) {
            KakaoLoginButton(onClick = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 360, name = "Loading")
@Composable
private fun KakaoLoginButtonPreviewLoading() {
    ScanPangTheme {
        Box(modifier = Modifier.padding(20.dp)) {
            KakaoLoginButton(onClick = {}, isLoading = true)
        }
    }
}
