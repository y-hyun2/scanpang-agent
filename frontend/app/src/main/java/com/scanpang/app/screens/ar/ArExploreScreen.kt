package com.scanpang.app.screens.ar

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.opengl.Matrix
import android.speech.SpeechRecognizer
import android.util.Log
import android.app.Activity
import android.graphics.Bitmap
import android.view.PixelCopy
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.HeadsetOff
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
import com.scanpang.app.data.remote.PlaceQueryRequest
import com.scanpang.app.data.remote.RetrofitClient
import com.scanpang.app.data.remote.SearchRequest
import com.scanpang.app.data.remote.SearchResultItem
import com.scanpang.app.data.remote.ScanPangViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.scanpang.app.components.ar.ArAgentChatMessage
import com.scanpang.app.components.ar.ArCircleIconButton
import com.scanpang.app.components.ar.ArExploreInteractiveChatSection
import com.scanpang.app.components.ar.ArExploreSearchHitUi
import com.scanpang.app.components.ar.ArExploreSearchPanelContent
import com.scanpang.app.components.ar.ArFloorStoreGuideOverlay
import com.scanpang.app.components.ar.ArPoiFloatingDetailOverlay
import com.scanpang.app.components.ar.ArPoiTabBuilding
import com.scanpang.app.components.ar.ArExploreFilterPanelFigma
import com.scanpang.app.components.ar.ArExploreSideColumn
import com.scanpang.app.components.ar.ArNavWhiteFab
import com.scanpang.app.components.ar.arExploreCategoryChipSpecs
import com.scanpang.app.components.ar.ArCategoryIconBadge
import com.scanpang.app.components.ar.ArPoiCard
import com.scanpang.app.data.AppSettingsPreferences
import com.scanpang.app.data.SearchHistoryPreferences
import com.scanpang.app.data.TtsState
import com.scanpang.app.navigation.AppRoutes
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import androidx.compose.material3.HorizontalDivider
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
import com.scanpang.app.data.remote.Building
import com.scanpang.app.data.remote.GeoJsonMultiPolygon
import com.scanpang.app.components.ar.buildFovPolygon
import com.scanpang.app.components.ar.clipPolygon
import com.scanpang.app.components.ar.computeCentroid
import com.scanpang.app.components.ar.computeAngularFootprint
import com.scanpang.app.components.ar.computeFrontEdgeMidpoint
import com.scanpang.app.components.ar.computePolygonCentroid
import com.scanpang.app.components.ar.isRayBlockedByPolygon
import kotlin.math.abs

private data class DynamicPoi(
    val id: String,
    val name: String,
    val category: String = "",
    val distance: Float = 0f,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val arOverlay: ArOverlay? = null,
    val isPending: Boolean = false,
    val storeCount: Int = 0,
    val matchingStores: List<SearchResultItem> = emptyList(),
    val ufid: String? = null,
)

private data class BuildingCandidate(
    val b: Building,
    val centerLat: Double,
    val centerLng: Double,
    val dist: Float,
    val centerBearing: Double,      // 사용자 → 건물 중심 방향 (0~360°)
    val angularHalfWidth: Double,   // 사용자 위치에서 건물이 차지하는 시야각의 절반
    val visiblePolygon: List<Pair<Double, Double>>,  // FOV ∩ 건물 polygon (Pair<lng, lat>)
)

