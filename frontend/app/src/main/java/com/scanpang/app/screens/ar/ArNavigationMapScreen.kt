package com.scanpang.app.screens.ar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.TurnSharpLeft
import androidx.compose.material.icons.rounded.TurnSharpRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanpang.app.data.remote.ScanPangViewModel
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.scanpang.app.components.ar.ArNavCompass
import com.scanpang.app.components.ar.ArNavMiniMap
import com.scanpang.app.components.ar.ArNavUiState
import com.scanpang.app.components.ar.ArRealSceneView
import com.scanpang.app.components.ar.TurnDirection
import com.scanpang.app.components.ar.ArNavActionCardCluster
import com.scanpang.app.components.ar.ArNavAiGuideTabWithTextField
import com.scanpang.app.components.ar.ArNavBottomSheet
import com.scanpang.app.components.ar.ArNavDestinationPill
import com.scanpang.app.components.ar.ArNavSideVolumeCamera
import com.scanpang.app.components.ar.ArNavTopHud
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors

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
    destinationName: String = "",
) {
    // ArRealSceneView가 매 프레임 보고하는 길안내 상태 (좌/우/직진, 거리, 도착 등)
    var navUiState by remember { mutableStateOf(ArNavUiState()) }

    // 미니맵용 — 사용자 pose, 라우트 폴리라인, 목적지 좌표
    var userLat by remember { mutableStateOf(0.0) }
    var userLng by remember { mutableStateOf(0.0) }
    var userHeading by remember { mutableStateOf(0.0) }
    var routePoints by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    var destLat by remember { mutableStateOf<Double?>(null) }
    var destLng by remember { mutableStateOf<Double?>(null) }

    // 폴백: AR이 아직 LOCALIZING이면 서버 응답의 첫 턴을 정적으로 표시
    val routeResult by viewModel.navRouteResult.collectAsState()
    val arCommand = routeResult?.ar_command
    val turnPoints = arCommand?.turn_points ?: emptyList()
    val firstTurn = turnPoints.firstOrNull()
    val secondTurn = turnPoints.getOrNull(1)
    val displayDestinationName = arCommand?.destination?.name
        ?: destinationName.ifEmpty { "목적지" }

    // 표시용 값: 라우팅 중이면 navUiState, 아니면 폴백
    val isRouting = navUiState.phase == ArNavUiState.Phase.ROUTING || navUiState.phase == ArNavUiState.Phase.ARRIVED
    val currentInstruction = when {
        navUiState.isArrived -> navUiState.statusMessage
        isRouting -> "${navUiState.direction} ${navUiState.currentDistanceM}m"
        else -> firstTurn?.let { it.speech.ifEmpty { it.description.ifEmpty { "직진" } } }
            ?: navUiState.statusMessage.ifEmpty { "위치 잡는 중..." }
    }
    val currentDistance = if (isRouting) "${navUiState.currentDistanceM}m"
        else firstTurn?.let { "${it.segment_distance_m}m" } ?: "—"
    val nextDistance = if (isRouting) "${navUiState.nextDistanceM}m"
        else secondTurn?.let { "${it.segment_distance_m}m" } ?: "—"
    val showNextStep = if (isRouting) navUiState.nextDistanceM > 0 else secondTurn != null

    val turnIcon = when (navUiState.turnDirection) {
        TurnDirection.LEFT -> Icons.Rounded.TurnSharpLeft
        TurnDirection.RIGHT -> Icons.Rounded.TurnSharpRight
        TurnDirection.DESTINATION -> Icons.Rounded.Place
        TurnDirection.STRAIGHT -> Icons.Rounded.ArrowUpward
    }
    var activeTab by remember { mutableStateOf(NAV_TAB_MAP) }
    var aiQuery by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {
        ArRealSceneView(
            modifier = Modifier.fillMaxSize(),
            targetDestination = destinationName,
            onPoseUpdate = { lat, lng, heading, _, _ ->
                userLat = lat
                userLng = lng
                userHeading = heading
            },
            onNavigationUpdate = { navUiState = it },
            onRouteAvailable = { points, dLat, dLng ->
                routePoints = points
                destLat = dLat
                destLng = dLng
            },
        )

        ArNavActionCardCluster(
            showNextStep = showNextStep,
            nextDistance = nextDistance,
            currentManeuverIcon = turnIcon,
            currentDistance = currentDistance,
            currentInstruction = currentInstruction,
        )

        ArNavTopHud(
            modifier = Modifier.align(Alignment.TopStart),
            onHomeClick = { navController.popBackStack() },
            onSearchClick = {
                navController.navigate(AppRoutes.ArExplore) { launchSingleTop = true }
            },
            destinationPill = {
                ArNavDestinationPill(
                    text = "$displayDestinationName 안내 중",
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
                mapContent = {
                    ArNavMiniMap(
                        userLat = userLat,
                        userLng = userLng,
                        userHeading = userHeading,
                        routePoints = routePoints,
                        destinationLat = destLat,
                        destinationLng = destLng,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
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

        // 점선 나침반 — 폰을 아래로 내려다볼 때만 표시. BottomSheet보다 위에 z-order로
        // 그리되, 화면 상단 60% 영역으로 한정해 지도와 겹치지 않게.
        if (navUiState.showCompass) {
            ArNavCompass(
                angleDiffDeg = navUiState.compassAngleDeg,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f),
            )
        }
    }
}
