package com.scanpang.app.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

@Composable
fun ScanPangSearchFieldPlaceholder(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ScanPangDimens.searchBarHeightDefault)
            .clip(ScanPangShapes.radius14)
            .background(ScanPangColors.Background)
            .clickable(onClick = onClick)
            .padding(horizontal = ScanPangSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.rowGap10),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            modifier = Modifier.size(ScanPangDimens.icon18),
            tint = ScanPangColors.OnSurfacePlaceholder,
        )
        Text(
            text = placeholder,
            style = ScanPangType.searchPlaceholderRegular,
            color = ScanPangColors.OnSurfacePlaceholder,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ScanPangSearchFieldFilled(
    query: String,
    onSearchBarClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
    hintWhenBlank: String? = null,
) {
    val showHint = query.isBlank() && !hintWhenBlank.isNullOrBlank()
    val labelText = if (showHint) hintWhenBlank else query
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ScanPangDimens.searchBarHeightActive)
            .clip(ScanPangShapes.radius12)
            .background(ScanPangColors.Background)
            .padding(horizontal = ScanPangDimens.searchBarInnerHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onSearchBarClick),
            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.rowGap10),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(ScanPangDimens.icon18),
                tint = if (showHint) ScanPangColors.OnSurfacePlaceholder else ScanPangColors.Primary,
            )
            Text(
                text = labelText,
                style = if (showHint) ScanPangType.searchPlaceholderRegular else ScanPangType.body15Medium,
                color = if (showHint) ScanPangColors.OnSurfacePlaceholder else ScanPangColors.OnSurfaceStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!showHint && query.isNotEmpty()) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "지우기",
                modifier = Modifier
                    .size(ScanPangDimens.icon18)
                    .clickable(onClick = onClearClick),
                tint = ScanPangColors.OnSurfacePlaceholder,
            )
        }
    }
}

/**
 * 검색 탭 기본 화면 / 검색 결과 화면이 공통으로 쓰는 인라인 검색 입력 필드.
 *
 * 디자인 토큰(높이·반경·배경·아이콘 색·플레이스홀더)을 한 곳에 모아 두 화면의 검색바 UI 가
 * 절대 어긋나지 않도록 한다.
 *
 * 동작 분리:
 * - [value] / [onValueChange]: 호출 측이 상태를 보유.
 * - [onSubmit]: IME 의 "검색" 액션이 눌렸을 때 한 번 호출(예: 최근 검색 저장 + 키보드 닫기).
 * - [onTrailingClick]: 우측 X 아이콘 탭 동작 (예: 입력 비우기, 또는 기본 검색 화면으로 돌아가기).
 *   `value.isNotEmpty()` 일 때만 X 가 노출되며 둘러싸인 아이콘 영역만 클릭 가능하다
 *   (좌측 돋보기 아이콘 영역은 별도 동작이 없고 탭 시 자연스럽게 TextField 가 포커스됨).
 */
@Composable
fun ScanPangInlineSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onTrailingClick: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clip(ScanPangShapes.radius14),
        placeholder = {
            Text(
                text = placeholder,
                style = ScanPangType.searchPlaceholderRegular.copy(
                    color = ScanPangColors.OnSurfacePlaceholder,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(ScanPangDimens.icon18),
                tint = ScanPangColors.OnSurfacePlaceholder,
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onTrailingClick) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "지우기",
                        modifier = Modifier.size(ScanPangDimens.icon18),
                        tint = ScanPangColors.OnSurfacePlaceholder,
                    )
                }
            }
        },
        textStyle = ScanPangType.body15Medium.copy(
            color = ScanPangColors.OnSurfaceStrong,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = ScanPangColors.Background,
            unfocusedContainerColor = ScanPangColors.Background,
            disabledContainerColor = ScanPangColors.Background,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = ScanPangColors.Primary,
        ),
    )
}
