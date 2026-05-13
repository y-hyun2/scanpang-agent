package com.scanpang.app.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangTheme
import com.scanpang.app.ui.theme.ScanPangType

private val ErrorGradient = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to ScanPangColors.LoginGradientStart,
        0.55f to ScanPangColors.LoginGradientMid,
        1f to ScanPangColors.LoginGradientEnd,
    ),
)

@Composable
fun LoginErrorScreen(
    onRetry: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ErrorGradient)
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(ScanPangColors.ErrorSoftBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    tint = ScanPangColors.DangerStrong,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "로그인에 실패했어요",
                style = ScanPangType.detailScreenTitle22,
                color = ScanPangColors.OnSurfaceStrong,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "네트워크 연결을 확인하고\n다시 시도해주세요",
                style = ScanPangType.body14Regular,
                color = ScanPangColors.OnSurfaceMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ScanPangColors.Primary)
                    .clickable(onClick = onRetry),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "다시 시도",
                    style = ScanPangType.title16SemiBold,
                    color = Color.White,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable(onClick = onBackToLogin),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "다른 방법으로 로그인",
                    style = ScanPangType.body14Regular.copy(
                        textDecoration = TextDecoration.Underline,
                    ),
                    color = ScanPangColors.Primary,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun LoginErrorScreenPreview() {
    ScanPangTheme {
        LoginErrorScreen(onRetry = {}, onBackToLogin = {})
    }
}
