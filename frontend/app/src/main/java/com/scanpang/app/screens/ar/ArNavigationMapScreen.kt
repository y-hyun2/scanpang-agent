package com.scanpang.app.screens.ar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TurnSharpLeft
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hufs.arnavigation_com.ArNavigationActivity
import com.scanpang.app.data.remote.ScanPangViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavController
import com.scanpang.app.components.ar.ArCameraBackdrop
import com.scanpang.app.components.ar.ArFloorStoreGuideOverlay
import com.scanpang.app.components.ar.ArNavActionCardCluster
import com.scanpang.app.components.ar.ArNavAiGuideTabWithTextField
import com.scanpang.app.components.ar.ArNavBottomSheet
import com.scanpang.app.components.ar.ArNavDefaultPoiMarkers
import com.scanpang.app.components.ar.ArNavDestinationPill
import com.scanpang.app.components.ar.ArNavMapImageContent
import com.scanpang.app.components.ar.ArNavSideVolumeCamera
import com.scanpang.app.components.ar.ArNavTopHud
import com.scanpang.app.components.ar.ArNavTurnBadge
import com.scanpang.app.components.ar.ArPoiFloatingDetailOverlay
import com.scanpang.app.components.ar.ArPoiTabBuilding
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens

private const val NAV_TAB_MAP = "map"
private const val NAV_TAB_AI = "ai"

/**
 * AR 길안내 — 지도 / AI 가이드 탭을 한 화면 내 상태로 전환.
 */
@Composable
fun ArNavigationMapScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ScanPangViewModel = viewModel(),
) {
    // AR Navigation Activity 실행 (ARCore 3D 경로 렌더링)
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        context.startActivity(Intent(context, ArNavigationActivity::class.java))
    }

    val routeResult by viewModel.navRouteResult.collectAsState()
    val arCommand = routeResult?.ar_command
    val turnPoints = arCommand?.turn_points ?: emptyList()
    val currentTurn = turnPoints.firstOrNull()
    val nextTurn = turnPoints.getOrNull(1)
    val destinationName = arCommand?.destination?.name ?: "목적지"
    val currentInstruction = currentTurn?.let {
        it.speech.ifEmpty { it.description.ifEmpty { "직진" } }
    } ?: "스타벅스에서 좌회전"
    val currentDistance = currentTurn?.let { "${it.segment_distance_m}m" } ?: "80m"
    val nextDistance = nextTurn?.let { "${it.segment_distance_m}m" } ?: "60m"
    var activeTab by remember { mutableStateOf(NAV_TAB_MAP) }
    var aiQuery by remember { mutableStateOf("") }
    var selectedPoi by remember { mutableStateOf<String?>(null) }
    var activePoiDetailTab by remember { mutableStateOf(ArPoiTabBuilding) }
    var selectedStore by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        ArCameraBackdrop(showFreezeTint = false, modifier = Modifier.fillMaxSize())

        ArNavActionCardCluster(
            showNextStep = nextTurn != null,
            nextDistance = nextDistance,
            currentManeuverIcon = Icons.Rounded.TurnSharpLeft,
            currentDistance = currentDistance,
            currentInstruction = currentInstruction,
        )

        ArNavDefaultPoiMarkers(
            onShoppingPoiClick = {
                selectedPoi = "눈스퀘어"
                activePoiDetailTab = ArPoiTabBuilding
                selectedStore = null
                viewModel.queryPlace(heading = 0.0, lat = 37.5636, lng = 126.9822)
            },
            onExchangePoiClick = {
                selectedPoi = "명동 환전소"
                activePoiDetailTab = ArPoiTabBuilding
                selectedStore = null
                viewModel.queryPlace(heading = 90.0, lat = 37.5636, lng = 126.9822)
            },
        )

        ArNavTopHud(
            modifier = Modifier.align(Alignment.TopStart),
            onHomeClick = { navController.popBackStack() },
            onSearchClick = {
                navController.navigate(AppRoutes.ArExplore) { launchSingleTop = true }
            },
            destinationPill = {
                ArNavDestinationPill(
                    text = "$destinationName 안내 중",
                    containerColor = ScanPangColors.Primary,
                )
            },
        )

        ArNavSideVolumeCamera(
            onVolumeClick = { },
            onCameraClick = { },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            ArNavBottomSheet(
                mapTabSelected = activeTab == NAV_TAB_MAP,
                onSelectMap = { activeTab = NAV_TAB_MAP },
                onSelectAgent = { activeTab = NAV_TAB_AI },
                modifier = Modifier.fillMaxWidth(),
                mapContent = { ArNavMapImageContent(Modifier.fillMaxSize()) },
                agentContent = {
                    ArNavAiGuideTabWithTextField(
                        query = aiQuery,
                        onQueryChange = { aiQuery = it },
                        userMessage = "눈스퀘어가 뭐야?",
                        agentMessage = "거의 다 왔어요! 입구는 정면 오른쪽이에요.",
                        placeholder = "무엇이든 물어보세요",
                    )
                },
            )
        }

        ArNavTurnBadge(
            icon = Icons.Rounded.TurnSharpLeft,
            iconSize = ScanPangDimens.arNavTurnBadgeIcon,
            badgeColor = ScanPangColors.ArNavPrimaryBadge90,
            iconTint = Color.White,
        )

        val placeResult by viewModel.placeResult.collectAsState()

        selectedPoi?.let { poi ->
            ArPoiFloatingDetailOverlay(
                poiName = poi,
                activeDetailTab = activePoiDetailTab,
                onActiveDetailTabChange = { activePoiDetailTab = it },
                onDismiss = {
                    selectedPoi = null
                    selectedStore = null
                    activePoiDetailTab = ArPoiTabBuilding
                },
                onFloorStoreClick = { selectedStore = it },
                modifier = Modifier.fillMaxSize(),
                arOverlay = placeResult?.ar_overlay,
                docent = placeResult?.docent,
            )
        }

        selectedStore?.let { store ->
            ArFloorStoreGuideOverlay(
                storeName = store,
                onDismiss = { selectedStore = null },
                onStartNavigation = {
                    navController.navigate(AppRoutes.ArNavMap) { launchSingleTop = true }
                    selectedStore = null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
