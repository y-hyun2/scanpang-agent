package com.scanpang.app.ar.explore

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.opengl.Matrix
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Earth
import com.google.ar.core.TrackingState
import com.scanpang.app.ar.ArExploreTtsController
import com.scanpang.app.ar.ArSpeechRecognizerHelper
import com.scanpang.app.ar.ScanPangAgentService
import com.scanpang.app.ar.sendVoiceMessage
import com.scanpang.app.components.ar.ArAgentChatMessage
import com.scanpang.app.components.ar.ArCircleIconButton
import com.scanpang.app.components.ar.ArExploreFilterPanelFigma
import com.scanpang.app.components.ar.ArExploreInteractiveChatSection
import com.scanpang.app.components.ar.ArExploreSideColumn
import com.scanpang.app.components.ar.ArFloorStoreGuideOverlay
import com.scanpang.app.components.ar.ArPoiCard
import com.scanpang.app.components.ar.ArPoiFloatingDetailOverlay
import com.scanpang.app.components.ar.ArPoiTabBuilding
import com.scanpang.app.components.ar.arExploreCategoryChipSpecs
import com.scanpang.app.data.remote.ArOverlay
import com.scanpang.app.data.remote.Docent
import com.scanpang.app.data.remote.PlaceQueryRequest
import com.scanpang.app.data.remote.PlaceQueryResponse
import com.scanpang.app.data.remote.RetrofitClient
import com.scanpang.app.data.remote.ScanPangViewModel
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.app.ui.theme.ScanPangDimens
import com.scanpang.app.ui.theme.ScanPangShapes
import com.scanpang.app.ui.theme.ScanPangSpacing
import com.scanpang.app.ui.theme.ScanPangTheme
import com.scanpang.app.ui.theme.ScanPangType
import io.github.sceneview.ar.ARScene
import io.github.sceneview.rememberEngine
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

class PlaceAugmentingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScanPangTheme {
                PlaceAugmentingScreen(
                    onNavigateHome = { finish() },
                    onStartArNavigation = { destName ->
                        val intent = Intent(
                            this,
                            com.hufs.arnavigation_com.ArNavigationActivity::class.java,
                        ).apply {
                            putExtra("destinationName", destName)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PlaceAugmentingScreen(
    onNavigateHome: () -> Unit,
    onStartArNavigation: (String) -> Unit,
) {
    val permissions = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
    )
    if (permissions.allPermissionsGranted) {
        GeospatialARExploreScreen(
            onNavigateHome = onNavigateHome,
            onStartArNavigation = onStartArNavigation,
        )
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("AR 탐색을 위해\n카메라와 위치 정보가 필요합니다.")
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { permissions.launchMultiplePermissionRequest() }) {
                Text("권한 허용하기")
            }
        }
    }
}

@Composable
fun GeospatialARExploreScreen(
    onNavigateHome: () -> Unit,
    onStartArNavigation: (String) -> Unit,
) {
    val viewModel: ScanPangViewModel = viewModel()
    val placeResult by viewModel.placeResult.collectAsState()
    val convenienceResult by viewModel.convenienceResult.collectAsState()
    val context = LocalContext.current
    val appContext = context.applicationContext

    val categoryKeyMap = mapOf(
        "쇼핑" to "shopping", "편의점" to "convenience_store", "식당" to "restaurant",
        "카페" to "cafe", "환전소" to "exchange", "은행" to "bank", "ATM" to "atm",
        "병원" to "hospital", "지하철역" to "subway", "화장실" to "restroom",
        "물품보관함" to "locker", "약국" to "pharmacy",
    )

    // ── ARCore Geospatial 상태 ──
    val engine = rememberEngine()
    val api = remember { RetrofitClient.api }
    val scope = rememberCoroutineScope()
    var hasAchievedHighAccuracy by remember { mutableStateOf(false) }
    var trackingMessage by remember { mutableStateOf("ARCore 초기화 중...") }
    var currentHeading by remember { mutableStateOf(0.0) }
    var currentAltitude by remember { mutableStateOf(0.0) }
    var currentPitch by remember { mutableStateOf(0.0) }
    var currentLat by remember { mutableStateOf(0.0) }
    var currentLng by remember { mutableStateOf(0.0) }
    var lastQueryTime by remember { mutableStateOf(0L) }
    var triggerHitTest by remember { mutableStateOf(false) }

    val geospatialAnchors = remember { mutableStateMapOf<String, Anchor>() }
    var anchorScreenPositions by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }
    val dynamicPois = remember { mutableStateListOf<DynamicPoi>() }
    val verifiedCache = remember { mutableStateListOf<DynamicPoi>() }

