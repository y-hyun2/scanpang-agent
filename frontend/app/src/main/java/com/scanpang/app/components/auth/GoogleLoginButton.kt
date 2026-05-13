package com.scanpang.app.components.auth

import androidx.compose.foundation.Image
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scanpang.app.R
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangTheme
import com.scanpang.app.ui.theme.ScanPangType

/**
 * 구글 로그인 버튼. 동작·시그니처는 [KakaoLoginButton] 과 동일하게 맞춘다.
 *
 * - bg: [ScanPangColors.GoogleSurface] (neutral gray), pressed 시 [ScanPangColors.GoogleSurfacePressed]
 * - 보더 없음 (Figma neutral 스타일)
 * - 좌측에 공식 Google "G" 4-color 로고 (18dp, 24dp inset), 라벨은 버튼 중앙
 */
@Composable
fun GoogleLoginButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bg = if (isPressed && enabled && !isLoading) {
        ScanPangColors.GoogleSurfacePressed
    } else {
        ScanPangColors.GoogleSurface
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
                color = ScanPangColors.GoogleLabel,
                strokeWidth = 2.dp,
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.ic_google_g),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .size(18.dp),
            )
            Text(
                text = "Google로 시작하기",
                style = ScanPangType.title16SemiBold,
                color = ScanPangColors.GoogleLabel,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 360, name = "Default")
@Composable
private fun GoogleLoginButtonPreviewDefault() {
    ScanPangTheme {
        Box(modifier = Modifier.padding(20.dp)) {
            GoogleLoginButton(onClick = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 360, name = "Loading")
@Composable
private fun GoogleLoginButtonPreviewLoading() {
    ScanPangTheme {
        Box(modifier = Modifier.padding(20.dp)) {
            GoogleLoginButton(onClick = {}, isLoading = true)
        }
    }
}
