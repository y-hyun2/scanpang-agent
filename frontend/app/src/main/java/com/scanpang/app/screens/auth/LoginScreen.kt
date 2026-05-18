package com.scanpang.app.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scanpang.app.components.auth.GoogleLoginButton
import com.scanpang.app.components.auth.KakaoLoginButton
import com.scanpang.app.components.auth.LogoMark
import com.scanpang.app.data.AuthProvider
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangTheme
import com.scanpang.app.ui.theme.ScanPangType

private val LoginGradient = Brush.verticalGradient(
    colorStops = arrayOf(
        0f to ScanPangColors.LoginGradientStart,
        0.55f to ScanPangColors.LoginGradientMid,
        1f to ScanPangColors.LoginGradientEnd,
    ),
)

private val WordmarkBaseStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = 36.sp,
    lineHeight = 44.sp,
    letterSpacing = (-0.5).sp,
)

@Composable
fun LoginScreen(
    onKakaoClick: () -> Unit,
    onGoogleClick: () -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    loadingProvider: AuthProvider? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LoginGradient)
                .systemBarsPadding()
                .padding(horizontal = ScanPangSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            LogoMark(size = 120.dp)
            Spacer(modifier = Modifier.height(24.dp))
            ScanPangWordmark()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "AR로 여는 무슬림 여행자의 한국 탐험",
                style = ScanPangType.meta13,
                color = ScanPangColors.OnSurfaceMuted,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.weight(1f))

            KakaoLoginButton(
                onClick = onKakaoClick,
                enabled = !isLoading || loadingProvider == AuthProvider.KAKAO,
                isLoading = isLoading && loadingProvider == AuthProvider.KAKAO,
            )
            Spacer(modifier = Modifier.height(12.dp))
            GoogleLoginButton(
                onClick = onGoogleClick,
                enabled = !isLoading || loadingProvider == AuthProvider.GOOGLE,
                isLoading = isLoading && loadingProvider == AuthProvider.GOOGLE,
            )

            Spacer(modifier = Modifier.height(16.dp))
            TermsNotice(onTermsClick = onTermsClick, onPrivacyClick = onPrivacyClick)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ScanPangWordmark() {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "Scan",
            style = WordmarkBaseStyle.copy(fontWeight = FontWeight.Light),
            color = ScanPangColors.OnSurfaceStrong,
        )
        Text(
            text = "Pang",
            style = WordmarkBaseStyle.copy(fontWeight = FontWeight.Bold),
            color = ScanPangColors.Primary,
        )
    }
}

private const val TERMS_TAG = "terms"
private const val PRIVACY_TAG = "privacy"

private fun buildTermsNoticeAnnotatedString(): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = ScanPangColors.OnSurfaceMuted)) {
        append("로그인하면 ")
    }
    pushStringAnnotation(tag = TERMS_TAG, annotation = TERMS_TAG)
    withStyle(
        SpanStyle(
            color = ScanPangColors.Primary,
            textDecoration = TextDecoration.Underline,
        ),
    ) {
        append("이용약관")
    }
    pop()
    withStyle(SpanStyle(color = ScanPangColors.OnSurfaceMuted)) {
        append(" 및 ")
    }
    pushStringAnnotation(tag = PRIVACY_TAG, annotation = PRIVACY_TAG)
    withStyle(
        SpanStyle(
            color = ScanPangColors.Primary,
            textDecoration = TextDecoration.Underline,
        ),
    ) {
        append("개인정보 처리방침")
    }
    pop()
    withStyle(SpanStyle(color = ScanPangColors.OnSurfaceMuted)) {
        append("에 동의합니다")
    }
}

@Composable
private fun TermsNotice(
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
) {
    val annotated: AnnotatedString = remember { buildTermsNoticeAnnotatedString() }
    val noticeStyle = remember {
        ScanPangType.caption12.copy(textAlign = TextAlign.Center)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        ClickableText(
            text = annotated,
            style = noticeStyle,
            onClick = { offset ->
                annotated.getStringAnnotations(tag = TERMS_TAG, start = offset, end = offset)
                    .firstOrNull()
                    ?.let {
                        onTermsClick()
                        return@ClickableText
                    }
                annotated.getStringAnnotations(tag = PRIVACY_TAG, start = offset, end = offset)
                    .firstOrNull()
                    ?.let { onPrivacyClick() }
            },
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Default")
@Composable
private fun LoginScreenPreviewDefault() {
    ScanPangTheme {
        LoginScreen(
            onKakaoClick = {},
            onGoogleClick = {},
            onTermsClick = {},
            onPrivacyClick = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "Loading KAKAO")
@Composable
private fun LoginScreenPreviewLoadingKakao() {
    ScanPangTheme {
        LoginScreen(
            onKakaoClick = {},
            onGoogleClick = {},
            onTermsClick = {},
            onPrivacyClick = {},
            isLoading = true,
            loadingProvider = AuthProvider.KAKAO,
        )
    }
}
