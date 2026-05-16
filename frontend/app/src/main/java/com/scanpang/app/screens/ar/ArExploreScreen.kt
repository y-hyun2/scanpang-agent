package com.scanpang.app.screens.ar

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.location.Location
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.PixelCopy
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
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
import com.scanpang.app.data.remote.Building
import com.scanpang.app.data.remote.GeoJsonMultiPolygon
import kotlin.math.abs

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

private data class BuildingCandidate(
    val b: Building,
    val centerLat: Double,
    val centerLng: Double,
    val dist: Float,
    val centerBearing: Double,      // 사용자 → 건물 중심 방향 (0~360°)
    val angularHalfWidth: Double,   // 사용자 위치에서 건물이 차지하는 시야각의 절반
    val visiblePolygon: List<Pair<Double, Double>>,  // FOV ∩ 건물 polygon (Pair<lng, lat>)
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
    val context = LocalContext.current

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
                ArAgentChatMessage(
                    text = "아미나님, 오늘은 어떤 할랄 맛집을 찾으세요?",
                    isUser = true,
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
    var frozenBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val activityView = LocalView.current
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

    val geospatialAnchors = remember { mutableStateMapOf<String, Anchor>() }
    var anchorScreenPositions by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }
    val dynamicPois = remember { mutableStateListOf<DynamicPoi>() }
    var lastProcessedChunkCell by remember { mutableStateOf<String?>(null) }
    var lastVisibilityCalcTime by remember { mutableStateOf(0L) }

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

                    if (hasAchievedHighAccuracy && !isFrozen) {
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
                        if (now2 - lastVisibilityCalcTime > 300) {
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

                                // 새 visible ID 집합
                                val newVisibleIds = visibleCandidates.map { cand ->
                                    "building_${cand.b.ufid ?: cand.b.h3_index_10}_${cand.b.hashCode()}"
                                }.toSet()

                                // 더 이상 visible 아닌 건물 라벨 제거 (멀어진 / 가려진 / FOV 밖)
                                val currentBuildingIds = dynamicPois.filter { it.id.startsWith("building_") }.map { it.id }
                                val toRemove = currentBuildingIds.filter { it !in newVisibleIds }
                                toRemove.forEach { id ->
                                    geospatialAnchors[id]?.detach()
                                    geospatialAnchors.remove(id)
                                }
                                dynamicPois.removeAll { it.id.startsWith("building_") && it.id !in newVisibleIds }

                                // 새 visible 건물 — anchor 생성 또는 위치 갱신
                                val existingIds = dynamicPois.map { it.id }.toSet()
                                visibleCandidates.forEach { cand ->
                                    val id = "building_${cand.b.ufid ?: cand.b.h3_index_10}_${cand.b.hashCode()}"

                                    // 마커 위치 — visible polygon front edge 중점
                                    val markerPos = computeFrontEdgeMidpoint(
                                        cand.visiblePolygon, currentLat, currentLng
                                    ) ?: Pair(cand.centerLat, cand.centerLng)

                                    // 땅 높이 ≈ 사용자 위치 - 키(1.5m 가정)
                                    val groundAltitude = currentAltitude - 1.5
                                    val labelAltitude = groundAltitude + (cand.b.render_height / 2.0)

                                    if (id !in existingIds) {
                                        // 새 건물 — anchor 신규 생성
                                        try {
                                            val anchor = earth.createAnchor(
                                                markerPos.first, markerPos.second, labelAltitude,
                                                0f, 0f, 0f, 1f,
                                            )
                                            geospatialAnchors[id] = anchor
                                            dynamicPois.add(
                                                DynamicPoi(
                                                    id = id,
                                                    name = cand.b.bld_nm ?: "이름 없는 건물",
                                                    category = "건물",
                                                    distance = cand.dist,
                                                    latitude = markerPos.first,
                                                    longitude = markerPos.second,
                                                ),
                                            )
                                        } catch (e: Exception) {
                                            Log.e("ArExplore", "건물 앵커 실패 ${cand.b.bld_nm}: ${e.message}")
                                        }
                                    } else {
                                        // 기존 건물 — markerPos 3m 이상 변할 때만 anchor 재생성
                                        // (작은 변화는 ARCore 자동 추적이 더 부드럽게 처리)
                                        val existingPoi = dynamicPois.firstOrNull { it.id == id } ?: return@forEach
                                        val r = FloatArray(1)
                                        Location.distanceBetween(
                                            existingPoi.latitude, existingPoi.longitude,
                                            markerPos.first, markerPos.second, r,
                                        )
                                        if (r[0] > 3f) {
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
                                        // 3m 이내 변화면 anchor 그대로 — ARCore가 자동으로 부드럽게 추적
                                    }
                                }

                                Log.d("ArExplore",
                                    "visibility 갱신: 표시 ${visibleCandidates.size}개 " +
                                            "(캐시 ${cache.size}, FOV+100m 통과 ${candidates.size}, " +
                                            "occlusion ${candidates.size - visibleCandidates.size}개 제외, " +
                                            "제거 ${toRemove.size})")
                            }
                        }
                    } else {
                        trackingMessage =
                            "VPS 정밀 탐색 중... (오차: ${"%.1f".format(pose.horizontalAccuracy)}m / 1.5m 미만 필요)"
                    }

                    // 앵커 → 화면 좌표 투영 (프리즈 중에는 마지막 위치 유지)
                    if (!isFrozen) {
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
                    }
                },
            )

            // 화면 고정 시 반투명 오버레이
            // ── 프리징된 화면 (캡처 이미지) ──
            frozenBitmap?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            // 화면 고정 시 반투명 오버레이 (프리징됨 표시)
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
                        // 마커 클릭 시 정보 패널 띄움 (도슨트는 안 부름)
                        selectedPoi = poi.name
                        selectedPoiOverlay = poi.arOverlay
                        selectedPoiDocent = null
                        activeDetailTab = ArPoiTabBuilding
                    },
                )

                ArExploreSideColumn(
                    onTtsClick = {
                        isTtsOn = !isTtsOn
                        val msg = if (isTtsOn) "음성 안내 켜짐" else "음성 안내 꺼짐"
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    },
                    onCameraClick = {
                        if (isFrozen) {
                            // 프리즈 해제
                            isFrozen = false
                            frozenBitmap = null
                        } else {
                            // 캡처 → 프리즈
                            captureArScene(activityView) { bitmap ->
                                if (bitmap != null) {
                                    frozenBitmap = bitmap.asImageBitmap()
                                    isFrozen = true
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("화면 캡처에 실패했어요")
                                    }
                                }
                            }
                        }
                    },
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
                                onApply = { isFilterOpen = false },
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

        Box(modifier = Modifier.offset { IntOffset(xPx, yPx) }) {
            ArPoiCard(
                title = if (poi.isPending) "분석 중..." else poi.name,
                subtitle = buildString {
                    if (poi.category.isNotEmpty()) append("${poi.category} · ")
                    append("${"%.0f".format(poi.distance)}m")
                },
                onClick = { onPoiClick(poi) },
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

// ─────────────────────────────────────────────────────────────────────────────
// Polygon utility 함수들
// ─────────────────────────────────────────────────────────────────────────────

/**
 * GeoJSON MultiPolygon의 첫 번째 polygon 외곽 ring의 vertex 평균 = 단순 중심점.
 */
private fun computeCentroid(geom: GeoJsonMultiPolygon): Pair<Double, Double>? {
    val polygon = geom.coordinates.firstOrNull() ?: return null
    val ring = polygon.firstOrNull() ?: return null
    if (ring.isEmpty()) return null

    var sumLat = 0.0
    var sumLng = 0.0
    for (point in ring) {
        sumLng += point[0]  // GeoJSON: [lng, lat] 순서
        sumLat += point[1]
    }
    val n = ring.size
    return Pair(sumLat / n, sumLng / n)
}

/**
 * 사용자 위치 기준, 건물 polygon이 차지하는 시야각(angular footprint).
 * 반환: (centerBearing 0~360°, halfWidth°)
 * Occlusion 판정에 사용.
 */
private fun computeAngularFootprint(
    geom: GeoJsonMultiPolygon,
    userLat: Double,
    userLng: Double,
): Pair<Double, Double>? {
    val polygon = geom.coordinates.firstOrNull() ?: return null
    val ring = polygon.firstOrNull() ?: return null
    if (ring.size < 3) return null

    val bearings = ring.map { point ->
        val r = FloatArray(3)
        Location.distanceBetween(userLat, userLng, point[1], point[0], r)
        ((r[1] + 360f) % 360f).toDouble()
    }

    val minB = bearings.min()
    val maxB = bearings.max()
    val range = maxB - minB

    return if (range > 180.0) {
        val wrapped = bearings.map { if (it < 180) it + 360 else it }
        val wMin = wrapped.min()
        val wMax = wrapped.max()
        val center = ((wMin + wMax) / 2.0) % 360.0
        Pair(center, (wMax - wMin) / 2.0)
    } else {
        Pair((minB + maxB) / 2.0, range / 2.0)
    }
}

/**
 * 사용자 시야(FOV)를 위경도 polygon으로 근사.
 * 부채꼴을 7개 vertex로 표현: 사용자 점 + FOV 호 위 6점.
 */
private fun buildFovPolygon(
    userLat: Double,
    userLng: Double,
    heading: Double,
    fovDeg: Double = 60.0,
    maxDistM: Double = 200.0,
): List<Pair<Double, Double>> {
    val halfFov = fovDeg / 2.0
    val numArcPoints = 7

    val result = mutableListOf<Pair<Double, Double>>()
    result.add(Pair(userLng, userLat))

    val angleStep = fovDeg / (numArcPoints - 1)
    for (i in 0 until numArcPoints) {
        val angleFromHeading = -halfFov + i * angleStep
        val bearingDeg = (heading + angleFromHeading + 360) % 360
        val bearingRad = Math.toRadians(bearingDeg)

        val dLat = maxDistM * Math.cos(bearingRad) / 111_320.0
        val dLng = maxDistM * Math.sin(bearingRad) / (111_320.0 * Math.cos(Math.toRadians(userLat)))

        result.add(Pair(userLng + dLng, userLat + dLat))
    }

    result.add(Pair(userLng, userLat))
    return result
}

/**
 * Sutherland-Hodgman 폴리곤 클리핑.
 * subject (임의 모양) ∩ clip (convex, CW) = visible polygon.
 */
private fun clipPolygon(
    subject: List<Pair<Double, Double>>,
    clip: List<Pair<Double, Double>>,
): List<Pair<Double, Double>> {
    if (subject.size < 3 || clip.size < 3) return emptyList()

    val subjectClean = if (subject.first() == subject.last()) subject.dropLast(1) else subject
    val clipClean = if (clip.first() == clip.last()) clip.dropLast(1) else clip

    var output = subjectClean.toMutableList()

    for (i in clipClean.indices) {
        if (output.isEmpty()) break

        val input = output.toList()
        output = mutableListOf()

        val edgeStart = clipClean[i]
        val edgeEnd = clipClean[(i + 1) % clipClean.size]

        for (j in input.indices) {
            val current = input[j]
            val previous = input[(j - 1 + input.size) % input.size]

            val currentInside = isInsideEdge(current, edgeStart, edgeEnd)
            val previousInside = isInsideEdge(previous, edgeStart, edgeEnd)

            when {
                previousInside && currentInside -> output.add(current)
                previousInside && !currentInside -> output.add(computeLineIntersection(previous, current, edgeStart, edgeEnd))
                !previousInside && currentInside -> {
                    output.add(computeLineIntersection(previous, current, edgeStart, edgeEnd))
                    output.add(current)
                }
            }
        }
    }
    return output
}

private fun isInsideEdge(
    point: Pair<Double, Double>,
    edgeStart: Pair<Double, Double>,
    edgeEnd: Pair<Double, Double>,
): Boolean {
    // buildFovPolygon이 CW(시계방향) → inside = edge 오른쪽 → cross <= 0
    val cross = (edgeEnd.first - edgeStart.first) * (point.second - edgeStart.second) -
            (edgeEnd.second - edgeStart.second) * (point.first - edgeStart.first)
    return cross <= 0
}

private fun computeLineIntersection(
    p1: Pair<Double, Double>,
    p2: Pair<Double, Double>,
    edgeStart: Pair<Double, Double>,
    edgeEnd: Pair<Double, Double>,
): Pair<Double, Double> {
    val x1 = p1.first; val y1 = p1.second
    val x2 = p2.first; val y2 = p2.second
    val x3 = edgeStart.first; val y3 = edgeStart.second
    val x4 = edgeEnd.first; val y4 = edgeEnd.second

    val denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
    if (denom == 0.0) return p2

    val t = ((x1 - x3) * (y3 - y4) - (y1 - y3) * (x3 - x4)) / denom
    return Pair(x1 + t * (x2 - x1), y1 + t * (y2 - y1))
}

/**
 * visible polygon에서 사용자에게 가장 가까운 모서리(front edge) 중점 반환.
 * 반환: Pair<lat, lng> (ARCore anchor 형식)
 */
private fun computeFrontEdgeMidpoint(
    visiblePolygon: List<Pair<Double, Double>>,  // Pair<lng, lat>
    userLat: Double,
    userLng: Double,
): Pair<Double, Double>? {
    if (visiblePolygon.isEmpty()) return null

    // vertex 1개 — 그 점 자체를 마커 위치로 (시야 가장자리에 살짝 걸친 경우)
    if (visiblePolygon.size == 1) {
        val p = visiblePolygon[0]
        return Pair(p.second, p.first)   // Pair<lat, lng>
    }

    // vertex 2개 이상 — 모서리 중점 중 사용자에게 가장 가까운 것
    var bestMidLng = 0.0
    var bestMidLat = 0.0
    var bestDist = Double.MAX_VALUE

    val n = visiblePolygon.size
    for (i in 0 until n) {
        val p1 = visiblePolygon[i]
        val p2 = visiblePolygon[(i + 1) % n]

        val midLng = (p1.first + p2.first) / 2.0
        val midLat = (p1.second + p2.second) / 2.0

        val r = FloatArray(1)
        Location.distanceBetween(userLat, userLng, midLat, midLng, r)

        if (r[0] < bestDist) {
            bestDist = r[0].toDouble()
            bestMidLat = midLat
            bestMidLng = midLng
        }
    }

    return Pair(bestMidLat, bestMidLng)
}

/**
 * 사용자 → target까지의 ray가 occluder polygon을 통과하는지.
 * polygon의 어떤 edge와도 교차하면 true (가려진 것).
 *
 * 좌표는 모두 (lng, lat) 형식. polygon은 닫힌 ring (마지막 점이 첫 점과 같지 않아도 자동 연결).
 */
private fun isRayBlockedByPolygon(
    userLng: Double, userLat: Double,
    targetLng: Double, targetLat: Double,
    occluderPolygon: List<Pair<Double, Double>>,
): Boolean {
    if (occluderPolygon.size < 3) return false
    val n = occluderPolygon.size
    for (i in 0 until n) {
        val p1 = occluderPolygon[i]
        val p2 = occluderPolygon[(i + 1) % n]
        if (segmentsIntersect(
                userLng, userLat, targetLng, targetLat,
                p1.first, p1.second, p2.first, p2.second,
            )) {
            return true
        }
    }
    return false
}

/**
 * 두 선분 (x1,y1)-(x2,y2)와 (x3,y3)-(x4,y4)가 교차하는지.
 * 표준 orientation 테스트.
 */
private fun segmentsIntersect(
    x1: Double, y1: Double, x2: Double, y2: Double,
    x3: Double, y3: Double, x4: Double, y4: Double,
): Boolean {
    val d1 = direction(x3, y3, x4, y4, x1, y1)
    val d2 = direction(x3, y3, x4, y4, x2, y2)
    val d3 = direction(x1, y1, x2, y2, x3, y3)
    val d4 = direction(x1, y1, x2, y2, x4, y4)
    return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
}

private fun direction(
    x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double,
): Double {
    return (x3 - x1) * (y2 - y1) - (y3 - y1) * (x2 - x1)
}

// ─────────────────────────────────────────────────────────────────────────────
// 스크린 프리징 — PixelCopy로 현재 화면 캡처
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 현재 화면(ARScene + UI overlay 포함)을 Bitmap으로 캡처.
 * PixelCopy.request의 callback은 비동기로 실행됨.
 */
private fun captureArScene(view: View, onCaptured: (Bitmap?) -> Unit) {
    val window = (view.context as? Activity)?.window
    if (window == null || view.width <= 0 || view.height <= 0) {
        onCaptured(null)
        return
    }
    try {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val rect = Rect(
            location[0], location[1],
            location[0] + view.width, location[1] + view.height,
        )
        PixelCopy.request(
            window, rect, bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) onCaptured(bitmap)
                else onCaptured(null)
            },
            Handler(Looper.getMainLooper()),
        )
    } catch (e: Exception) {
        Log.e("ArExplore", "캡처 실패: ${e.message}")
        onCaptured(null)
    }
}