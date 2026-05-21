package com.scanpang.app.screens.ar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scanpang.app.ar.ArExploreTtsController
import com.scanpang.app.ar.ScanPangAgentService
import com.scanpang.app.components.ar.ArAgentChatMessage
import com.scanpang.app.data.remote.ScanPangViewModel
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import kotlinx.coroutines.launch
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
import com.scanpang.app.components.ar.ArNavStopNavigationSheet
import com.scanpang.app.components.ar.ArNavStopConfirmDialog
import com.scanpang.app.components.ar.ArPoiFloatingDetailOverlay
import com.scanpang.app.components.ar.ArFloorStoreGuideOverlay
import com.scanpang.app.components.ar.ArPoiTabBuilding
import com.scanpang.app.components.ScanPangMainTab
import com.scanpang.app.components.ScanPangTabBar
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import androidx.compose.runtime.LaunchedEffect

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
    val appContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val agentService = remember { ScanPangAgentService() }
    val ttsController = remember(appContext) { ArExploreTtsController(appContext) {} }
    var chatMessages by remember {
        mutableStateOf(listOf(ArAgentChatMessage(text = "길찾기 중 궁금한 점을 물어보세요!", isUser = false)))
    }

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
    var isTtsOn by remember { mutableStateOf(true) }
    var showStopNavSheet by remember { mutableStateOf(false) }
    var showStopConfirmDialog by remember { mutableStateOf(false) }

    // 건물 핀 클릭 → 상세 오버레이
    var selectedBuildingPoi by remember { mutableStateOf<String?>(null) }
    var activeDetailTab by remember { mutableStateOf(ArPoiTabBuilding) }
    var selectedStore by remember { mutableStateOf<String?>(null) }
    val placeResult by viewModel.placeResult.collectAsState()
    val storeResult by viewModel.storeResult.collectAsState()


    // 탐색모드와 동일하게 GPS 기반으로 주변 건물을 가져와 길안내 중에도 건물 핀 표시
    val buildingsCache by viewModel.buildingsCache.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        ArRealSceneView(
            modifier = Modifier.fillMaxSize(),
            targetDestination = destinationName,
            onPoseUpdate = { lat, lng, heading, _, _ ->
                userLat = lat
                userLng = lng
                userHeading = heading
                agentService.updatePosition(lat, lng, heading)
                viewModel.updateLocationForChunk(lat, lng)
            },
            onNavigationUpdate = { navUiState = it },
            onRouteAvailable = { points, dLat, dLng ->
                routePoints = points
                destLat = dLat
                destLng = dLng
            },
            voiceOn = isTtsOn,
            buildingsCache = buildingsCache,
            onBuildingPinClick = { name, bdMgtSn ->
                selectedBuildingPoi = name
                activeDetailTab = ArPoiTabBuilding
                selectedStore = null
                if (bdMgtSn != null) {
                    viewModel.queryPlace(
                        heading = userHeading,
                        lat = userLat,
                        lng = userLng,
                        bdMgtSn = bdMgtSn,
                    )
                }
            },
        )

        ArNavActionCardCluster(
            showNextStep = showNextStep,
            nextDistance = nextDistance,
            nextManeuverIcon = nextTurnIcon,
            currentManeuverIcon = turnIcon,
            currentDistance = currentDistance,
            currentInstruction = currentInstruction,
            isArrived = navUiState.isArrived,
        )

        ArNavTopHud(
            modifier = Modifier.align(Alignment.TopStart),
            onCameraClick = { },
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
                    onClick = { showStopNavSheet = true },
                )
            },
        )

        ArNavSideVolumeCamera(
            onVolumeClick = { isTtsOn = !isTtsOn },
            isTtsOn = isTtsOn,
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            ArNavBottomSheet(
                mapTabSelected = activeTab == NAV_TAB_MAP,
                onSelectMap = { activeTab = NAV_TAB_MAP },
                onSelectAgent = { activeTab = NAV_TAB_AI },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
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
                        onSend = { text ->
                            val q = text.trim()
                            if (q.isEmpty()) return@ArNavAiGuideTabWithTextField
                            chatMessages = chatMessages + ArAgentChatMessage(text = q, isUser = true)
                            aiQuery = ""
                            scope.launch {
                                val reply = agentService.sendMessage(q)
                                chatMessages = chatMessages + ArAgentChatMessage(text = reply, isUser = false)
                                ttsController.speakIfEnabled(reply, isTtsOn)
                            }
                        },
                        messages = chatMessages,
                        placeholder = "무엇이든 물어보세요",
                    )
                },
            )
            ScanPangTabBar(
                selectedTab = ScanPangMainTab.Explore,
                onHomeClick = {
                    navController.navigate(AppRoutes.Home) {
                        popUpTo(AppRoutes.Home) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onSearchClick = {
                    navController.navigate(AppRoutes.Search) { launchSingleTop = true }
                },
                onSavedClick = {
                    navController.navigate(AppRoutes.Saved) { launchSingleTop = true }
                },
                onProfileClick = {
                    navController.navigate(AppRoutes.Profile) { launchSingleTop = true }
                },
                onExploreClick = {
                    navController.navigate(AppRoutes.ArExplore) {
                        launchSingleTop = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
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
                    .fillMaxHeight()
                    .padding(bottom = ScanPangDimens.arChatAreaMaxHeight + ScanPangDimens.bottomBarContainerHeight),
            )
        }

        if (showStopNavSheet) {
            ArNavStopNavigationSheet(
                destinationName = displayDestinationName,
                onDismiss = { showStopNavSheet = false },
                onStopNavigation = {
                    showStopNavSheet = false
                    showStopConfirmDialog = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 건물 핀 클릭 → 건물 상세 오버레이 (탐색모드와 동일 로직)
        selectedBuildingPoi?.let { poi ->
            ArPoiFloatingDetailOverlay(
                poiName = poi,
                activeDetailTab = activeDetailTab,
                onActiveDetailTabChange = { activeDetailTab = it },
                onDismiss = {
                    selectedBuildingPoi = null
                    selectedStore = null
                    activeDetailTab = ArPoiTabBuilding
                },
                onFloorStoreClick = {
                    // 건물 패널 닫고 매장 패널 표시 — 두 fillMaxSize 패널 동시 표시 시
                    // 매장 카드 잘려보임 방지.
                    selectedStore = it
                    selectedBuildingPoi = null
                },
                onSave = {},
                modifier = Modifier.fillMaxSize(),
                arOverlay = placeResult?.ar_overlay,
            )
        }

        selectedStore?.let { store ->
            LaunchedEffect(store) { viewModel.queryStore(placeId = "", storeName = store) }
            val s = storeResult?.takeIf { it.store_name == store }
            ArFloorStoreGuideOverlay(
                storeName = store,
                onDismiss = { selectedStore = null },
                onStartNavigation = {
                    navController.navigate(AppRoutes.arNavMapRoute(store)) { launchSingleTop = true }
                    selectedStore = null
                },
                modifier = Modifier.fillMaxSize(),
                category = s?.category.orEmpty(),
                isOpenNow = s?.is_open_now,
                storeResult = s,
            )
        }

        if (showStopConfirmDialog) {
            ArNavStopConfirmDialog(
                onNavigateToExplore = {
                    showStopConfirmDialog = false
                    navController.navigate(AppRoutes.ArExplore) {
                        popUpTo(AppRoutes.Home) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onNavigateToHome = {
                    showStopConfirmDialog = false
                    navController.navigate(AppRoutes.Home) {
                        popUpTo(AppRoutes.Home) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onDismiss = { showStopConfirmDialog = false },
            )
        }
    }
}
