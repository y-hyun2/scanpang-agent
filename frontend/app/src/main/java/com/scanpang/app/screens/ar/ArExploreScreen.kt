package com.scanpang.app.screens.ar

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.opengl.Matrix
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Earth
import com.google.ar.core.TrackingState
import com.scanpang.app.ar.ArExploreTtsController
import com.scanpang.app.ar.ArSpeechRecognizerHelper
import com.scanpang.app.ar.ScanPangAgentService
import com.scanpang.app.ar.sendVoiceMessage
import com.scanpang.app.data.remote.ArOverlay
import com.scanpang.app.data.remote.Docent
import com.scanpang.app.data.remote.PlaceQueryRequest
import com.scanpang.app.data.remote.RetrofitClient
import com.scanpang.app.data.remote.ScanPangViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.scanpang.app.components.ar.ArAgentChatMessage
import com.scanpang.app.components.ar.ArCircleIconButton
import com.scanpang.app.components.ar.ArExploreInteractiveChatSection
import com.scanpang.app.components.ar.ArFloorStoreGuideOverlay
import com.scanpang.app.components.ar.ArPoiFloatingDetailOverlay
import com.scanpang.app.components.ar.ArPoiTabBuilding
import com.scanpang.app.components.ar.ArExploreFilterPanelFigma
import com.scanpang.app.components.ar.ArExploreSideColumn
import com.scanpang.app.components.ar.arExploreCategoryChipSpecs
import com.scanpang.app.components.ar.ArPoiCard
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangType
import io.github.sceneview.ar.ARScene
import io.github.sceneview.rememberEngine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private data class ArSearchHit(
    val title: String,
    val scoreLine: String,
    val distance: String,
)

private data class DynamicPoi(
    val id: String,
    val name: String,
    val category: String = "",
    val distance: Float = 0f,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val arOverlay: ArOverlay? = null,
    val docent: Docent? = null,
    val isPending: Boolean = false,
)

/**
 * AR 탐색 단일 화면 — ARCore Geospatial 엔진 통합.
 * CameraX 프리뷰 대신 ARScene을 사용하고, 주변 건물을 자동 탐지하여 동적 마커 배치.
 */
