package com.scanpang.app.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangTheme
import com.scanpang.app.ui.theme.ScanPangType

private val DeleteItems = listOf(
    "프로필 및 계정 정보",
    "저장된 장소 및 즐겨찾기",
    "여행 기록 및 리뷰",
    "할랄 인증 스캔 이력",
)

@Composable
fun WithdrawalScreen(
    onBack: () -> Unit,
    onWithdraw: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmed by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScanPangColors.Surface,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScanPangColors.Surface)
                .statusBarsPadding(),
        ) {
            WithdrawalHeader(onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = ScanPangSpacing.lg)
                    .padding(top = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                WarnIcon()
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "정말 탈퇴하시겠어요?",
                        style = ScanPangType.detailScreenTitle22,
                        color = ScanPangColors.OnSurfaceStrong,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "탈퇴 시 모든 데이터가 삭제되며\n복구할 수 없습니다.",
                        style = ScanPangType.body14Regular,
                        color = ScanPangColors.OnSurfaceMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                InfoCard()
                ConfirmRow(
                    checked = confirmed,
                    onToggle = { confirmed = !confirmed },
                )
                Spacer(modifier = Modifier.weight(1f))
                WithdrawCta(enabled = confirmed, onClick = onWithdraw)
            }
        }
    }
}

@Composable
private fun WithdrawalHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = ScanPangSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
            contentDescription = "뒤로",
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onBack),
            tint = ScanPangColors.OnSurfaceStrong,
        )
        Text(
            text = "회원탈퇴",
            style = ScanPangType.profileName18,
            color = ScanPangColors.OnSurfaceStrong,
        )
    }
}

@Composable
private fun WarnIcon() {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(ScanPangColors.WarningSoftBackground),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.PriorityHigh,
            contentDescription = null,
            tint = ScanPangColors.AccentAmber,
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun InfoCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ScanPangColors.Background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "삭제되는 정보",
            style = ScanPangType.title14,
            color = ScanPangColors.OnSurfaceStrong,
        )
        DeleteItems.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = ScanPangColors.DangerStrong,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = item,
                    style = ScanPangType.body14Regular,
                    color = ScanPangColors.OnSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun ConfirmRow(
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val border = if (checked) ScanPangColors.DangerStrong else ScanPangColors.OnSurfaceStrong
        val fill = if (checked) ScanPangColors.DangerStrong else Color.Transparent
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(1.5.dp, border, RoundedCornerShape(4.dp))
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            text = "위 내용을 모두 확인했습니다",
            style = ScanPangType.meta13,
            color = ScanPangColors.OnSurfaceMuted,
        )
    }
}

@Composable
private fun WithdrawCta(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(RoundedCornerShape(12.dp))
            .background(ScanPangColors.DangerStrong)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "탈퇴하기",
            style = ScanPangType.title16SemiBold,
            color = Color.White,
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Default")
@Composable
private fun WithdrawalScreenPreview() {
    ScanPangTheme {
        WithdrawalScreen(onBack = {}, onWithdraw = {})
    }
}
