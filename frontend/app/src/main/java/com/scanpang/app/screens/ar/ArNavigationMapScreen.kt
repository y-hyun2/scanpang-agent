package com.scanpang.app.screens.ar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
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
import com.scanpang.app.components.ar.ArPoiFloatingDetailOverlay
import com.scanpang.app.components.ar.ArPoiTabBuilding
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
        // LLM이 만든 풍부한 안내 문구가 있으면 우선 사용 (예: "GS25 명동점에서 우회전하세요.")
        isRouting && navUiState.currentSpeech.isNotBlank() -> navUiState.currentSpeech
        // 폴백: 단순 "좌회전 152m" 형식
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
    val nextTurnIcon = when (navUiState.nextTurnDirection) {
        TurnDirection.LEFT -> Icons.Rounded.TurnSharpLeft
        TurnDirection.RIGHT -> Icons.Rounded.TurnSharpRight
        TurnDirection.DESTINATION -> Icons.Rounded.Place
        TurnDirection.STRAIGHT -> Icons.Rounded.ArrowUpward
    }
    var activeTab by remember { mutableStateOf(NAV_TAB_MAP) }
    var aiQuery by remember { mutableStateOf("") }
    var bottomSheetExpanded by remember { mutableStateOf(true) }

    // 공간증강 (주변 건물 인식) 상태
    var spaceAugmentEnabled by remember { mutableStateOf(true) }
    var selectedPoi by remember { mutableStateOf<String?>(null) }
    var activePoiDetailTab by remember { mutableStateOf(ArPoiTabBuilding) }
    val placeResult by viewModel.placeResult.collectAsState()
    val detectedOverlay = placeResult?.ar_overlay

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
            spaceAugmentEnabled = spaceAugmentEnabled,
            onPlaceQueryRequest = { heading, lat, lng, alt, pitch ->
                viewModel.queryPlace(
                    heading = heading,
                    lat = lat,
                    lng = lng,
                    alt = alt,
                    pitch = pitch,
                )
            },
        )

        ArNavActionCardCluster(
            showNextStep = showNextStep,
            nextDistance = nextDistance,
            nextManeuverIcon = nextTurnIcon,
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
                // 도착 시: 파란 "X 안내 중" → 초록 "X 도착" (Figma 디자인)
                ArNavDestinationPill(
                    text = if (navUiState.isArrived)
                        "$displayDestinationName 도착"
                    else
                        "$displayDestinationName 안내 중",
                    containerColor = if (navUiState.isArrived)
                        ScanPangColors.Success
                    else
                        ScanPangColors.Primary,
                )
            },
        )

        ArNavSideVolumeCamera(
            onVolumeClick = { },
            onCameraClick = { },
            spaceAugmentOn = spaceAugmentEnabled,
            onSpaceAugmentToggle = { spaceAugmentEnabled = !spaceAugmentEnabled },
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
                expanded = bottomSheetExpanded,
                onToggleExpanded = { bottomSheetExpanded = !bottomSheetExpanded },
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

        // 공간증강 검출 결과 — 건물 인식되면 화면 가운데 살짝 아래에 플로팅 칩 표시
        if (spaceAugmentEnabled && detectedOverlay != null && detectedOverlay.name.isNotBlank() && selectedPoi == null) {
            androidx.compose.material3.Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 220.dp)
                    .clickable { selectedPoi = detectedOverlay.name },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                color = ScanPangColors.ArOverlayWhite93,
                shadowElevation = 6.dp,
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Rounded.Place,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = ScanPangColors.Primary,
                    )
                    androidx.compose.material3.Text(
                        text = detectedOverlay.name,
                        color = ScanPangColors.OnSurfaceStrong,
                    )
                    androidx.compose.material3.Text(
                        text = "탭해서 보기",
                        color = ScanPangColors.OnSurfaceMuted,
                    )
                }
            }
        }

        // 디테일 패널 — 칩 탭 시 표시 (ArExploreScreen과 동일 컴포넌트 재활용)
        selectedPoi?.let { poi ->
            ArPoiFloatingDetailOverlay(
                poiName = poi,
                activeDetailTab = activePoiDetailTab,
                onActiveDetailTabChange = { activePoiDetailTab = it },
                onDismiss = {
                    selectedPoi = null
                    activePoiDetailTab = ArPoiTabBuilding
                },
                onFloorStoreClick = { /* 길안내 중에는 매장별 안내 분기 없이 닫기만 */ },
                modifier = Modifier.fillMaxSize(),
                arOverlay = placeResult?.ar_overlay,
                docent = placeResult?.docent,
            )
        }
    }
}