@Composable
fun ArExploreScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: ScanPangViewModel = viewModel(),
) {
    val placeResult by viewModel.placeResult.collectAsState()
    val convenienceResult by viewModel.convenienceResult.collectAsState()
    val context = LocalContext.current

    // 카테고리 라벨 → convenience API category key 매핑
    val categoryKeyMap = mapOf(
        "쇼핑" to "shopping", "편의점" to "convenience_store", "식당" to "restaurant",
        "카페" to "cafe", "환전소" to "exchange", "은행" to "bank", "ATM" to "atm",
        "병원" to "hospital", "지하철역" to "subway", "화장실" to "restroom",
        "물품보관함" to "locker", "약국" to "pharmacy",
    )

    val appContext = context.applicationContext
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val chatListState = rememberLazyListState()
    var chatInput by remember { mutableStateOf("") }
    var chatMessages by remember {
        mutableStateOf(
            listOf(
                ArAgentChatMessage(
                    text = "안녕하세요! 스캔팡입니다. 주변 장소를 AR로 안내해 드릴게요.",
                    isUser = false,
                ),
            ),
        )
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            chatListState.scrollToItem(chatMessages.lastIndex)
        }
    }

    var isFilterOpen by remember { mutableStateOf(false) }
    var categorySelection by remember { mutableStateOf(setOf<String>()) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var showArSearchResults by remember { mutableStateOf(false) }

    var isFrozen by remember { mutableStateOf(false) }
    var isTtsOn by remember { mutableStateOf(true) }

    var isSttListening by remember { mutableStateOf(false) }
    val ttsPlayingState = remember { mutableStateOf(false) }
    val isTtsPlaying by ttsPlayingState
    var speechHelperRef by remember { mutableStateOf<ArSpeechRecognizerHelper?>(null) }
    var pendingMicAfterPermission by remember { mutableStateOf(false) }

    val agentService = remember { ScanPangAgentService() }
    val ttsController = remember(appContext) {
        ArExploreTtsController(appContext) { playing -> ttsPlayingState.value = playing }
    }

    DisposableEffect(ttsController) {
        ttsController.start()
        onDispose { ttsController.shutdown() }
    }

    LaunchedEffect(isTtsOn) {
        if (!isTtsOn) ttsController.stop()
    }

    val onSttResult: (String) -> Unit = { text ->
        chatInput = text
        scope.launch {
            val reply = sendVoiceMessage(text, agentService)
            chatMessages = chatMessages +
                ArAgentChatMessage(text = text, isUser = true) +
                ArAgentChatMessage(text = reply, isUser = false)
            chatInput = ""
            ttsController.speakIfEnabled(reply, isTtsOn)
        }
    }
    val latestOnSttResult = rememberUpdatedState(onSttResult)

    val latestSnackbar = rememberUpdatedState(snackbarHostState)
    val latestScope = rememberUpdatedState(scope)

    DisposableEffect(appContext) {
        val h = ArSpeechRecognizerHelper(
            context = appContext,
            onListeningChange = { isSttListening = it },
            onResult = { text -> latestOnSttResult.value(text) },
            onErrorCode = { code ->
                if (code != SpeechRecognizer.ERROR_NO_MATCH &&
                    code != SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) {
                    latestScope.value.launch {
                        latestSnackbar.value.showSnackbar("음성 인식 중 오류가 났어요")
                    }
                }
            },
        )
        speechHelperRef = h
        onDispose {
            h.destroy()
            speechHelperRef = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingMicAfterPermission) {
            speechHelperRef?.startListening()
        } else if (!granted) {
            scope.launch { snackbarHostState.showSnackbar("마이크 권한이 필요해요") }
        }
        pendingMicAfterPermission = false
    }

    var selectedPoi by remember { mutableStateOf<String?>(null) }
    var selectedPoiOverlay by remember { mutableStateOf<ArOverlay?>(null) }
    var selectedPoiDocent by remember { mutableStateOf<Docent?>(null) }
    var activeDetailTab by remember { mutableStateOf(ArPoiTabBuilding) }
    var selectedStore by remember { mutableStateOf<String?>(null) }

    val categoryChipSpecs = remember { arExploreCategoryChipSpecs() }
    val recentQueries = remember {
        listOf("할랄 식당", "명동성당", "근처 환전소")
    }
    val suggestionTags = remember {
        listOf("할랄", "카페", "기도실", "환전소")
    }
    val searchHits = remember {
        listOf(
            ArSearchHit("할랄가든 명동점", "일치도 98%", "120m"),
            ArSearchHit("명동성당", "일치도 92%", "350m"),
            ArSearchHit("우리은행 환전소", "일치도 88%", "80m"),
        )
    }

    // ── ARCore Geospatial 상태 ──
    val engine = rememberEngine()
    val api = remember { RetrofitClient.api }
    var hasAchievedHighAccuracy by remember { mutableStateOf(false) }
    var trackingMessage by remember { mutableStateOf("ARCore 초기화 중...") }
    var currentHeading by remember { mutableStateOf(0.0) }
    var currentAltitude by remember { mutableStateOf(0.0) }
    var currentPitch by remember { mutableStateOf(0.0) }
    var currentLat by remember { mutableStateOf(0.0) }
    var currentLng by remember { mutableStateOf(0.0) }
    var lastQueryTime by remember { mutableStateOf(0L) }

    val geospatialAnchors = remember { mutableStateMapOf<String, Anchor>() }
    var anchorScreenPositions by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }
    val dynamicPois = remember { mutableStateListOf<DynamicPoi>() }

    // 화면 크기 (앵커 → 화면 좌표 투영용)
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }.toInt()
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }.toInt()

    DisposableEffect(Unit) {
        onDispose {
            geospatialAnchors.values.forEach { it.detach() }
            geospatialAnchors.clear()
        }
    }

    // convenience 결과 → 동적 POI로 추가
    LaunchedEffect(convenienceResult) {
        convenienceResult?.facilities?.forEach { facility ->
            val alreadyExists = dynamicPois.any { it.name == facility.name }
            if (!alreadyExists && facility.lat != 0.0 && facility.lng != 0.0) {
                val newId = "conv_${facility.name}_${System.currentTimeMillis()}"
                dynamicPois.add(
                    DynamicPoi(
                        id = newId,
                        name = facility.name,
                        category = convenienceResult?.category ?: "",
                        distance = facility.distance_m.toFloat(),
                        latitude = facility.lat,
                        longitude = facility.lng,
                    )
                )
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            // ── ARScene 배경 (CameraX 대체) ──
            ARScene(
                modifier = Modifier.fillMaxSize(),
                engine = engine,
                planeRenderer = false,
                sessionConfiguration = { _, config ->
                    config.geospatialMode = Config.GeospatialMode.ENABLED
                    config.depthMode = Config.DepthMode.AUTOMATIC
                },
                onSessionUpdated = { session, frame ->
                    val earth = session.earth ?: return@ARScene
                    val camera = frame.camera
                    if (earth.earthState != Earth.EarthState.ENABLED ||
                        earth.trackingState != TrackingState.TRACKING
                    ) return@ARScene

                    val pose = earth.cameraGeospatialPose
                    currentLat = pose.latitude
                    currentLng = pose.longitude
                    currentHeading = pose.heading
                    currentAltitude = pose.altitude

                    // pitch 계산
                    val q = pose.eastUpSouthQuaternion
                    val fx = 2f * (q[0] * q[2] + q[3] * q[1])
                    val fy = 2f * (q[1] * q[2] - q[3] * q[0])
                    val fz = 1f - 2f * (q[0] * q[0] + q[1] * q[1])
                    val horiz = sqrt(fx * fx + fz * fz)
                    currentPitch = Math.toDegrees(atan2(-fy.toDouble(), horiz.toDouble()))

                    if (pose.horizontalAccuracy < 1.5) hasAchievedHighAccuracy = true

                    if (hasAchievedHighAccuracy) {
                        trackingMessage = "위치 파악 완료 (오차: ${"%.1f".format(pose.horizontalAccuracy)}m)"

                        // 거리 업데이트
                        val results = FloatArray(1)
                        for (i in dynamicPois.indices) {
                            Location.distanceBetween(
                                currentLat, currentLng,
                                dynamicPois[i].latitude, dynamicPois[i].longitude,
                                results,
                            )
                            dynamicPois[i] = dynamicPois[i].copy(distance = results[0])
                        }

                        // 5초 간격 자동 쿼리
                        val now = System.currentTimeMillis()
                        if (now - lastQueryTime > 5000 && !isFrozen) {
                            lastQueryTime = now
                            scope.launch {
                                try {
                                    val response = api.queryPlace(
                                        PlaceQueryRequest(
                                            heading = currentHeading,
                                            user_lat = currentLat,
                                            user_lng = currentLng,
                                            user_alt = currentAltitude,
                                            pitch = currentPitch,
                                        ),
                                    )
                                    val overlay = response.ar_overlay ?: return@launch
                                    if (overlay.name.isEmpty()) return@launch

                                    // 중복 체크
                                    val alreadyExists = dynamicPois.any { it.name == overlay.name }
                                    if (alreadyExists) return@launch

                                    // 앵커 생성 — 사용자 바라보는 방향 ~50m 앞
                                    val headingRad = Math.toRadians(currentHeading)
                                    val offsetDist = 0.00045 // ~50m in degrees
                                    val anchorLat = currentLat + offsetDist * cos(headingRad)
                                    val anchorLng = currentLng + offsetDist * sin(headingRad)

                                    val newId = "poi_${System.currentTimeMillis()}"
                                    val anchor = earth.createAnchor(
                                        anchorLat, anchorLng, currentAltitude,
                                        0f, 0f, 0f, 1f,
                                    )
                                    geospatialAnchors[newId] = anchor

                                    Location.distanceBetween(
                                        currentLat, currentLng, anchorLat, anchorLng, results,
                                    )
                                    dynamicPois.add(
                                        DynamicPoi(
                                            id = newId,
                                            name = overlay.name,
                                            category = overlay.category,
                                            distance = results[0],
                                            latitude = anchorLat,
                                            longitude = anchorLng,
                                            arOverlay = overlay,
                                            docent = response.docent,
                                        ),
                                    )
                                } catch (e: Exception) {
                                    Log.e("ArExplore", "자동 쿼리 실패: ${e.message}")
                                }
                            }
                        }
                    } else {
                        trackingMessage =
                            "VPS 정밀 탐색 중... (오차: ${"%.1f".format(pose.horizontalAccuracy)}m / 1.5m 미만 필요)"
                    }

                    // 앵커 → 화면 좌표 투영
                    val newPositions = mutableMapOf<String, Offset>()
                    val viewMatrix = FloatArray(16)
                    camera.getViewMatrix(viewMatrix, 0)
                    val projMatrix = FloatArray(16)
                    camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100.0f)

                    geospatialAnchors.forEach { (id, anchor) ->
                        if (anchor.trackingState == TrackingState.TRACKING) {
                            val anchorPose = anchor.pose
                            val anchorTranslation = floatArrayOf(
                                anchorPose.tx(), anchorPose.ty(), anchorPose.tz(), 1f,
                            )
                            val viewCoords = FloatArray(4)
                            Matrix.multiplyMV(viewCoords, 0, viewMatrix, 0, anchorTranslation, 0)
                            if (viewCoords[2] <= 0) {
                                val clipCoords = FloatArray(4)
                                Matrix.multiplyMV(clipCoords, 0, projMatrix, 0, viewCoords, 0)
                                if (clipCoords[3] != 0f) {
                                    val x = ((clipCoords[0] / clipCoords[3] + 1.0f) / 2.0f) * screenWidthPx
                                    val y = ((1.0f - clipCoords[1] / clipCoords[3]) / 2.0f) * screenHeightPx
                                    newPositions[id] = Offset(x, y)
                                }
                            }
                        }
                    }
                    anchorScreenPositions = newPositions
                },
            )

            // 화면 고정 시 반투명 오버레이
            if (isFrozen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ScanPangColors.ArFreezeTint),
                )
            }

            // ── 상단 바 ──
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ScanPangColors.ArExploreScrimGradientTop,
                                ScanPangColors.ArExploreScrimGradientBottom,
                            ),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(horizontal = ScanPangDimens.arTopBarHorizontal)
                    .padding(bottom = ScanPangDimens.arTopBarBottomPadding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            maxOf(
                                ScanPangDimens.arCircleBtn36,
                                ScanPangDimens.arStatusPillHeight,
                            ),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArCircleIconButton(
                        icon = Icons.Rounded.Home,
                        contentDescription = "홈",
                        onClick = { navController.popBackStack() },
                        modifier = Modifier,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = ScanPangSpacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        ArExploreStatusPill(
                            isFrozen = isFrozen,
                            selectedFilters = categorySelection,
                            hasHighAccuracy = hasAchievedHighAccuracy,
                            onClick = {
                                if (isFrozen) {
                                    isFrozen = false
                                } else {
                                    isFilterOpen = true
                                }
                            },
                        )
                    }
                    ArCircleIconButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = "검색",
                        onClick = { isSearchOpen = true },
                        modifier = Modifier,
                    )
                }
            }

            // ── 동적 마커 + 사이드 컬럼 ──
            Box(modifier = Modifier.fillMaxSize()) {
                ArDynamicPoiMarkers(
                    dynamicPois = dynamicPois,
                    anchorScreenPositions = anchorScreenPositions,
                    onPoiClick = { poi ->
                        selectedPoi = poi.name
                        selectedPoiOverlay = poi.arOverlay
                        selectedPoiDocent = poi.docent
                        activeDetailTab = ArPoiTabBuilding
                        selectedStore = null
                    },
                )
                ArExploreSideColumn(
                    onTtsClick = {
                        isTtsOn = !isTtsOn
                        val msg = if (isTtsOn) "음성 안내 켜짐" else "음성 안내 꺼짐"
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    },
                    onCameraClick = { isFrozen = !isFrozen },
                    isTtsOn = isTtsOn,
                    isFrozen = isFrozen,
                    isTtsPlaying = isTtsPlaying,
                )
            }

            // ── 하단 채팅 섹션 ──
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                ArExploreInteractiveChatSection(
                    messages = chatMessages,
                    inputText = chatInput,
                    onInputChange = { chatInput = it },
                    onSend = send@{
                        val q = chatInput.trim()
                        if (q.isEmpty()) return@send
                        scope.launch {
                            val reply = agentService.sendMessage(q)
                            chatMessages = chatMessages +
                                ArAgentChatMessage(text = q, isUser = true) +
                                ArAgentChatMessage(text = reply, isUser = false)
                            chatInput = ""
                            ttsController.speakIfEnabled(reply, isTtsOn)
                        }
                    },
                    isSttListening = isSttListening,
                    onMicClick = mic@{
                        val h = speechHelperRef
                        if (isSttListening) {
                            h?.stopListening()
                            return@mic
                        }
                        if (h == null) {
                            scope.launch {
                                snackbarHostState.showSnackbar("음성 입력을 준비하지 못했어요")
                            }
                            return@mic
                        }
                        if (!h.isRecognitionAvailable()) {
                            scope.launch {
                                snackbarHostState.showSnackbar("이 기기에서 음성 인식을 쓸 수 없어요")
                            }
                            return@mic
                        }
                        val hasMic = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasMic) {
                            h.startListening()
                        } else {
                            pendingMicAfterPermission = true
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    listState = chatListState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── 필터 패널 ──
            AnimatedVisibility(
                visible = isFilterOpen,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ScanPangColors.ArOverlayScrimDark)
                        .clickable { isFilterOpen = false },
                ) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = ScanPangDimens.arFilterPanelHorizontal)
                            .padding(top = ScanPangSpacing.lg)
                            .clickable(enabled = false) { },
                        shape = ScanPangShapes.arFilterPanelTop,
                        color = ScanPangColors.Surface,
                        shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(ScanPangDimens.arTopBarHorizontal)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            ArExploreFilterPanelFigma(
                                categorySpecs = categoryChipSpecs,
                                categorySelection = categorySelection,
                                onCategoryToggle = { label ->
                                    categorySelection =
                                        if (label in categorySelection) {
                                            categorySelection - label
                                        } else {
                                            categorySelection + label
                                        }
                                },
                                onReset = { categorySelection = emptySet() },
                                onApply = {
                                    isFilterOpen = false
                                    // 선택된 카테고리로 convenience/query 호출
                                    categorySelection.forEach { label ->
                                        val apiCategory = categoryKeyMap[label] ?: return@forEach
                                        viewModel.searchConvenience(
                                            category = apiCategory,
                                            lat = if (currentLat != 0.0) currentLat else 37.5636,
                                            lng = if (currentLng != 0.0) currentLng else 126.9822,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // ── 검색 패널 ──
            AnimatedVisibility(
                visible = isSearchOpen,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ScanPangColors.ArOverlayScrimDark)
                        .clickable { isSearchOpen = false; showArSearchResults = false },
                ) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = ScanPangDimens.arFilterPanelHorizontal)
                            .padding(top = ScanPangSpacing.lg)
                            .clickable(enabled = false) { },
                        shape = ScanPangShapes.arSearchPanel,
                        color = ScanPangColors.Surface,
                        shadowElevation = ScanPangDimens.arPoiCardShadowElevation,
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(ScanPangDimens.arTopBarHorizontal)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(ScanPangSpacing.md),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = ScanPangColors.OnSurfaceMuted,
                                        modifier = Modifier.size(ScanPangDimens.icon20),
                                    )
                                    Text(
                                        text = "장소·메뉴 검색",
                                        style = ScanPangType.searchPlaceholderRegular,
                                        color = ScanPangColors.OnSurfacePlaceholder,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        isSearchOpen = false
                                        showArSearchResults = false
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "닫기",
                                        tint = ScanPangColors.OnSurfaceStrong,
                                    )
                                }
                            }
                            Text(
                                text = "최근 검색",
                                style = ScanPangType.sectionTitle16,
                                color = ScanPangColors.OnSurfaceStrong,
                            )
                            recentQueries.forEach { q ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showArSearchResults = true
                                        }
                                        .padding(vertical = ScanPangSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = ScanPangColors.OnSurfaceMuted,
                                        modifier = Modifier.size(ScanPangDimens.icon18),
                                    )
                                    Text(
                                        text = q,
                                        style = ScanPangType.body14Regular,
                                        color = ScanPangColors.OnSurfaceStrong,
                                    )
                                }
                            }
                            Text(
                                text = "추천 검색어",
                                style = ScanPangType.sectionTitle16,
                                color = ScanPangColors.OnSurfaceStrong,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                            ) {
                                suggestionTags.forEach { tag ->
                                    Surface(
                                        shape = ScanPangShapes.badge6,
                                        color = ScanPangColors.ArRecommendTagHalalBackground,
                                        modifier = Modifier.clickable { showArSearchResults = true },
                                    ) {
                                        Text(
                                            text = tag,
                                            modifier = Modifier.padding(
                                                horizontal = ScanPangDimens.arSearchTagHorizontalPad,
                                                vertical = ScanPangDimens.arSearchTagVerticalPad,
                                            ),
                                            style = ScanPangType.tag11Medium,
                                            color = ScanPangColors.Primary,
                                        )
                                    }
                                }
                            }
                            if (showArSearchResults) {
                                HorizontalDivider(color = ScanPangColors.OutlineSubtle)
                                Text(
                                    text = "정확도 · 거리순",
                                    style = ScanPangType.meta11SemiBold,
                                    color = ScanPangColors.OnSurfaceMuted,
                                )
                                searchHits.forEach { hit ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = ScanPangSpacing.sm),
                                    ) {
                                        Text(
                                            text = hit.title,
                                            style = ScanPangType.title14,
                                            color = ScanPangColors.OnSurfaceStrong,
                                        )
                                        Text(
                                            text = "${hit.scoreLine} · ${hit.distance}",
                                            style = ScanPangType.caption12Medium,
                                            color = ScanPangColors.OnSurfaceMuted,
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                                            modifier = Modifier.padding(top = ScanPangSpacing.sm),
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    selectedPoi = hit.title
                                                    selectedPoiOverlay = null
                                                    selectedPoiDocent = null
                                                    activeDetailTab = ArPoiTabBuilding
                                                    isSearchOpen = false
                                                    showArSearchResults = false
                                                },
                                            ) {
                                                Text(
                                                    text = "정보 보기",
                                                    color = ScanPangColors.Primary,
                                                    style = ScanPangType.body15Medium,
                                                )
                                            }
                                            TextButton(
                                                onClick = {
                                                    navController.navigate(AppRoutes.ArNavMap) {
                                                        launchSingleTop = true
                                                    }
                                                    isSearchOpen = false
                                                    showArSearchResults = false
                                                },
                                            ) {
                                                Text(
                                                    text = "길안내",
                                                    color = ScanPangColors.Primary,
                                                    style = ScanPangType.body15Medium,
                                                )
                                            }
                                        }
                                    }
                                    HorizontalDivider(color = ScanPangColors.OutlineSubtle)
                                }
                            }
                        }
                    }
                }
            }

            // ── POI 상세 패널 ──
            selectedPoi?.let { poi ->
                ArPoiFloatingDetailOverlay(
                    poiName = poi,
                    activeDetailTab = activeDetailTab,
                    onActiveDetailTabChange = { activeDetailTab = it },
                    onDismiss = {
                        selectedPoi = null
                        selectedPoiOverlay = null
                        selectedPoiDocent = null
                        selectedStore = null
                        activeDetailTab = ArPoiTabBuilding
                    },
                    onFloorStoreClick = { selectedStore = it },
                    onSave = {
                        scope.launch { snackbarHostState.showSnackbar("저장되었습니다") }
                    },
                    modifier = Modifier.fillMaxSize(),
                    arOverlay = selectedPoiOverlay ?: placeResult?.ar_overlay,
                    docent = selectedPoiDocent ?: placeResult?.docent,
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
}

/**
 * 동적 POI 마커 레이어 — ARCore anchor의 화면 좌표에 ArPoiCard 배치.
 */
@Composable
private fun BoxScope.ArDynamicPoiMarkers(
    dynamicPois: List<DynamicPoi>,
    anchorScreenPositions: Map<String, Offset>,
    onPoiClick: (DynamicPoi) -> Unit,
) {
    dynamicPois.forEach { poi ->
        val screenPos = anchorScreenPositions[poi.id] ?: return@forEach
        val xPx = screenPos.x.roundToInt()
        val yPx = screenPos.y.roundToInt()

        Box(
            modifier = Modifier.offset { IntOffset(xPx, yPx) },
        ) {
            ArPoiCard(
                title = if (poi.isPending) "분석 중..." else poi.name,
                subtitle = buildString {
                    if (poi.category.isNotEmpty()) append("${poi.category} · ")
                    append("${"%.0f".format(poi.distance)}m")
                },
                onClick = if (!poi.isPending) {
                    { onPoiClick(poi) }
                } else null,
            )
        }
    }
}

@Composable
private fun ArExploreStatusPill(
    isFrozen: Boolean,
    selectedFilters: Set<String>,
    hasHighAccuracy: Boolean = true,
    onClick: () -> Unit,
) {
    when {
        isFrozen -> {
            Surface(
                modifier = Modifier
                    .height(ScanPangDimens.arStatusPillHeight)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
                shape = CircleShape,
                color = ScanPangColors.Primary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = ScanPangDimens.arStatusPillHorizontalPad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(ScanPangDimens.icon18),
                        tint = Color.White,
                    )
                    Text(
                        text = "화면 고정 중",
                        style = ScanPangType.arStatusPill15,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(ScanPangDimens.arNavDestinationChevron),
                        tint = Color.White,
                    )
                }
            }
        }
        !hasHighAccuracy -> {
            Surface(
                modifier = Modifier
                    .height(ScanPangDimens.arStatusPillHeight)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
                shape = CircleShape,
                color = ScanPangColors.ArOverlayWhite80,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = ScanPangDimens.arStatusPillHorizontalPad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CropFree,
                        contentDescription = null,
                        modifier = Modifier.size(ScanPangDimens.icon18),
                        tint = ScanPangColors.OnSurfaceStrong,
                    )
                    Text(
                        text = "VPS 탐색 중...",
                        style = ScanPangType.arStatusPill15,
                        color = ScanPangColors.OnSurfaceStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        selectedFilters.isEmpty() -> {
            Surface(
                modifier = Modifier
                    .height(ScanPangDimens.arStatusPillHeight)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
                shape = CircleShape,
                color = ScanPangColors.ArOverlayWhite80,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = ScanPangDimens.arStatusPillHorizontalPad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CropFree,
                        contentDescription = null,
                        modifier = Modifier.size(ScanPangDimens.icon18),
                        tint = ScanPangColors.OnSurfaceStrong,
                    )
                    Text(
                        text = "AR 탐색 중",
                        style = ScanPangType.arStatusPill15,
                        color = ScanPangColors.OnSurfaceStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(ScanPangDimens.arNavDestinationChevron),
                        tint = ScanPangColors.OnSurfacePlaceholder,
                    )
                }
            }
        }
        else -> {
            val label = buildFilterPillLabel(selectedFilters)
            Surface(
                modifier = Modifier
                    .height(ScanPangDimens.arStatusPillHeight)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
                shape = CircleShape,
                color = ScanPangColors.Primary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = ScanPangDimens.arStatusPillHorizontalPad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(ScanPangDimens.icon18),
                        tint = Color.White,
                    )
                    Text(
                        text = label,
                        style = ScanPangType.arStatusPill15,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(ScanPangDimens.arNavDestinationChevron),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

private fun buildFilterPillLabel(selected: Set<String>): String {
    val list = selected.toList()
    if (list.isEmpty()) return ""
    if (list.size == 1) return list[0]
    return "${list[0]} 외 ${list.size - 1}개"
}