// place_id = "__outdoor__" 매장들 — 건물 폴리곤 밖에 있어 store_details.place_id 로
// 건물 매칭이 안 되는 매장. 매장 좌표끼리 3m 이내인 것들을 한 마커로 묶는다.
private data class OutdoorCluster(
    val id: String,
    val lat: Double,
    val lng: Double,
    val stores: List<SearchResultItem>,
    val distance: Float,
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
    // 마커 탭 시 /place/store 응답 — ArFloorStoreGuideOverlay 메타 라인의 category·영업중 표시 원천
    val storeResult by viewModel.storeResult.collectAsState()
    val storeProgress by viewModel.storeProgress.collectAsState()
    val context = LocalContext.current

    val appContext = context.applicationContext
    val appSettingsPrefs = remember { AppSettingsPreferences(appContext) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val chatListState = rememberLazyListState()
    var chatInput by remember { mutableStateOf("") }
    // 응답 도착 전 보내기 연타로 같은 query 가 backend 에 N번 전송되던 버그
    // (POST /ar/agent/chat 6번씩 찍힘) + UI 가 무반응으로 보이던 문제 차단.
    var isChatSending by remember { mutableStateOf(false) }
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
    var filterStores by remember { mutableStateOf<List<SearchResultItem>>(emptyList()) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var showArSearchResults by remember { mutableStateOf(false) }
    var arSearchQuery by remember { mutableStateOf("") }

    BackHandler(enabled = isSearchOpen) {
        arSearchQuery = ""
        showArSearchResults = false
        isSearchOpen = false
    }

    var isFrozen by remember { mutableStateOf(false) }
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var frozenDynamicPois by remember { mutableStateOf<List<DynamicPoi>>(emptyList()) }
    var frozenAnchorPositions by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }
    val currentView = LocalView.current

    LaunchedEffect(Unit) { TtsState.init(appSettingsPrefs) }
    val isTtsOn by TtsState.enabled.collectAsState()

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

    val onSttResult: (String) -> Unit = stt@{ text ->
        if (isChatSending) return@stt
        if (text.isBlank()) return@stt
        isChatSending = true
        chatInput = ""
        chatMessages = chatMessages + ArAgentChatMessage(text = text, isUser = true)
        scope.launch {
            try {
                val reply = sendVoiceMessage(text, agentService)
                chatMessages = chatMessages + ArAgentChatMessage(text = reply, isUser = false)
                ttsController.speakIfEnabled(reply, isTtsOn)
            } finally {
                isChatSending = false
            }
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
    var activeDetailTab by remember { mutableStateOf(ArPoiTabBuilding) }
    var selectedStore by remember { mutableStateOf<String?>(null) }
    var storeListPoi by remember { mutableStateOf<DynamicPoi?>(null) }

    BackHandler(enabled = selectedPoi != null && selectedStore == null) {
        selectedPoi = null
        selectedPoiOverlay = null
    }
    BackHandler(enabled = selectedStore != null) {
        selectedStore = null
    }

    val categoryChipSpecs = remember { arExploreCategoryChipSpecs() }
    var arSearchHistoryTick by remember { mutableIntStateOf(0) }
    val searchHistoryPrefs = remember(appContext) { SearchHistoryPreferences(appContext) }
    val arExploreDemoHits = remember {
        listOf(
            ArExploreSearchHitUi("할랄가든 명동점", "식당", "120m", "할랄 인증"),
            ArExploreSearchHitUi("명동성당", "관광지", "350m", null),
            ArExploreSearchHitUi("우리은행 환전소", "환전소", "80m", null),
        )
    }
    val arRecentQueries = remember(arSearchHistoryTick, isSearchOpen) {
        if (isSearchOpen) searchHistoryPrefs.getRecent() else emptyList()
    }
    val displayedArHits = remember(arSearchQuery, showArSearchResults, arExploreDemoHits) {
        if (!showArSearchResults) emptyList()
        else filterArExploreHits(arSearchQuery, arExploreDemoHits)
    }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            arSearchQuery = ""
            showArSearchResults = false
        }
    }

    val submitArSearch: () -> Unit = {
        val q = arSearchQuery.trim()
        if (q.isNotEmpty()) {
            searchHistoryPrefs.add(q)
            arSearchHistoryTick++
            showArSearchResults = true
        }
    }

    // ── ARCore Geospatial 상태 ──
    val engine = rememberEngine()
    val api = remember { RetrofitClient.api }
    var hasAchievedHighAccuracy by remember { mutableStateOf(false) }
    var showInitOverlay by remember { mutableStateOf(true) }
    var trackingMessage by remember { mutableStateOf("ARCore 초기화 중...") }
    var currentHeading by remember { mutableStateOf(0.0) }
    var currentAltitude by remember { mutableStateOf(0.0) }
    var currentPitch by remember { mutableStateOf(0.0) }
    var currentLat by remember { mutableStateOf(0.0) }
    var currentLng by remember { mutableStateOf(0.0) }
    // VPS 안정화 시점의 고도를 한번만 캐싱 — anchor 생성에 일관되게 사용.
    // currentAltitude 를 매번 쓰면 ARCore 가 고도를 재추정할 때마다 새로 생성되는 anchor 가
    // 서로 다른 높이에 박혀서, 필터 전환 후 건물 마커가 처음과 다른 높이로 보이는 문제 발생.
    var stableAltitude by remember { mutableStateOf<Double?>(null) }

    val geospatialAnchors = remember { mutableStateMapOf<String, Anchor>() }
    var anchorScreenPositions by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }
    val dynamicPois = remember { mutableStateListOf<DynamicPoi>() }
    var lastProcessedChunkCell by remember { mutableStateOf<String?>(null) }
    var lastVisibilityCalcTime by remember { mutableStateOf(0L) }

    LaunchedEffect(isFrozen) {
        if (isFrozen) {
            // 마커 상태를 먼저 스냅샷으로 저장
            frozenDynamicPois = dynamicPois.toList()
            frozenAnchorPositions = anchorScreenPositions.toMap()
            // ARCore가 렌더링하는 SurfaceView만 캡처 → Compose UI 없이 배경만 얻음
            val surfaceView = currentView.findFirstSurfaceView()
            if (surfaceView != null) {
                val bmp = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
                PixelCopy.request(surfaceView, bmp, { result ->
                    frozenBitmap = if (result == PixelCopy.SUCCESS) bmp else null
                }, Handler(Looper.getMainLooper()))
            } else {
                // fallback: 윈도우 전체 캡처
                val window = (currentView.context as? Activity)?.window ?: return@LaunchedEffect
                val bmp = Bitmap.createBitmap(currentView.width, currentView.height, Bitmap.Config.ARGB_8888)
                PixelCopy.request(window, bmp, { result ->
                    frozenBitmap = if (result == PixelCopy.SUCCESS) bmp else null
                }, Handler(Looper.getMainLooper()))
            }
        } else {
            frozenBitmap = null
            frozenDynamicPois = emptyList()
            frozenAnchorPositions = emptyMap()
        }
    }

    // 화면 크기 (앵커 → 화면 좌표 투영용)
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }.toInt()
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }.toInt()

    DisposableEffect(Unit) {
        onDispose {
            // detach() 호출 금지 — disposal 순서상 ARScene 이 ArSession 을 먼저 파괴하면
            // 죽은 session 의 anchor 를 건드려서 native SIGSEGV. ARCore 가 session 파괴 시
            // anchor 도 같이 정리해주므로 reference 만 비우면 됨.
            geospatialAnchors.clear()
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        showInitOverlay = false
    }

    LaunchedEffect(categorySelection) {
        // 기존 building / outdoor 마커 전부 제거 — 다음 프레임 사이클에서 필터 기준으로 재생성
        val staleKeys = geospatialAnchors.keys.filter {
            it.startsWith("building_") || it.startsWith("outdoor_")
        }.toList()
        staleKeys.forEach { k ->
            geospatialAnchors[k]?.detach()
            geospatialAnchors.remove(k)
        }
        dynamicPois.removeAll { it.id.startsWith("building_") || it.id.startsWith("outdoor_") }
        // API 호출 전에 즉시 비움 — 이전 카테고리의 stale 데이터로 마커가 생기는 걸 방지.
        // (안 비우면 visibility update가 150ms 안에 돌면서 이전 filterStores로 마커를 만들고,
        //  그 마커 id가 existingIds에 박혀버려서 API 응답이 와도 새로 안 만들어짐.)
        filterStores = emptyList()

        if (categorySelection.isNotEmpty()) {
            val results = mutableListOf<SearchResultItem>()
            for (cat in categorySelection) {
                try {
                    val resp = api.searchPlaces(
                        SearchRequest(query = cat, lat = currentLat, lng = currentLng, limit = 100)
                    )
                    results.addAll(resp.results)
                } catch (e: Exception) {
                    Log.e("ArExplore", "카테고리 필터 검색 실패 ($cat): ${e.message}")
                }
            }
            filterStores = results
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
                    config.depthMode = Config.DepthMode.DISABLED
                },
                onSessionUpdated = { session, frame ->
                    if (isFrozen) return@ARScene
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

                    if (pose.horizontalAccuracy < 1.5) {
                        hasAchievedHighAccuracy = true
                        // 첫 high-accuracy 도달 시점의 고도를 고정 — 이후 anchor 생성에 일관되게 사용
                        if (stableAltitude == null) stableAltitude = pose.altitude
                    }

                    // 10초마다 상태 종합 로그
                    if (System.currentTimeMillis() % 10000 < 100) {
                        Log.d("SCANPANG_AR", buildString {
                            append("━━━━━━━━━━ AR 상태 ━━━━━━━━━━\n")
                            append("  VPS 오차: ${"%.2f".format(pose.horizontalAccuracy)}m  (high=$hasAchievedHighAccuracy)\n")
                            append("  위치: lat=${"%.6f".format(currentLat)}, lng=${"%.6f".format(currentLng)}\n")
                            append("  방향: heading=${"%.1f".format(currentHeading)}°, pitch=${"%.1f".format(currentPitch)}°\n")
                            append("  H3 셀: ${viewModel.currentH3Cell.value ?: "(아직 로드 안 됨)"}\n")
                            append("  청크 내 건물: ${viewModel.buildingsChunk.value.size}개")
                        })
                    }

                    // ARCore 위치를 채팅 에이전트에 실시간 반영 → /ar/agent/chat 호출 시 정확한 위치 전달
                    agentService.updatePosition(currentLat, currentLng, currentHeading)

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

                        // H3 청크 갱신
                        viewModel.updateLocationForChunk(currentLat, currentLng)

                        val now2 = System.currentTimeMillis()
                        if (now2 - lastVisibilityCalcTime > 150) {
                            lastVisibilityCalcTime = now2

                            val cache = viewModel.buildingsCache.value
                            if (cache.isNotEmpty()) {
                                // FOV polygon 계산 (이번 1초 사이클의 시야)
                                val fov = buildFovPolygon(currentLat, currentLng, currentHeading)

                                // 100m 이내 + FOV 안 후보 추출
                                val candidates = cache.values
                                    .mapNotNull { b ->
                                        val center = computeCentroid(b.geom) ?: return@mapNotNull null
                                        val footprint = computeAngularFootprint(b.geom, currentLat, currentLng) ?: return@mapNotNull null
                                        val r = FloatArray(1)
                                        Location.distanceBetween(currentLat, currentLng, center.first, center.second, r)
                                        val dist = r[0]
                                        if (dist >= 70f) return@mapNotNull null

                                        // FOV 필터 — 사용자 시야 안에 들어온 부분만
                                        val firstRing = b.geom.coordinates.firstOrNull()?.firstOrNull() ?: return@mapNotNull null
                                        val buildingPoly = firstRing.map { Pair(it[0], it[1]) }  // [lng, lat]
                                        val visible = clipPolygon(buildingPoly, fov)
                                        if (visible.isEmpty()) return@mapNotNull null   // 시야 밖이면 제외

                                        BuildingCandidate(
                                            b = b,
                                            centerLat = center.first,
                                            centerLng = center.second,
                                            dist = dist,
                                            centerBearing = footprint.first,
                                            angularHalfWidth = footprint.second,
                                            visiblePolygon = visible,
                                        )
                                    }
                                    .sortedBy { it.dist }

                                // Occlusion 필터링 — 사용자 → 후보 마커 ray가 더 가까운 건물 polygon을 통과하면 가려진 걸로
                                val visibleCandidates = mutableListOf<BuildingCandidate>()
                                for (cand in candidates) {
                                    // 후보 마커 위치 (front edge midpoint)
                                    val candMarkerPos = computeFrontEdgeMidpoint(
                                        cand.visiblePolygon, currentLat, currentLng
                                    ) ?: Pair(cand.centerLat, cand.centerLng)

                                    // 중심점이 10m 이내인 마커가 이미 있으면 같은 건물 중복 레코드로 간주
                                    val isDuplicate = visibleCandidates.any { other ->
                                        val r = FloatArray(1)
                                        Location.distanceBetween(
                                            other.centerLat, other.centerLng,
                                            cand.centerLat, cand.centerLng, r,
                                        )
                                        r[0] < 10f
                                    }
                                    if (isDuplicate) continue

                                    val isOccluded = visibleCandidates.any { occluder ->
                                        // 거리 차 5m 미만이면 옆에 나란히 있는 거 — 가린다고 판단 안 함
                                        if (cand.dist - occluder.dist < 5f) return@any false

                                        // occluder polygon (위경도 외곽 ring)
                                        val occluderPoly = occluder.b.geom.coordinates.firstOrNull()
                                            ?.firstOrNull()?.map { Pair(it[0], it[1]) } ?: return@any false

                                        // 사용자 → 후보 마커 ray가 occluder polygon을 통과하는지
                                        isRayBlockedByPolygon(
                                            currentLng, currentLat,
                                            candMarkerPos.second, candMarkerPos.first,   // lat, lng → lng, lat
                                            occluderPoly,
                                        )
                                    }

                                    if (!isOccluded) {
                                        visibleCandidates.add(cand)
                                    }
                                    if (visibleCandidates.size >= 30) break
                                }

                                // Outdoor 매장 클러스터 — 필터 모드에서만, 좌표 3m 이내끼리 묶음
                                val outdoorClusters: List<OutdoorCluster> =
                                    if (categorySelection.isEmpty()) emptyList()
                                    else computeOutdoorClusters(
                                        outdoorStores = filterStores.filter { it.place_id == "__outdoor__" },
                                        userLat = currentLat,
                                        userLng = currentLng,
                                        fov = fov,
                                        maxDistanceM = 70f,
                                        groupRadiusM = 3f,
                                    )

                                // 새 visible ID 집합 (건물 + outdoor 클러스터)
                                val newVisibleIds: Set<String> =
                                    visibleCandidates.map { cand ->
                                        "building_${cand.b.ufid ?: cand.b.h3_index_10}_${cand.b.hashCode()}"
                                    }.toSet() + outdoorClusters.map { it.id }.toSet()

                                // 더 이상 visible 아닌 건물/outdoor 라벨 제거 (멀어진 / 가려진 / FOV 밖)
                                val currentDynamicIds = dynamicPois
                                    .filter { it.id.startsWith("building_") || it.id.startsWith("outdoor_") }
                                    .map { it.id }
                                val toRemove = currentDynamicIds.filter { it !in newVisibleIds }
                                toRemove.forEach { id ->
                                    geospatialAnchors[id]?.detach()
                                    geospatialAnchors.remove(id)
                                }
                                dynamicPois.removeAll {
                                    (it.id.startsWith("building_") || it.id.startsWith("outdoor_")) &&
                                            it.id !in newVisibleIds
                                }

                                // 새 visible 건물 — anchor 생성 또는 위치 갱신
                                val existingIds = dynamicPois.map { it.id }.toSet()
                                visibleCandidates.forEach { cand ->
                                    val id = "building_${cand.b.ufid ?: cand.b.h3_index_10}_${cand.b.hashCode()}"

                                    // 마커 위치 — visible polygon 무게중심
                                    val markerPos = computePolygonCentroid(cand.visiblePolygon)
                                        ?: Pair(cand.centerLat, cand.centerLng)

                                    // 마커를 사용자 눈높이와 동일한 고도에 배치
                                    // (render_height를 더하면 가까운 건물에서 마커가 하늘로 올라가는 문제 발생)
                                    // stableAltitude 가 있으면 그걸 사용 — anchor 재생성마다 고도가 달라지는 문제 방지
                                    val labelAltitude = stableAltitude ?: currentAltitude

                                    if (id !in existingIds) {
                                        // 새 건물 — 필터 미적용: 건물 마커, 필터 적용: 매칭 매장 마커
                                        val poiToAdd: DynamicPoi? = if (categorySelection.isEmpty()) {
                                            DynamicPoi(
                                                id = id,
                                                name = cand.b.bld_nm ?: "이름 없는 건물",
                                                category = "건물",
                                                distance = cand.dist,
                                                latitude = markerPos.first,
                                                longitude = markerPos.second,
                                                ufid = cand.b.ufid.ifEmpty { null },
                                            )
                                        } else {
                                            val nearby = filterStores.filter { store ->
                                                store.place_id == cand.b.ufid
                                            }
                                            if (nearby.isEmpty()) null else {
                                                val picked = nearby.random()
                                                DynamicPoi(
                                                    id = id,
                                                    name = picked.store_name,
                                                    category = categorySelection.firstOrNull()
                                                        ?: picked.category ?: "",
                                                    distance = cand.dist,
                                                    latitude = markerPos.first,
                                                    longitude = markerPos.second,
                                                    storeCount = nearby.size,
                                                    matchingStores = nearby,
                                                )
                                            }
                                        }
                                        if (poiToAdd != null) try {
                                            val anchor = earth.createAnchor(
                                                markerPos.first, markerPos.second, labelAltitude,
                                                0f, 0f, 0f, 1f,
                                            )
                                            geospatialAnchors[id] = anchor
                                            dynamicPois.add(poiToAdd)
                                        } catch (e: Exception) {
                                            Log.e("ArExplore", "건물 앵커 실패 ${cand.b.bld_nm}: ${e.message}")
                                        }
                                    } else {
                                        // 기존 건물 — centroid 1m 이상 변할 때만 anchor 재생성
                                        val existingPoi = dynamicPois.firstOrNull { it.id == id } ?: return@forEach
                                        val r = FloatArray(1)
                                        Location.distanceBetween(
                                            existingPoi.latitude, existingPoi.longitude,
                                            markerPos.first, markerPos.second, r,
                                        )
                                        if (r[0] > 1f) {
                                            try {
                                                val newAnchor = earth.createAnchor(
                                                    markerPos.first, markerPos.second, labelAltitude,
                                                    0f, 0f, 0f, 1f,
                                                )
                                                geospatialAnchors[id]?.detach()
                                                geospatialAnchors[id] = newAnchor
                                                val idx = dynamicPois.indexOfFirst { it.id == id }
                                                if (idx != -1) {
                                                    dynamicPois[idx] = dynamicPois[idx].copy(
                                                        latitude = markerPos.first,
                                                        longitude = markerPos.second,
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                Log.e("ArExplore", "앵커 갱신 실패 ${cand.b.bld_nm}: ${e.message}")
                                            }
                                        }
                                        // 1m 이내 변화면 anchor 그대로 — ARCore가 자동으로 부드럽게 추적
                                    }
                                }

                                // Outdoor 클러스터 마커 — 매장 좌표에 직접 anchor 생성
                                outdoorClusters.forEach { cluster ->
                                    if (cluster.id !in existingIds) {
                                        val firstStore = cluster.stores.first()
                                        val poiToAdd = DynamicPoi(
                                            id = cluster.id,
                                            name = firstStore.store_name,
                                            // 필터 칩 키워드("약국", "화장실" 등)를 우선 — Kakao raw 카테고리
                                            // 문자열은 categoryIcon 매칭이 안 돼서 기본 핀 아이콘이 뜨는 문제 방지.
                                            category = categorySelection.firstOrNull() ?: firstStore.category ?: "",
                                            distance = cluster.distance,
                                            latitude = cluster.lat,
                                            longitude = cluster.lng,
                                            storeCount = cluster.stores.size,
                                            matchingStores = cluster.stores,
                                        )
                                        try {
                                            val anchor = earth.createAnchor(
                                                cluster.lat, cluster.lng, stableAltitude ?: currentAltitude,
                                                0f, 0f, 0f, 1f,
                                            )
                                            geospatialAnchors[cluster.id] = anchor
                                            dynamicPois.add(poiToAdd)
                                        } catch (e: Exception) {
                                            Log.e("ArExplore", "outdoor 앵커 실패 ${firstStore.store_name}: ${e.message}")
                                        }
                                    }
                                    // 이미 있는 outdoor 클러스터는 위치 안 바뀌므로 갱신 불필요
                                    // (cluster id 가 store id 해시라서 멤버십 변하면 자동 재생성)
                                }

                                Log.d("ArExplore",
                                    "visibility 갱신: 건물 ${visibleCandidates.size}개, outdoor 클러스터 ${outdoorClusters.size}개 " +
                                            "(캐시 ${cache.size}, FOV+100m 통과 ${candidates.size}, " +
                                            "occlusion ${candidates.size - visibleCandidates.size}개 제외, " +
                                            "제거 ${toRemove.size})")
                            }
                        }
                    } else {
                        trackingMessage =
                            "VPS 정밀 탐색 중... (오차: ${"%.1f".format(pose.horizontalAccuracy)}m / 1.5m 미만 필요)"
                    }

                    // 앵커 → 화면 좌표 투영
                    run {
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
                                    val w = clipCoords[3]
                                    if (w != 0f) {
                                        val ndcX = clipCoords[0] / w
                                        val ndcY = clipCoords[1] / w
                                        // NDC 범위 밖 anchor는 화면 바깥이므로 표시 안 함
                                        if (ndcX >= -1f && ndcX <= 1f && ndcY >= -1f && ndcY <= 1f) {
                                            val x = ((ndcX + 1.0f) / 2.0f) * screenWidthPx
                                            val y = ((1.0f - ndcY) / 2.0f) * screenHeightPx
                                            newPositions[id] = Offset(x, y)
                                        }
                                    }
                                }
                            }
                        }
                        anchorScreenPositions = newPositions
                    }
                },
            )

            // ── Screen Freeze 오버레이 ──
            val bitmap = frozenBitmap
            if (isFrozen && bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ScanPangColors.ArFreezeTint),
                )
            }

            // AR 엔진 초기화 중 노이즈 화면 가림 (2초 타임아웃)
            if (showInitOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "위치 파악 중...",
                        style = ScanPangType.arStatusPill15,
                        color = Color.White,
                    )
                }
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
                    .padding(top = 8.dp)
                    .padding(horizontal = ScanPangDimens.arTopBarHorizontal)
                    .padding(bottom = ScanPangDimens.arTopBarBottomPadding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            maxOf(
                                ScanPangDimens.arSideFab44,
                                ScanPangDimens.arStatusPillHeight,
                            ),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArNavWhiteFab(
                        icon = if (isTtsOn) Icons.Rounded.Headset else Icons.Rounded.HeadsetOff,
                        contentDescription = "음성 안내",
                        onClick = {
                            TtsState.toggle(appSettingsPrefs)
                            val msg = if (isTtsOn) "음성 안내 꺼짐" else "음성 안내 켜짐"
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        },
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
                                if (isFrozen) isFrozen = false
                                else isFilterOpen = true
                            },
                        )
                    }
                    ArNavWhiteFab(
                        icon = Icons.Rounded.Search,
                        contentDescription = "검색",
                        onClick = { isSearchOpen = true },
                    )
                }
            }

            // ── 동적 마커 + 사이드 컬럼 ──
            Box(modifier = Modifier.fillMaxSize()) {

                ArDynamicPoiMarkers(
                    dynamicPois = if (isFrozen) frozenDynamicPois else dynamicPois,
                    anchorScreenPositions = if (isFrozen) frozenAnchorPositions else anchorScreenPositions,
                    onPoiClick = { poi ->
                        if (poi.matchingStores.isNotEmpty()) {
                            // 필터 모드 — 매장 마커
                            if (poi.storeCount > 1) {
                                storeListPoi = poi
                            } else {
                                val store = poi.matchingStores.first()
                                navController.navigate(
                                    AppRoutes.placeDetailRoute(store.category_key ?: "other", store.id)
                                )
                            }
                        } else {
                            // 건물 마커 — 건물 상세 플로우
                            selectedPoi = poi.name
                            selectedPoiOverlay = poi.arOverlay
                            activeDetailTab = ArPoiTabBuilding
                            if (poi.ufid != null) {
                                viewModel.queryPlace(
                                    heading = currentHeading,
                                    lat = currentLat,
                                    lng = currentLng,
                                    ufid = poi.ufid,
                                )
                            }
                        }
                    },
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            end = ScanPangDimens.arSideColumnEnd,
                            top = ScanPangDimens.arSideColumnTop,
                        ),
                    verticalArrangement = Arrangement.spacedBy(ScanPangDimens.arSideIconGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ArNavWhiteFab(
                        icon = Icons.Rounded.CameraAlt,
                        contentDescription = "화면 고정",
                        onClick = { isFrozen = !isFrozen },
                        isActive = isFrozen,
                    )
                }
            }

            // 하단 그라데이션 — transparent → white 50%
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.5f)),
                        ),
                    ),
            )

            // ── 하단 채팅 섹션 ──
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = ScanPangDimens.mainTabContentBottomInset - 16.dp),
            ) {
                ArExploreInteractiveChatSection(
                    messages = chatMessages,
                    inputText = chatInput,
                    onInputChange = { chatInput = it },
                    onSend = send@{
                        if (isChatSending) return@send
                        val q = chatInput.trim()
                        if (q.isEmpty()) return@send
                        isChatSending = true
                        chatInput = ""
                        chatMessages = chatMessages + ArAgentChatMessage(text = q, isUser = true)
                        scope.launch {
                            try {
                                val reply = agentService.sendMessage(q)
                                chatMessages = chatMessages + ArAgentChatMessage(text = reply, isUser = false)
                                ttsController.speakIfEnabled(reply, isTtsOn)
                            } finally {
                                isChatSending = false
                            }
                        }
                    },
                    isSending = isChatSending,
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
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .clickable { isFilterOpen = false },
                ) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = ScanPangDimens.arFilterPanelHorizontal)
                            .padding(top = ScanPangDimens.arFilterPanelTopOffset)
                            .clickable(enabled = false) { },
                        shape = RoundedCornerShape(16.dp),
                        color = ScanPangColors.ArOverlayWhite93,
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
                                        if (label in categorySelection) emptySet()
                                        else setOf(label)
                                },
                                onReset = { categorySelection = emptySet() },
                                onApply = { isFilterOpen = false },
                            )
                        }
                    }
                }
            }

            // ── 검색 패널 ──
            AnimatedVisibility(
                visible = isSearchOpen,
                enter = slideInVertically { -it },
                exit = slideOutVertically { -it },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .clickable { isSearchOpen = false; showArSearchResults = false },
                ) {
                    ArExploreSearchPanelContent(
                        query = arSearchQuery,
                        onQueryChange = { arSearchQuery = it },
                        onSubmitSearch = submitArSearch,
                        recentQueries = arRecentQueries,
                        onRecentQueryClick = { q ->
                            arSearchQuery = q
                            searchHistoryPrefs.add(q)
                            arSearchHistoryTick++
                            showArSearchResults = true
                        },
                        onRecentQueryRemove = { q ->
                            searchHistoryPrefs.remove(q)
                            arSearchHistoryTick++
                        },
                        onRecentClearAll = {
                            searchHistoryPrefs.clearAll()
                            arSearchHistoryTick++
                        },
                        showResultList = showArSearchResults,
                        searchHits = displayedArHits,
                        onHitViewInfo = { hit ->
                            selectedPoi = hit.title
                            activeDetailTab = ArPoiTabBuilding
                            isSearchOpen = false
                            showArSearchResults = false
                        },
                        onHitStartNav = { hit ->
                            navController.navigate(AppRoutes.arNavMapRoute(hit.title)) { launchSingleTop = true }
                            isSearchOpen = false
                            showArSearchResults = false
                        },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(horizontal = ScanPangDimens.arFilterPanelHorizontal)
                            .padding(top = ScanPangDimens.arFilterPanelTopOffset)
                            .clickable(enabled = false) { },
                    )
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
                        selectedStore = null
                        activeDetailTab = ArPoiTabBuilding
                    },
                    onFloorStoreClick = {
                        // 건물 패널 닫고 매장 floating 으로 전환 — 두 fillMaxSize 패널이
                        // 동시에 그려지면 겹쳐서 매장 카드가 잘려보임.
                        selectedStore = it
                        selectedPoi = null
                    },
                    onSave = {
                        scope.launch { snackbarHostState.showSnackbar("저장되었습니다") }
                    },
                    modifier = Modifier.fillMaxSize(),
                    arOverlay = selectedPoiOverlay ?: placeResult?.ar_overlay,
                )
            }

            selectedStore?.let { store ->
                // 매장 선택 시 백엔드 풀필드(category·is_open_now·...) 받아오기.
                // 건물 ufid 를 place_id 로 전달 — store_details cache key 일관성.
                // selectedPoiOverlay 가 우선(층별탭 시나리오), 없으면 placeResult.
                val placeUfid = (selectedPoiOverlay?.ufid ?: placeResult?.ar_overlay?.ufid).orEmpty()
                LaunchedEffect(store) { viewModel.streamStore(placeId = placeUfid, storeName = store) }
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
                    storeProgress = storeProgress,
                )
            }

            // ── 필터 매장 리스트 ──
            // Dialog 로 렌더링해서 하단 탭 바보다 위 레이어에 표시.
            // (같은 Box 안 AnimatedVisibility 는 ScanPangApp 의 ScanPangTabBar 보다
            //  z-order 가 낮아 탭 바에 가려짐.)
            if (storeListPoi != null) {
                Dialog(
                    onDismissRequest = { storeListPoi = null },
                    properties = DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false,
                    ),
                ) {
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically { it },
                    ) {
                        storeListPoi?.let { poi ->
                            ArFilteredStoreListOverlay(
                                poi = poi,
                                onStoreClick = { store ->
                                    storeListPoi = null
                                    navController.navigate(
                                        AppRoutes.placeDetailRoute(store.category_key ?: "other", store.id)
                                    )
                                },
                                onDismiss = { storeListPoi = null },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
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

        Box(modifier = Modifier.offset { IntOffset(xPx, yPx) }) {
            ArPoiCard(
                title = if (poi.isPending) "분석 중..." else poi.name,
                subtitle = buildString {
                    if (poi.category.isNotEmpty()) append("${poi.category} · ")
                    append("${"%.0f".format(poi.distance)}m")
                },
                category = poi.category,
                extraCount = poi.storeCount,
                onClick = { onPoiClick(poi) },
            )
        }
    }
}

@Composable
private fun ArFilteredStoreListOverlay(
    poi: DynamicPoi,
    onStoreClick: (SearchResultItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(ScanPangColors.ArOverlayScrimDark)
            .clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {},
            shape = ScanPangShapes.arFilterPanelTop,
            color = ScanPangColors.Surface,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = ScanPangDimens.arTopBarHorizontal)
                    .padding(top = ScanPangSpacing.md)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "${poi.category} ${poi.storeCount}개",
                    style = ScanPangType.arFilterTitle16,
                    color = ScanPangColors.OnSurfaceStrong,
                    modifier = Modifier.padding(bottom = ScanPangSpacing.sm),
                )
                poi.matchingStores.forEach { store ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStoreClick(store) }
                            .padding(vertical = ScanPangSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                    ) {
                        ArCategoryIconBadge(
                            category = poi.category,
                            badgeSize = 32,
                            iconSize = 18,
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = store.store_name,
                                style = ScanPangType.chip13SemiBold,
                                color = ScanPangColors.OnSurfaceStrong,
                            )
                            val meta = buildString {
                                store.floor?.let { append(it) }
                                if (store.floor != null && store.addr != null) append(" · ")
                                store.addr?.let { append(it) }
                            }
                            if (meta.isNotEmpty()) {
                                Text(
                                    text = meta,
                                    style = ScanPangType.meta11Medium,
                                    color = ScanPangColors.ArPoiSubtitle,
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
                Spacer(modifier = Modifier.height(ScanPangSpacing.sm))
            }
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

private fun filterArExploreHits(query: String, all: List<ArExploreSearchHitUi>): List<ArExploreSearchHitUi> {
    val t = query.trim().lowercase()
    if (t.isEmpty()) return all
    val filtered = all.filter { hit ->
        hit.title.lowercase().contains(t) ||
            hit.category.lowercase().contains(t) ||
            hit.badgeLabel?.lowercase()?.contains(t) == true
    }
    return filtered.ifEmpty { all }
}

private fun buildFilterPillLabel(selected: Set<String>): String {
    val list = selected.toList()
    if (list.isEmpty()) return ""
    if (list.size == 1) return "${list[0]} 탐색 중"
    return "${list[0]} 외 ${list.size - 1}개 탐색 중"
}

/**
 * place_id = "__outdoor__" 매장들을 좌표 기준으로 클러스터링.
 * - maxDistanceM: 사용자로부터 이 거리 이내 매장만 후보 (건물과 동일 기준)
 * - groupRadiusM: 이 반경 이내 매장끼리 한 마커로 묶음
 * - fov: 시야 polygon (Pair<lng, lat>). 매장이 FOV 안에 있어야 후보.
 *
 * 클러스터 ID 는 멤버 store id 들의 정렬 해시 — 같은 멤버면 항상 같은 ID 라 마커 깜빡임 방지.
 */
private fun computeOutdoorClusters(
    outdoorStores: List<SearchResultItem>,
    userLat: Double,
    userLng: Double,
    fov: List<Pair<Double, Double>>,
    maxDistanceM: Float,
    groupRadiusM: Float,
): List<OutdoorCluster> {
    // 1) 시야+거리 안에 들어오는 매장만 추출
    data class Visible(val store: SearchResultItem, val lat: Double, val lng: Double, val dist: Float)
    val visible = outdoorStores.mapNotNull { store ->
        val lat = store.lat ?: return@mapNotNull null
        val lng = store.lng ?: return@mapNotNull null
        val r = FloatArray(1)
        Location.distanceBetween(userLat, userLng, lat, lng, r)
        if (r[0] > maxDistanceM) return@mapNotNull null
        if (!isPointInPolygon(lng, lat, fov)) return@mapNotNull null
        Visible(store, lat, lng, r[0])
    }

    // 2) 3m 이내끼리 묶기 — greedy 클러스터링 (성능 충분, 보통 매장 < 100개)
    val groups = mutableListOf<MutableList<Visible>>()
    for (v in visible) {
        val joined = groups.firstOrNull { g ->
            g.any { existing ->
                val r = FloatArray(1)
                Location.distanceBetween(existing.lat, existing.lng, v.lat, v.lng, r)
                r[0] <= groupRadiusM
            }
        }
        if (joined != null) joined.add(v) else groups.add(mutableListOf(v))
    }

    // 3) 각 클러스터를 OutdoorCluster 로
    return groups.map { g ->
        val avgLat = g.map { it.lat }.average()
        val avgLng = g.map { it.lng }.average()
        val r = FloatArray(1)
        Location.distanceBetween(userLat, userLng, avgLat, avgLng, r)
        val sortedIds = g.map { it.store.id }.sorted().joinToString(",")
        OutdoorCluster(
            id = "outdoor_${sortedIds.hashCode()}",
            lat = avgLat,
            lng = avgLng,
            stores = g.map { it.store },
            distance = r[0],
        )
    }
}

/** Ray casting 알고리즘 — (x, y) 가 polygon 안에 있는지. polygon 은 List<Pair<x, y>>. */
private fun isPointInPolygon(x: Double, y: Double, poly: List<Pair<Double, Double>>): Boolean {
    if (poly.size < 3) return false
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val xi = poly[i].first;  val yi = poly[i].second
        val xj = poly[j].first;  val yj = poly[j].second
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
            inside = !inside
        }
        j = i
    }
    return inside
}

// ─────────────────────────────────────────────────────────────────────────────
// Polygon utility 함수들은 ArBuildingPinUtils.kt (com.scanpang.app.components.ar)로 이동됨.
// computeCentroid / computeAngularFootprint / buildFovPolygon / clipPolygon /
// computeFrontEdgeMidpoint / isRayBlockedByPolygon 은 그 파일의 internal fun 을 사용.
// ─────────────────────────────────────────────────────────────────────────────

private fun android.view.View.findFirstSurfaceView(): android.view.SurfaceView? {
    if (this is android.view.SurfaceView) return this
    if (this is android.view.ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).findFirstSurfaceView()?.let { return it }
        }
    }
    return null
}



