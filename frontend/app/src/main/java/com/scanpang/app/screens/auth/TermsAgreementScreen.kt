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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangTheme
import com.scanpang.app.ui.theme.ScanPangType
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.scanpang.app.R

private data class TermItem(
    val id: String,
    @StringRes val labelRes: Int,
    val required: Boolean,
)

private val Terms = listOf(
    TermItem("age14",     R.string.terms_age14,     required = true),
    TermItem("service",   R.string.terms_service,   required = true),
    TermItem("privacy",   R.string.terms_privacy,   required = true),
    TermItem("location",  R.string.terms_location,  required = true),
    TermItem("marketing", R.string.terms_marketing, required = false),
)

/**
 * 약관 동의 화면.
 *
 * State hoisting 단순화 — 외부에선 [onAllAgreedAndContinue] 콜백만 받고,
 * 체크 상태는 화면 내부 [remember] 로 보관한다.
 */
@Composable
fun TermsAgreementScreen(
    onBack: () -> Unit,
    onAllAgreedAndContinue: () -> Unit,
    onTermDetailClick: (termId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var checks by remember {
        mutableStateOf(Terms.associate { it.id to false })
    }
    val allChecked = checks.values.all { it }
    val requiredChecked = Terms.filter { it.required }.all { checks[it.id] == true }

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
            TermsHeader(onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = ScanPangSpacing.lg)
                    .padding(top = ScanPangSpacing.lg, bottom = ScanPangSpacing.xl),
            ) {
                Text(
                    text = stringResource(R.string.terms_consent_required),
                    style = ScanPangType.detailScreenTitle22,
                    color = ScanPangColors.OnSurfaceStrong,
                )
                Spacer(modifier = Modifier.height(ScanPangSpacing.lg))

                AllAgreeRow(
                    checked = allChecked,
                    onToggle = {
                        val next = !allChecked
                        checks = Terms.associate { it.id to next }
                    },
                )
                Spacer(modifier = Modifier.height(ScanPangSpacing.md))

                Terms.forEachIndexed { index, term ->
                    TermsAgreeRow(
                        label = run {
                            val termLabel = stringResource(term.labelRes)
                            if (term.required) stringResource(R.string.terms_required_suffix, termLabel)
                            else               stringResource(R.string.terms_optional_suffix, termLabel)
                        },
                        checked = checks[term.id] == true,
                        onToggle = {
                            checks = checks.toMutableMap().apply {
                                this[term.id] = !(this[term.id] ?: false)
                            }
                        },
                        showDetail = index != 0,
                        onDetailClick = { onTermDetailClick(term.id) },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                ContinueCta(
                    enabled = requiredChecked,
                    onClick = onAllAgreedAndContinue,
                )
            }
        }
    }
}

@Composable
private fun TermsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScanPangSpacing.lg, vertical = ScanPangSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(ScanPangColors.Background)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
                contentDescription = stringResource(R.string.common_back),
                modifier = Modifier.size(20.dp),
                tint = ScanPangColors.OnSurfaceStrong,
            )
        }
        Text(
            text = stringResource(R.string.terms_screen_title),
            style = ScanPangType.profileName18,
            color = ScanPangColors.OnSurfaceStrong,
        )
    }
}

@Composable
private fun AllAgreeRow(
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, ScanPangColors.OutlineSubtle, RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = ScanPangSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
    ) {
        CheckBoxRound(checked = checked, large = true)
        Text(
            text = stringResource(R.string.terms_agree_all),
            style = ScanPangType.title16SemiBold,
            color = ScanPangColors.OnSurfaceStrong,
        )
    }
}

@Composable
private fun TermsAgreeRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    showDetail: Boolean,
    onDetailClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = ScanPangSpacing.xs, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
    ) {
        CheckBoxRound(checked = checked, large = false)
        Text(
            text = label,
            style = ScanPangType.body14Regular,
            color = ScanPangColors.OnSurfaceStrong,
            modifier = Modifier.weight(1f),
        )
        if (showDetail) {
            Text(
                text = stringResource(R.string.terms_view_full),
                style = ScanPangType.caption12Medium,
                color = ScanPangColors.Primary,
                modifier = Modifier.clickable(onClick = onDetailClick),
            )
        }
    }
}

@Composable
private fun CheckBoxRound(checked: Boolean, large: Boolean) {
    val sizeDp = if (large) 24.dp else 22.dp
    val border = if (checked) ScanPangColors.Primary else ScanPangColors.OutlineSubtle
    val fill = if (checked) ScanPangColors.Primary else Color.Transparent
    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.5.dp, border, RoundedCornerShape(6.dp))
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(if (large) 16.dp else 14.dp),
            )
        }
    }
}

@Composable
private fun ContinueCta(enabled: Boolean, onClick: () -> Unit) {
    val bg = if (enabled) ScanPangColors.Primary else ScanPangColors.DisabledSurface
    val labelColor =
        if (enabled) Color.White else ScanPangColors.OnSurfacePlaceholder
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.terms_agree_continue),
            style = ScanPangType.title16SemiBold,
            color = labelColor,
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun TermsAgreementScreenPreview() {
    ScanPangTheme {
        TermsAgreementScreen(
            onBack = {},
            onAllAgreedAndContinue = {},
            onTermDetailClick = {},
        )
    }
}
