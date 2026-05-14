package com.scanpang.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.scanpang.app.components.RecentlyViewedRow
import com.scanpang.app.components.toDetailRoute
import com.scanpang.app.data.RecentlyViewedStore
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType

/**
 * "최근 본 장소 더보기" 화면 — [RecentlyViewedStore] 에 누적된 전체 항목을 시간 역순으로 노출.
 * Home 미리보기와 동일한 [RecentlyViewedRow] 를 그대로 재사용한다.
 */
@Composable
fun RecentlyViewedListScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember(context) { RecentlyViewedStore(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var items by remember { mutableStateOf(store.getAll()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                items = store.getAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ScanPangColors.Surface,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { _ ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(ScanPangColors.Surface)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                horizontal = ScanPangDimens.screenHorizontal,
                vertical = ScanPangSpacing.md,
            ),
            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = ScanPangSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "뒤로",
                            tint = ScanPangColors.OnSurfaceStrong,
                        )
                    }
                    Text(
                        text = "최근 본 장소",
                        style = ScanPangType.detailScreenTitle22,
                        color = ScanPangColors.OnSurfaceStrong,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (items.isEmpty()) {
                item {
                    RecentlyViewedListEmpty()
                }
            } else {
                items(
                    items = items,
                    key = { it.id + it.viewedAt },
                ) { entry ->
                    RecentlyViewedRow(
                        entry = entry,
                        onClick = {
                            navController.navigate(entry.target.toDetailRoute()) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentlyViewedListEmpty() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ScanPangSpacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "아직 본 장소가 없어요",
            style = ScanPangType.body14Regular,
            color = ScanPangColors.OnSurfaceMuted,
        )
    }
}
