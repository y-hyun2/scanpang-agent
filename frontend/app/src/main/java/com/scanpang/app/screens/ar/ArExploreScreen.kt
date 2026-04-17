package com.scanpang.app.screens.ar

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.scanpang.app.data.remote.ScanPangViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanpang.app.ui.theme.ScanPangColors

/**
 * AR 탐색 — PlaceAugmentingActivity 실행 전용 화면.
 * ARCore Geospatial + hufs-cdp UI 통합은 PlaceAugmentingActivity에서 처리.
 */
@Composable
fun ArExploreScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ScanPangViewModel = viewModel(),
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        context.startActivity(
            Intent(context, com.scanpang.app.ar.explore.PlaceAugmentingActivity::class.java)
        )
    }

    // PlaceAugmentingActivity가 뜨기 전 잠깐 보이는 배경
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ScanPangColors.Background),
    )
}
