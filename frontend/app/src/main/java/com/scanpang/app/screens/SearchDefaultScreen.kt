package com.scanpang.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.scanpang.app.components.RecentSearchRow
import com.scanpang.app.components.ScanPangCategoryTile
import com.scanpang.app.components.ScanPangSuggestionRow
import com.scanpang.app.data.SearchHistoryPreferences
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

/**
 * 검색 기본 — TextField로 입력 후 IME 검색 시 결과 화면으로 이동.
 */
@Composable
fun SearchDefaultScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val historyPrefs = remember { SearchHistoryPreferences(context) }
    var recent by remember { mutableStateOf(historyPrefs.getRecent()) }
    var query by remember { mutableStateOf("") }

    fun runSearch(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) return
        keyboard?.hide()
        historyPrefs.add(q)
        recent = historyPrefs.getRecent()
        navController.navigate(AppRoutes.searchResultsRoute(q))
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScanPangColors.Surface,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScanPangColors.Surface)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(ScanPangDimens.screenHorizontal)
                .padding(bottom = ScanPangDimens.mainTabContentBottomInset + ScanPangSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.xl),
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
                    .clip(ScanPangShapes.radius14),
                placeholder = {
                    Text(
                        text = "장소, 식당, 카테고리 검색",
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
                textStyle = ScanPangType.body15Medium.copy(
                    color = ScanPangColors.OnSurfaceStrong,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { runSearch(query) },
                ),
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
            Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "최근 검색",
                        style = ScanPangType.sectionTitle16,
                        color = ScanPangColors.OnSurfaceStrong,
                    )
                    Text(
                        text = "전체 삭제",
                        style = ScanPangType.caption12Medium,
                        color = ScanPangColors.OnSurfacePlaceholder,
                        modifier = Modifier.clickable {
                            historyPrefs.clearAll()
                            recent = emptyList()
                        },
                    )
                }
                recent.forEach { item ->
                    RecentSearchRow(
                        query = item,
                        onRowClick = { runSearch(item) },
                        onRemoveClick = {
                            historyPrefs.remove(item)
                            recent = historyPrefs.getRecent()
                        },
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.lg)) {
                Text(
                    text = "추천 카테고리",
                    style = ScanPangType.sectionTitle16,
                    color = ScanPangColors.OnSurfaceStrong,
                )
                Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.rowGap10)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.rowGap10),
                    ) {
                        ScanPangCategoryTile(
                            label = "할랄 식당",
                            icon = Icons.Rounded.Restaurant,
                            iconTint = ScanPangColors.CategoryRestaurant,
                            onClick = { navController.navigate(AppRoutes.NearbyHalal) },
                            modifier = Modifier.weight(1f),
                        )
                        ScanPangCategoryTile(
                            label = "기도실",
                            icon = Icons.Rounded.Mosque,
                            iconTint = ScanPangColors.Primary,
                            onClick = { navController.navigate(AppRoutes.NearbyPrayer) },
                            modifier = Modifier.weight(1f),
                        )
                        ScanPangCategoryTile(
                            label = "카페",
                            icon = Icons.Rounded.Coffee,
                            iconTint = ScanPangColors.CategoryCafe,
                            onClick = { runSearch("카페") },
                            modifier = Modifier.weight(1f),
                        )
                        ScanPangCategoryTile(
                            label = "쇼핑",
                            icon = Icons.Rounded.LocalMall,
                            iconTint = ScanPangColors.CategoryMall,
                            onClick = { runSearch("쇼핑") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.rowGap10),
                    ) {
                        ScanPangCategoryTile(
                            label = "병원",
                            icon = Icons.Rounded.LocalHospital,
                            iconTint = ScanPangColors.CategoryMedical,
                            onClick = { runSearch("병원") },
                            modifier = Modifier.weight(1f),
                        )
                        ScanPangCategoryTile(
                            label = "약국",
                            icon = Icons.Rounded.Medication,
                            iconTint = ScanPangColors.CategoryMedical,
                            onClick = { runSearch("약국") },
                            modifier = Modifier.weight(1f),
                        )
                        ScanPangCategoryTile(
                            label = "환전소",
                            icon = Icons.Rounded.CurrencyExchange,
                            iconTint = ScanPangColors.CategoryExchange,
                            onClick = { runSearch("환전소") },
                            modifier = Modifier.weight(1f),
                        )
                        ScanPangCategoryTile(
                            label = "관광지",
                            icon = Icons.Rounded.Place,
                            iconTint = ScanPangColors.Primary,
                            onClick = { runSearch("관광지") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md)) {
                Text(
                    text = "이런 곳은 어때요?",
                    style = ScanPangType.sectionTitle16,
                    color = ScanPangColors.OnSurfaceStrong,
                )
                Column(verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm)) {
                    ScanPangSuggestionRow(
                        title = "주변 할랄 식당 보기",
                        onClick = { navController.navigate(AppRoutes.NearbyHalal) },
                    )
                    ScanPangSuggestionRow(
                        title = "주변 기도실 보기",
                        onClick = { navController.navigate(AppRoutes.NearbyPrayer) },
                    )
                    ScanPangSuggestionRow(title = "명동 인기 쇼핑몰", onClick = { })
                    ScanPangSuggestionRow(title = "외국인 인기 관광지", onClick = { })
                }
            }
        }
    }
}