    // ── UI 상태 ──
    val snackbarHostState = remember { SnackbarHostState() }
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

    var selectedPoi by remember { mutableStateOf<String?>(null) }
    var selectedPoiOverlay by remember { mutableStateOf<ArOverlay?>(null) }
    var selectedPoiDocent by remember { mutableStateOf<Docent?>(null) }
    var activeDetailTab by remember { mutableStateOf(ArPoiTabBuilding) }
    var selectedStore by remember { mutableStateOf<String?>(null) }

    // ── TTS / STT / Agent ──
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

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) chatListState.scrollToItem(chatMessages.lastIndex)
    }

    val categoryChipSpecs = remember { arExploreCategoryChipSpecs() }
    val recentQueries = remember { listOf("할랄 식당", "명동성당", "근처 환전소") }
    val suggestionTags = remember { listOf("할랄", "카페", "기도실", "환전소") }

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

    // /place/query API 호출
    suspend fun callPlaceQuery(heading: Double, lat: Double, lng: Double, alt: Double, pitch: Double): PlaceQueryResponse? {
        return try {
            api.queryPlace(PlaceQueryRequest(heading = heading, user_lat = lat, user_lng = lng, user_alt = alt, pitch = pitch))
        } catch (e: Exception) {
            Log.e("SCANPANG", "place/query 오류: ${e.message}")
            null
        }
    }

    // convenience 결과 → 동적 POI로 추가
    LaunchedEffect(convenienceResult) {
        convenienceResult?.facilities?.forEach { facility ->
            val alreadyExists = dynamicPois.any { it.name == facility.name }
            if (!alreadyExists && facility.lat != 0.0 && facility.lng != 0.0) {
                dynamicPois.add(
                    DynamicPoi(
                        id = "conv_${facility.name}_${System.currentTimeMillis()}",
                        name = facility.name,
                        category = convenienceResult?.category ?: "",
                        distance = facility.distance_m.toFloat(),
                        latitude = facility.lat,
                        longitude = facility.lng,
                    ),
                )
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            // ── ARScene 배경 (ARCore Geospatial) ──
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
                    val userLat = pose.latitude
                    val userLng = pose.longitude
                    currentLat = userLat
                    currentLng = userLng
                    currentHeading = pose.heading
                    currentAltitude = pose.altitude

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
                            Location.distanceBetween(userLat, userLng, dynamicPois[i].latitude, dynamicPois[i].longitude, results)
                            dynamicPois[i] = dynamicPois[i].copy(distance = results[0])
                        }

                        // 5초마다 자동 건물 쿼리
                        val now = System.currentTimeMillis()
                        if (now - lastQueryTime > 5000 && !isFrozen) {
                            lastQueryTime = now
                            scope.launch {
                                val response = callPlaceQuery(currentHeading, userLat, userLng, currentAltitude, currentPitch)
                                val overlay = response?.ar_overlay ?: return@launch
                                if (overlay.name.isBlank()) return@launch
                                val alreadyExists = dynamicPois.any { it.name == overlay.name }
                                if (!alreadyExists) {
                                    val newId = "auto_${overlay.name}_${now}"
                                    val newAnchor = earth.createAnchor(userLat, userLng, pose.altitude, 0f, 0f, 0f, 1f)
                                    geospatialAnchors[newId] = newAnchor
                                    dynamicPois.add(
                                        DynamicPoi(
                                            id = newId,
                                            name = overlay.name,
                                            category = overlay.category,
                                            latitude = userLat,
                                            longitude = userLng,
                                            arOverlay = overlay,
                                            docent = response.docent,
                                        ),
                                    )
                                }
                            }
                        }

                        // 탭 기반 건물 인식 (hit test)
                        if (triggerHitTest) {
                            triggerHitTest = false
                            val hitResults = frame.hitTest(screenWidthPx / 2f, screenHeightPx / 2f)
                            var foundSurface = false

                            for (hitResult in hitResults) {
                                val trackable = hitResult.trackable
                                if (trackable is com.google.ar.core.Plane ||
                                    trackable is com.google.ar.core.Point ||
                                    trackable is com.google.ar.core.DepthPoint
                                ) {
                                    if (hitResult.distance > 1.5f) {
                                        val hitGeoPose = earth.getGeospatialPose(hitResult.hitPose)
                                        val newId = "hit_${System.currentTimeMillis()}"
                                        val newAnchor = earth.createAnchor(hitGeoPose.latitude, hitGeoPose.longitude, hitGeoPose.altitude, 0f, 0f, 0f, 1f)
                                        geospatialAnchors[newId] = newAnchor

                                        // 캐시 확인
                                        var cached: DynamicPoi? = null
                                        for (c in verifiedCache) {
                                            Location.distanceBetween(hitGeoPose.latitude, hitGeoPose.longitude, c.latitude, c.longitude, results)
                                            if (results[0] < 2.0f) { cached = c; break }
                                        }

                                        if (cached != null) {
                                            Location.distanceBetween(userLat, userLng, cached.latitude, cached.longitude, results)
                                            dynamicPois.add(cached.copy(id = newId, distance = results[0]))
                                        } else {
                                            Location.distanceBetween(userLat, userLng, hitGeoPose.latitude, hitGeoPose.longitude, results)
                                            dynamicPois.add(DynamicPoi(newId, "분석 중...", "", results[0], hitGeoPose.latitude, hitGeoPose.longitude, isPending = true))
                                            scope.launch {
                                                val response = callPlaceQuery(currentHeading, userLat, userLng, currentAltitude, currentPitch)
                                                val idx = dynamicPois.indexOfFirst { it.id == newId }
                                                if (idx != -1) {
                                                    val final = dynamicPois[idx].copy(
                                                        name = response?.ar_overlay?.name?.takeIf { it.isNotEmpty() } ?: "알 수 없는 장소",
                                                        arOverlay = response?.ar_overlay,
                                                        docent = response?.docent,
                                                        isPending = false,
                                                    )
                                                    dynamicPois[idx] = final
                                                    verifiedCache.add(final)
                                                }
                                            }
                                        }
                                        foundSurface = true; break
                                    }
                                }
                            }

                            if (!foundSurface) {
                                val newId = "hit_${System.currentTimeMillis()}"
                                val newAnchor = earth.createAnchor(userLat, userLng, pose.altitude, 0f, 0f, 0f, 1f)
                                geospatialAnchors[newId] = newAnchor
                                dynamicPois.add(DynamicPoi(newId, "분석 중...", "", 0f, userLat, userLng, isPending = true))
                                scope.launch {
                                    val response = callPlaceQuery(currentHeading, userLat, userLng, currentAltitude, currentPitch)
                                    val idx = dynamicPois.indexOfFirst { it.id == newId }
                                    if (idx != -1) {
                                        val final = dynamicPois[idx].copy(
                                            name = response?.ar_overlay?.name?.takeIf { it.isNotEmpty() } ?: "알 수 없는 장소",
                                            arOverlay = response?.ar_overlay,
                                            docent = response?.docent,
                                            isPending = false,
                                        )
                                        dynamicPois[idx] = final
                                        verifiedCache.add(final)
                                    }
                                }
                            }
                        }
                    } else {
                        trackingMessage = "VPS 정밀 탐색 중... (오차: ${"%.1f".format(pose.horizontalAccuracy)}m)"
                    }

                    // 앵커 → 화면 좌표 투영
                    val newPositions = mutableMapOf<String, Offset>()
                    val viewMatrix = FloatArray(16); camera.getViewMatrix(viewMatrix, 0)
                    val projMatrix = FloatArray(16); camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100.0f)
                    geospatialAnchors.forEach { (id, anchor) ->
                        if (anchor.trackingState == TrackingState.TRACKING) {
                            val anchorPose = anchor.pose
                            val translation = floatArrayOf(anchorPose.tx(), anchorPose.ty(), anchorPose.tz(), 1f)
                            val viewCoords = FloatArray(4)
                            Matrix.multiplyMV(viewCoords, 0, viewMatrix, 0, translation, 0)
                            if (viewCoords[2] <= 0) {
                                val clipCoords = FloatArray(4)
                                Matrix.multiplyMV(clipCoords, 0, projMatrix, 0, viewCoords, 0)
                                if (clipCoords[3] != 0f) {
                                    val x = ((clipCoords[0] / clipCoords[3] + 1f) / 2f) * screenWidthPx
                                    val y = ((1f - clipCoords[1] / clipCoords[3]) / 2f) * screenHeightPx
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
                Box(modifier = Modifier.fillMaxSize().background(ScanPangColors.ArFreezeTint))
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
                        .height(maxOf(ScanPangDimens.arCircleBtn36, ScanPangDimens.arStatusPillHeight)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArCircleIconButton(
                        icon = Icons.Rounded.Home,
                        contentDescription = "홈",
                        onClick = onNavigateHome,
                    )
                    Box(
                        modifier = Modifier.weight(1f).padding(horizontal = ScanPangSpacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        ArExploreStatusPill(
                            isFrozen = isFrozen,
                            selectedFilters = categorySelection,
                            hasHighAccuracy = hasAchievedHighAccuracy,
                            onClick = {
                                if (isFrozen) isFrozen = false else isFilterOpen = true
                            },
                        )
                    }
                    ArCircleIconButton(
                        icon = Icons.Rounded.Search,
                        contentDescription = "검색",
                        onClick = { isSearchOpen = true },
                    )
                }
            }

            // ── 동적 마커 + 사이드 컬럼 ──
            Box(modifier = Modifier.fillMaxSize()) {
                dynamicPois.forEach { poi ->
                    val screenPos = anchorScreenPositions[poi.id] ?: return@forEach
                    Box(modifier = Modifier.offset { IntOffset(screenPos.x.roundToInt(), screenPos.y.roundToInt()) }) {
                        ArPoiCard(
                            title = if (poi.isPending) "분석 중..." else poi.name,
                            subtitle = buildString {
                                if (poi.category.isNotEmpty()) append("${poi.category} · ")
                                append("${"%.0f".format(poi.distance)}m")
                            },
                            onClick = if (!poi.isPending) {
                                {
                                    selectedPoi = poi.name
                                    selectedPoiOverlay = poi.arOverlay
                                    selectedPoiDocent = poi.docent
                                    activeDetailTab = ArPoiTabBuilding
                                    selectedStore = null
                                }
                            } else null,
                        )
                    }
                }
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
                        if (isSttListening) { h?.stopListening(); return@mic }
                        if (h == null || !h.isRecognitionAvailable()) {
                            scope.launch { snackbarHostState.showSnackbar("음성 인식을 사용할 수 없어요") }
                            return@mic
                        }
                        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (hasMic) h.startListening()
                        else scope.launch { snackbarHostState.showSnackbar("마이크 권한이 필요해요") }
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
                    modifier = Modifier.fillMaxSize().background(ScanPangColors.ArOverlayScrimDark).clickable { isFilterOpen = false },
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
                            modifier = Modifier.padding(ScanPangDimens.arTopBarHorizontal).verticalScroll(rememberScrollState()),
                        ) {
                            ArExploreFilterPanelFigma(
                                categorySpecs = categoryChipSpecs,
                                categorySelection = categorySelection,
                                onCategoryToggle = { label ->
                                    categorySelection = if (label in categorySelection) categorySelection - label else categorySelection + label
                                },
                                onReset = { categorySelection = emptySet() },
                                onApply = {
                                    isFilterOpen = false
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
                    modifier = Modifier.fillMaxSize().background(ScanPangColors.ArOverlayScrimDark).clickable { isSearchOpen = false; showArSearchResults = false },
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
                            modifier = Modifier.padding(ScanPangDimens.arTopBarHorizontal).verticalScroll(rememberScrollState()),
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
                                    Icon(Icons.Rounded.Search, contentDescription = null, tint = ScanPangColors.OnSurfaceMuted, modifier = Modifier.size(ScanPangDimens.icon20))
                                    Text("장소·메뉴 검색", style = ScanPangType.searchPlaceholderRegular, color = ScanPangColors.OnSurfacePlaceholder)
                                }
                                IconButton(onClick = { isSearchOpen = false; showArSearchResults = false }) {
                                    Icon(Icons.Rounded.Close, "닫기", tint = ScanPangColors.OnSurfaceStrong)
                                }
                            }
                            Text("최근 검색", style = ScanPangType.sectionTitle16, color = ScanPangColors.OnSurfaceStrong)
                            recentQueries.forEach { q ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { showArSearchResults = true }.padding(vertical = ScanPangSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm),
                                ) {
                                    Icon(Icons.Rounded.History, null, tint = ScanPangColors.OnSurfaceMuted, modifier = Modifier.size(ScanPangDimens.icon18))
                                    Text(q, style = ScanPangType.body14Regular, color = ScanPangColors.OnSurfaceStrong)
                                }
                            }
                            Text("추천 검색어", style = ScanPangType.sectionTitle16, color = ScanPangColors.OnSurfaceStrong)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.sm)) {
                                suggestionTags.forEach { tag ->
                                    Surface(shape = ScanPangShapes.badge6, color = ScanPangColors.ArRecommendTagHalalBackground, modifier = Modifier.clickable { showArSearchResults = true }) {
                                        Text(tag, modifier = Modifier.padding(horizontal = ScanPangDimens.arSearchTagHorizontalPad, vertical = ScanPangDimens.arSearchTagVerticalPad), style = ScanPangType.tag11Medium, color = ScanPangColors.Primary)
                                    }
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
                        selectedPoi = null; selectedPoiOverlay = null; selectedPoiDocent = null
                        selectedStore = null; activeDetailTab = ArPoiTabBuilding
                    },
                    onFloorStoreClick = { selectedStore = it },
                    onSave = { scope.launch { snackbarHostState.showSnackbar("저장되었습니다") } },
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
                        onStartArNavigation(store)
                        selectedStore = null
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ── Status Pill ──

@Composable
private fun ArExploreStatusPill(
    isFrozen: Boolean,
    selectedFilters: Set<String>,
    hasHighAccuracy: Boolean,
    onClick: () -> Unit,
) {
    val (bgColor, textColor, icon, text) = when {
        isFrozen -> listOf(ScanPangColors.Primary, Color.White, Icons.Rounded.Pause, "화면 고정 중")
        !hasHighAccuracy -> listOf(ScanPangColors.ArOverlayWhite80, ScanPangColors.OnSurfaceStrong, Icons.Rounded.CropFree, "VPS 탐색 중...")
        selectedFilters.isEmpty() -> listOf(ScanPangColors.ArOverlayWhite80, ScanPangColors.OnSurfaceStrong, Icons.Rounded.CropFree, "AR 탐색 중")
        else -> {
            val label = if (selectedFilters.size == 1) selectedFilters.first() else "${selectedFilters.first()} 외 ${selectedFilters.size - 1}개"
            listOf(ScanPangColors.Primary, Color.White, Icons.Rounded.FilterList, label)
        }
    }
    @Suppress("UNCHECKED_CAST")
    Surface(
        modifier = Modifier.height(ScanPangDimens.arStatusPillHeight).clip(CircleShape).clickable(onClick = onClick),
        shape = CircleShape,
        color = bgColor as Color,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ScanPangDimens.arStatusPillHorizontalPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ScanPangSpacing.xs),
        ) {
            Icon(icon as androidx.compose.ui.graphics.vector.ImageVector, null, modifier = Modifier.size(ScanPangDimens.icon18), tint = textColor as Color)
            Text(text as String, style = ScanPangType.arStatusPill15, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isFrozen || selectedFilters.isNotEmpty()) {
                Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(ScanPangDimens.arNavDestinationChevron), tint = textColor)
            }
        }
    }
}
