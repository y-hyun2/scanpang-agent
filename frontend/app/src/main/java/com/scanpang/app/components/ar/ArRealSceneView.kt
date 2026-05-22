package com.scanpang.app.components.ar

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.google.ar.core.Anchor
import com.scanpang.app.data.remote.Building
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.filament.Engine
import com.google.ar.core.Config
import com.google.ar.core.Earth
import com.google.ar.core.TrackingState
import com.hufs.arnavigation_com.ArRouteNode
import com.hufs.arnavigation_com.NavigationState
import com.hufs.arnavigation_com.NodeType
import com.scanpang.app.ar.ArExploreTtsController
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.arnavigation.data.remote.dto.NavRouteResponse
import com.scanpang.arnavigation.data.repository.RouteRepositoryImpl
import com.scanpang.arnavigation.presentation.MainViewModel
import com.scanpang.arnavigation.presentation.MainViewModelFactory
import androidx.compose.material3.MaterialTheme
import com.google.android.filament.Material
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.node.Node
import io.github.sceneview.node.ViewNode2
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberViewNodeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class NavBuildingPin(val id: String, val name: String, val lat: Double, val lng: Double, val ufid: String?)

private data class NavBuildingCandidate(
    val building: Building,
    val centerLat: Double,
    val centerLng: Double,
    val distance: Float,
    val visiblePolygon: List<Pair<Double, Double>>,
)

/** AR 길안내 화면이 외부에 노출하는 UI 상태. */
data class ArNavUiState(
    val phase: Phase = Phase.LOCALIZING,
    val statusMessage: String = "",
    val direction: String = "직진",
    val currentDistanceM: Int = 0,
    val nextDistanceM: Int = 0,
    val isArrived: Boolean = false,
    val turnDirection: TurnDirection = TurnDirection.STRAIGHT,
    /** 다음의 다음 턴 방향 (서브 카드 아이콘용). */
    val nextTurnDirection: TurnDirection = TurnDirection.STRAIGHT,
    /** 사용자가 폰을 아래로 내려다보고 있을 때만 true (점선 나침반 표시 조건). */
    val showCompass: Boolean = false,
    /** 다음 턴 방향과 현재 heading의 각도차(deg). 양수=오른쪽, 음수=왼쪽. */
    val compassAngleDeg: Float = 0f,
    /**
     * 다음 턴 지점에 대한 LLM이 생성한 안내 문구.
     * 예: "GS25 명동점에서 우회전하세요." / "남포면옥까지 152m, 약 3분 소요됩니다."
     * 비어있으면 단순한 [direction] + 거리로 폴백.
     */
    val currentSpeech: String = "",
) {
    enum class Phase { LOCALIZING, ROUTING, ARRIVED }
}

enum class TurnDirection { LEFT, RIGHT, STRAIGHT, DESTINATION }

/**
 * AR 길안내용 실제 ARSceneView를 Compose에 임베드하는 컴포넌트.
 *
 * 동작:
 * 1. CAMERA + ACCESS_FINE_LOCATION 권한 요청
 * 2. ARCore Geospatial 모드로 ARScene 띄움
 * 3. 정확도 < 10m 잡히면 백엔드 `/navigation/search` + `/navigation/route` 호출 (MainViewModel)
 * 4. 응답 라우트를 노드로 파싱 → 매 2초 nearby 화살표 3D 모델 렌더
 * 5. 매 프레임 거리 계산 + UI 상태(`ArNavUiState`) 콜백
 * 6. 8m 이내 진입 시 다음 턴으로 자동 진행, 도착 감지
 */
@Composable
fun ArRealSceneView(
    modifier: Modifier = Modifier,
    targetDestination: String = "",
    onPoseUpdate: (latitude: Double, longitude: Double, heading: Double, altitude: Double, horizontalAccuracy: Double) -> Unit = { _, _, _, _, _ -> },
    onNavigationUpdate: (ArNavUiState) -> Unit = {},
    /** 라우트 응답 도착 시 한 번 호출. 미니맵 폴리라인/목적지 마커용. */
    onRouteAvailable: (routePoints: List<Pair<Double, Double>>, destinationLat: Double, destinationLng: Double) -> Unit = { _, _, _ -> },
    voiceOn: Boolean = true,
    buildingsCache: Map<String, Building> = emptyMap(),
    onBuildingPinClick: (pinName: String, ufid: String?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val viewNodeManager = rememberViewNodeManager()
    val coroutineScope = rememberCoroutineScope()

    // 권한
    var permissionsGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results -> permissionsGranted = results.values.all { it } },
    )
    LaunchedEffect(Unit) {
        if (!permissionsGranted) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION),
            )
        }
    }

    if (!permissionsGranted) {
        Box(modifier = modifier.fillMaxSize().background(ScanPangColors.Background))
        return
    }

    // 길안내 ViewModel (RouteRepositoryImpl + MainViewModelFactory)
    val mainViewModel: MainViewModel = viewModel(
        factory = remember { MainViewModelFactory(RouteRepositoryImpl()) },
    )
    val routeData by mainViewModel.routeData.collectAsState()

    // 목적지 들어오면 LOCALIZING으로 초기화
    LaunchedEffect(targetDestination) {
        if (targetDestination.isNotEmpty()) {
            mainViewModel.updateState(NavigationState.LOCALIZING)
            onNavigationUpdate(
                ArNavUiState(
                    phase = ArNavUiState.Phase.LOCALIZING,
                    statusMessage = "주변을 스캔하세요.",
                ),
            )
        }
    }

    // TTS 컨트롤러 — 화면 진입 시 시작, 종료 시 shutdown
    val ttsController = remember {
        ArExploreTtsController(context, onPlayingChange = { /* 재생 상태 외부 노출 불필요 */ })
    }
    DisposableEffect(ttsController) {
        ttsController.start()
        onDispose { ttsController.shutdown() }
    }
    LaunchedEffect(voiceOn) {
        if (!voiceOn) ttsController.stop()
    }

    // 화면이 dispose 됐는지 추적 — 비동기 코루틴이 destroy된 리소스에 접근하는 걸 방지.
    // (홈/뒤로가기 시 모델 로드·TTS 코루틴이 늦게 완료돼 크래시 나는 케이스 차단)
    val isMounted = remember { mutableStateOf(true) }

    // 음성 안내 중복 방지용 상태
    var spokenForTurnIndex by remember { mutableStateOf(-1) }     // 마지막으로 음성 출력한 turn index
    var hasSpokenDeparture by remember { mutableStateOf(false) }  // 출발 안내 출력 여부
    var hasSpokenArrival by remember { mutableStateOf(false) }    // 도착 안내 출력 여부

    // 주변 건물 PIN 오버레이 상태 — FOV+Occlusion+FrontEdge (탐색모드와 동일 로직)
    val navBuildingPins = remember { mutableStateListOf<NavBuildingPin>() }
    val navBuildingAnchors = remember { mutableMapOf<String, Anchor>() }
    val navBuildingScreenPositions = remember { mutableStateOf<Map<String, Pair<Float, Float>>>(emptyMap()) }
    var lastNavBuildingVisibilityTime by remember { mutableStateOf(0L) }

    // 도착 상태 — ViewNode2 안의 Compose가 구독해서 배지를 초록 체크로 자동 전환
    val isArrivedState = remember { mutableStateOf(false) }

    // AR 노드 + 라우트 상태
    val routeNodes = remember { mutableStateListOf<Node>() }
    var fullRouteNodes by remember { mutableStateOf<List<ArRouteNode>>(emptyList()) }
    val majorPointIndices = remember { mutableStateListOf<Int>() }
    var currentTargetPointIndex by remember { mutableStateOf(1) }
    val renderedIndices = remember { mutableSetOf<Int>() }
    val activeArNodes = remember { mutableMapOf<Int, AnchorNode>() }
    val activeChildNodes = remember { mutableMapOf<Int, Node>() }       // 앞면 (정상 방향 아이콘)
    val activeBackChildNodes = remember { mutableMapOf<Int, Node>() }   // 뒷면 (거울상 방향 아이콘)
    // 앵커 생성 시점의 카메라 고도 — 매 프레임 ViewNode2 child의 local Y를
    // (현재 cameraAlt - 생성 시 cameraAlt)로 갱신해 사용자 고도 변화 추적.
    val anchorCreationAltitudes = remember { mutableMapOf<Int, Double>() }
    val turnDirectionMap = remember { mutableMapOf<Int, Boolean>() }
    var lastChunkRenderTime by remember { mutableStateOf(0L) }
    var routeStartTime by remember { mutableStateOf(0L) }

    // 라우트 응답 도착 → 노드 파싱 + 미니맵용 좌표 전달
    LaunchedEffect(routeData) {
        val route = routeData
        if (route is NavRouteResponse) {
            val nodes = parseNavResponse(route)
            if (nodes.isNotEmpty()) {
                clearArState(routeNodes, activeArNodes, activeChildNodes, activeBackChildNodes, renderedIndices)
                turnDirectionMap.clear()  // 새 라우트가 도착할 때만 이전 방향 정보 폐기
                isArrivedState.value = false  // 새 라우트 시작 — 도착 상태 리셋
                fullRouteNodes = nodes
                majorPointIndices.clear()
                nodes.forEachIndexed { i, n -> if (n.type != NodeType.PATH_POINT) majorPointIndices.add(i) }
                currentTargetPointIndex = 1
                lastChunkRenderTime = 0L
                routeStartTime = System.currentTimeMillis()

                // 라우트 도착 시 모든 major point의 좌/우 방향을 사전 계산.
                // turnDirectionMap에 키=major index, 값=true(우)/false(좌)/없음(직진)으로 저장.
                // 이후 현재 턴/다음 턴 모두 같은 맵에서 즉시 조회 → 매 프레임 비용 0.
                for ((mi, nodeIdx) in majorPointIndices.withIndex()) {
                    val node = nodes[nodeIdx]
                    if (node.type != NodeType.TURN_POINT) continue
                    val isRight: Boolean? = when (node.turnType) {
                        13, 19, 18 -> true
                        12, 16, 17 -> false
                        else -> calcTurnFromBearing(nodeIdx, node, nodes)
                    }
                    if (isRight != null) turnDirectionMap[mi] = isRight
                }

                val arCommand = route.ar_command
                if (arCommand != null) {
                    // 미니맵 폴리라인은 T-Map 원본 route_line을 그대로 사용 (순서 보장).
                    // 보간/턴포인트가 끼워넣어진 nodes는 AR 화살표·턴 감지용으로 별도 사용.
                    onRouteAvailable(
                        arCommand.route_line.map { it.lat to it.lng },
                        arCommand.destination.lat,
                        arCommand.destination.lng,
                    )

                    // 새 라우트 도착 시 건물 PIN 상태 초기화 — FOV 로직이 다음 프레임부터 재구성
                    navBuildingAnchors.values.forEach { runCatching { it.detach() } }
                    navBuildingAnchors.clear()
                    navBuildingScreenPositions.value = emptyMap()
                    navBuildingPins.clear()
                    lastNavBuildingVisibilityTime = 0L
                }

                // 출발 안내 음성 (route.speech) — 한 번만, 2초 지연 후 재생
                if (!hasSpokenDeparture && route.speech.isNotBlank()) {
                    val message = route.speech
                    coroutineScope.launch {
                        delay(2000)
                        if (!isMounted.value) return@launch
                        runCatching { ttsController.speakIfEnabled(message, voiceOn = voiceOn) }
                    }
                    hasSpokenDeparture = true
                }
                spokenForTurnIndex = -1
                hasSpokenArrival = false
            }
        }
    }

    // 화면 종료 시 AR 리소스 정리 + mounted 플래그 false.
    //
    // 정리 순서:
    //  1. ViewNode2 child들 명시 destroy — Filament texture/stream/material 해제.
    //     이걸 안 하면 engine 정리 시 libfilament-jni가 PreconditionPanic으로 SIGABRT.
    //  2. AnchorNode.destroy()는 호출 안 함 — ARCore session이 SceneView 내부에서
    //     이미 정리됐을 수 있어 ArAnchor_detach가 native NPE(SIGSEGV)를 냄.
    //     SceneView가 자체 lifecycle에서 anchor 정리해줌.
    DisposableEffect(Unit) {
        onDispose {
            isMounted.value = false
            // ViewNode2 명시적 정리.
            // ⚠️ SceneView 2.3.3의 ViewNode2.destroy()는 순서 버그가 있어 그냥 호출하면
            //   "destroying MaterialInstance which is still in use by Renderable" Filament panic.
            //   우회: Renderable component를 먼저 destroy해서 material binding을 풀고
            //   그 다음 ViewNode2.destroy() 호출 (material → texture → stream 순으로 정리).
            (activeChildNodes.values + activeBackChildNodes.values).forEach { node ->
                if (node is ViewNode2) {
                    runCatching { engine.renderableManager.destroy(node.entity) }
                    runCatching { node.destroy() }
                }
            }
            routeNodes.clear()
            activeArNodes.clear()
            activeChildNodes.clear()
            activeBackChildNodes.clear()
            anchorCreationAltitudes.clear()
            renderedIndices.clear()
            navBuildingAnchors.clear()
        }
    }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    Box(modifier = modifier.fillMaxSize()) {
    ARScene(
        modifier = Modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        viewNodeWindowManager = viewNodeManager,
        childNodes = routeNodes,
        planeRenderer = false,
        sessionConfiguration = { _, config ->
            config.geospatialMode = Config.GeospatialMode.ENABLED
            config.focusMode = Config.FocusMode.AUTO
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            config.depthMode = Config.DepthMode.DISABLED
        },
        onSessionUpdated = { session, frame ->
            if (!isMounted.value) return@ARScene
            val earth = session.earth ?: return@ARScene
            if (earth.earthState != Earth.EarthState.ENABLED ||
                earth.trackingState != TrackingState.TRACKING
            ) return@ARScene

            val pose = earth.cameraGeospatialPose
            val lat = pose.latitude
            val lng = pose.longitude
            val cameraAlt = pose.altitude
            val now = System.currentTimeMillis()

            onPoseUpdate(lat, lng, pose.heading, cameraAlt, pose.horizontalAccuracy)

            // ── 고도 추적: 각 ViewNode2 child의 local Y를 카메라 고도 변화량으로 갱신
            // 앵커 생성 시점 고도(creationAlt) 대비 현재 cameraAlt 차이만큼 child를 위/아래로 이동
            // → 사용자가 경사로/계단 올라가도 배지가 따라 올라옴 (눈높이 유지)
            activeChildNodes.forEach { (k, node) ->
                if (node is ViewNode2) {
                    val creationAlt = anchorCreationAltitudes[k] ?: return@forEach
                    val deltaAlt = (cameraAlt - creationAlt).toFloat()
                    val p = node.position
                    node.position = Float3(p.x, deltaAlt, p.z)
                }
            }
            activeBackChildNodes.forEach { (k, node) ->
                if (node is ViewNode2) {
                    val creationAlt = anchorCreationAltitudes[k] ?: return@forEach
                    val deltaAlt = (cameraAlt - creationAlt).toFloat()
                    val p = node.position
                    node.position = Float3(p.x, deltaAlt, p.z)
                }
            }

            // 카메라 pitch — 폰을 아래로 내려다보는지 판정 (점선 나침반 표시 조건)
            val poseMatrix = FloatArray(16)
            frame.camera.pose.toMatrix(poseMatrix, 0)
            val isLookingDown = poseMatrix[9] > 0.5f

            // ── 건물 PIN: 300ms 스로틀로 FOV+Occlusion+FrontEdge 계산 (탐색모드 동일 로직) ──
            if (buildingsCache.isNotEmpty() && pose.horizontalAccuracy < 3.0 && now - lastNavBuildingVisibilityTime > 300) {
                lastNavBuildingVisibilityTime = now

                val fov = buildFovPolygon(lat, lng, pose.heading)

                val candidates = buildingsCache.values
                    .mapNotNull { b ->
                        val center = computeCentroid(b.geom) ?: return@mapNotNull null
                        computeAngularFootprint(b.geom, lat, lng) ?: return@mapNotNull null
                        val r = FloatArray(1)
                        Location.distanceBetween(lat, lng, center.first, center.second, r)
                        val dist = r[0]
                        if (dist >= 70f) return@mapNotNull null
                        val firstRing = b.geom.coordinates.firstOrNull()?.firstOrNull() ?: return@mapNotNull null
                        val buildingPoly = firstRing.map { Pair(it[0], it[1]) }
                        val visible = clipPolygon(buildingPoly, fov)
                        if (visible.isEmpty()) return@mapNotNull null
                        NavBuildingCandidate(b, center.first, center.second, dist, visible)
                    }
                    .sortedBy { it.distance }

                data class VisibleEntry(val b: Building, val markerPos: Pair<Double, Double>, val dist: Float)
                val visibleCandidates = mutableListOf<VisibleEntry>()
                for (cand in candidates) {
                    val markerPos = computeFrontEdgeMidpoint(cand.visiblePolygon, lat, lng)
                        ?: Pair(cand.centerLat, cand.centerLng)
                    val isOccluded = visibleCandidates.any { entry ->
                        if (cand.distance - entry.dist < 5f) return@any false
                        val occluderPoly = entry.b.geom.coordinates.firstOrNull()
                            ?.firstOrNull()?.map { Pair(it[0], it[1]) } ?: return@any false
                        isRayBlockedByPolygon(lng, lat, markerPos.second, markerPos.first, occluderPoly)
                    }
                    if (!isOccluded) visibleCandidates.add(VisibleEntry(cand.building, markerPos, cand.distance))
                    if (visibleCandidates.size >= 30) break
                }

                val newVisibleIds = visibleCandidates.map { e ->
                    "navbld_${e.b.ufid.ifEmpty { e.b.h3_index_10 }}_${e.b.hashCode()}"
                }.toSet()

                // 더 이상 보이지 않는 핀 제거
                val toRemove = navBuildingPins.filter { it.id !in newVisibleIds }.map { it.id }
                toRemove.forEach { id ->
                    navBuildingAnchors[id]?.runCatching { detach() }
                    navBuildingAnchors.remove(id)
                }
                navBuildingPins.removeAll { it.id !in newVisibleIds }

                // 새/갱신 핀 처리
                val existingIds = navBuildingPins.map { it.id }.toSet()
                for (entry in visibleCandidates) {
                    val id = "navbld_${entry.b.ufid.ifEmpty { entry.b.h3_index_10 }}_${entry.b.hashCode()}"
                    val labelAlt = cameraAlt
                    if (id !in existingIds) {
                        runCatching {
                            val anchor = earth.createAnchor(entry.markerPos.first, entry.markerPos.second, labelAlt, 0f, 0f, 0f, 1f)
                            if (anchor != null) {
                                navBuildingAnchors[id] = anchor
                                navBuildingPins.add(NavBuildingPin(id, entry.b.bld_nm ?: "건물", entry.markerPos.first, entry.markerPos.second, entry.b.ufid.ifEmpty { null }))
                            }
                        }
                    } else {
                        val existing = navBuildingPins.firstOrNull { it.id == id } ?: continue
                        val r = FloatArray(1)
                        Location.distanceBetween(existing.lat, existing.lng, entry.markerPos.first, entry.markerPos.second, r)
                        if (r[0] > 3f) {
                            runCatching {
                                val newAnchor = earth.createAnchor(entry.markerPos.first, entry.markerPos.second, labelAlt, 0f, 0f, 0f, 1f)
                                if (newAnchor != null) {
                                    navBuildingAnchors[id]?.runCatching { detach() }
                                    navBuildingAnchors[id] = newAnchor
                                    val idx = navBuildingPins.indexOfFirst { it.id == id }
                                    if (idx != -1) navBuildingPins[idx] = navBuildingPins[idx].copy(lat = entry.markerPos.first, lng = entry.markerPos.second)
                                }
                            }
                        }
                    }
                }
            }

            // 건물 앵커 → 화면 좌표 투영 (매 프레임)
            if (navBuildingAnchors.isNotEmpty()) {
                val proj = FloatArray(16)
                val view = FloatArray(16)
                frame.camera.getProjectionMatrix(proj, 0, 0.1f, 500f)
                frame.camera.getViewMatrix(view, 0)
                val viewProj = FloatArray(16)
                android.opengl.Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0)
                val newPositions = mutableMapOf<String, Pair<Float, Float>>()
                navBuildingAnchors.forEach { (key, anchor) ->
                    val t = anchor.pose.translation
                    val clip = FloatArray(4)
                    android.opengl.Matrix.multiplyMV(clip, 0, viewProj, 0, floatArrayOf(t[0], t[1], t[2], 1f), 0)
                    if (clip[3] <= 0f) return@forEach
                    val ndcX = clip[0] / clip[3]
                    val ndcY = clip[1] / clip[3]
                    if (ndcX < -1.4f || ndcX > 1.4f || ndcY < -1.4f || ndcY > 1.4f) return@forEach
                    newPositions[key] = (ndcX + 1f) / 2f to (1f - ndcY) / 2f
                }
                navBuildingScreenPositions.value = newPositions
            }

            val navState = mainViewModel.navigationState.value

            // 1) LOCALIZING + 정확도 확보 + 목적지 있음 → 백엔드 라우트 요청
            if (navState == NavigationState.LOCALIZING &&
                pose.horizontalAccuracy < 3.0 &&
                targetDestination.isNotEmpty()
            ) {
                mainViewModel.updateState(NavigationState.READY_TO_ROUTE)
                mainViewModel.fetchRoute(lng.toString(), lat.toString(), targetDestination)
                onNavigationUpdate(
                    ArNavUiState(
                        phase = ArNavUiState.Phase.LOCALIZING,
                        statusMessage = "경로 탐색 중...",
                    ),
                )
                return@ARScene
            }

            // 2) READY_TO_ROUTE + 노드 준비됨 → 매 프레임 처리
            if (navState != NavigationState.READY_TO_ROUTE || majorPointIndices.isEmpty()) return@ARScene

            // 이미 도착한 상태면 ARRIVED UI를 계속 유지하고 이후 ROUTING 업데이트 차단
            if (isArrivedState.value) {
                onNavigationUpdate(
                    ArNavUiState(
                        phase = ArNavUiState.Phase.ARRIVED,
                        isArrived = true,
                        statusMessage = "목적지에 도착했습니다.",
                        direction = "목적지",
                        turnDirection = TurnDirection.DESTINATION,
                    ),
                )
                return@ARScene
            }

            if (currentTargetPointIndex < majorPointIndices.size) {
                val tn = fullRouteNodes[majorPointIndices[currentTargetPointIndex]]
                val distRes = FloatArray(2)
                Location.distanceBetween(lat, lng, tn.lat, tn.lng, distRes)
                val dist = distRes[0]

                // 도착 처리
                if (tn.type == NodeType.END && dist <= 25.0f && !isArrivedState.value) {
                    isArrivedState.value = true

                    // ── 목적지 도착: 모든 활성 배지에 애니메이션 ──
                    activeChildNodes.entries.mapNotNull { (k, ch) ->
                        val front = ch as? ViewNode2 ?: return@mapNotNull null
                        val anchor = activeArNodes[k] ?: return@mapNotNull null
                        val back = activeBackChildNodes[k] as? ViewNode2
                        val node = fullRouteNodes.getOrNull(k) ?: return@mapNotNull null
                        val mi = majorPointIndices.indexOf(k)
                        val dir = when (node.type) {
                            NodeType.END -> BadgeDirection.DESTINATION
                            NodeType.TURN_POINT -> when {
                                node.turnType == 17 -> BadgeDirection.LEFT
                                node.turnType == 18 -> BadgeDirection.RIGHT
                                else -> when (turnDirectionMap[mi]) {
                                    true -> BadgeDirection.RIGHT
                                    false -> BadgeDirection.LEFT
                                    else -> BadgeDirection.STRAIGHT
                                }
                            }
                            else -> return@mapNotNull null
                        }
                        Triple(front, back, Pair(anchor, dir))
                    }.forEach { (front, back, anchorDir) ->
                        val (anchor, dir) = anchorDir
                        launchBadgeSpinAnim(
                            front = front,
                            back = back,
                            anchor = anchor,
                            direction = dir,
                            spinMs = 800L,        // ← 목적지 전용 파라미터
                            switchAngle = 90f,    // ← 목적지 전용 파라미터
                            engine = engine,
                            viewNodeManager = viewNodeManager,
                            materialLoader = materialLoader,
                            isMounted = isMounted,
                            scope = coroutineScope,
                        )
                    }

                    val arrivalSpeech = tn.speech.ifBlank { "목적지에 도착했습니다." }
                    if (!hasSpokenArrival) {
                        coroutineScope.launch {
                            delay(2000)
                            if (!isMounted.value) return@launch
                            runCatching { ttsController.speakIfEnabled(arrivalSpeech, voiceOn = voiceOn) }
                        }
                        hasSpokenArrival = true
                    }
                    onNavigationUpdate(
                        ArNavUiState(
                            phase = ArNavUiState.Phase.ARRIVED,
                            isArrived = true,
                            statusMessage = arrivalSpeech,
                            direction = "목적지",
                            turnDirection = TurnDirection.DESTINATION,
                            currentSpeech = arrivalSpeech,
                        ),
                    )
                    return@ARScene
                }

                // 다음 턴 방향
                val direction = when (tn.type) {
                    NodeType.END -> "목적지"
                    NodeType.TURN_POINT -> when (turnDirectionMap[currentTargetPointIndex]) {
                        true -> if (tn.turnType == 18) "우측 경로" else "우회전"
                        false -> if (tn.turnType == 17) "좌측 경로" else "좌회전"
                        null -> "직진"
                    }
                    else -> "직진"
                }
                val turnDir = when (direction) {
                    "좌회전", "좌측 경로" -> TurnDirection.LEFT
                    "우회전", "우측 경로" -> TurnDirection.RIGHT
                    "목적지" -> TurnDirection.DESTINATION
                    else -> TurnDirection.STRAIGHT
                }

                // 다음 턴 이후의 거리 + 방향 (preview용)
                // 방향은 사전 계산된 turnDirectionMap에서 그대로 조회 → 메인 카드 / 3D 모델과 동일 소스.
                var nextDist = 0
                var nextTurnDir = TurnDirection.STRAIGHT
                if (currentTargetPointIndex + 1 < majorPointIndices.size) {
                    val nextMi = currentTargetPointIndex + 1
                    val nn = fullRouteNodes[majorPointIndices[nextMi]]
                    Location.distanceBetween(tn.lat, tn.lng, nn.lat, nn.lng, distRes)
                    nextDist = distRes[0].toInt()
                    nextTurnDir = when (nn.type) {
                        NodeType.END -> TurnDirection.DESTINATION
                        NodeType.TURN_POINT -> when (turnDirectionMap[nextMi]) {
                            true -> TurnDirection.RIGHT
                            false -> TurnDirection.LEFT
                            null -> TurnDirection.STRAIGHT
                        }
                        else -> TurnDirection.STRAIGHT
                    }
                }

                // 점선 나침반: 다음 턴 방향과 현재 heading의 각도차 (양수=오른쪽 회전 필요)
                val bearingResults = FloatArray(2)
                Location.distanceBetween(lat, lng, tn.lat, tn.lng, bearingResults)
                var compassAngle = bearingResults[1] - pose.heading.toFloat()
                while (compassAngle > 180f) compassAngle -= 360f
                while (compassAngle < -180f) compassAngle += 360f

                onNavigationUpdate(
                    ArNavUiState(
                        phase = ArNavUiState.Phase.ROUTING,
                        direction = direction,
                        currentDistanceM = dist.toInt(),
                        nextDistanceM = nextDist,
                        turnDirection = turnDir,
                        nextTurnDirection = nextTurnDir,
                        statusMessage = "${direction}까지 ${dist.toInt()}m",
                        showCompass = isLookingDown,
                        compassAngleDeg = compassAngle,
                        currentSpeech = tn.speech,
                    ),
                )

                // 새 턴에 대한 음성 안내 (currentTargetPointIndex가 바뀐 직후 한 번만, 2초 지연)
                if (spokenForTurnIndex != currentTargetPointIndex && tn.speech.isNotBlank()) {
                    val message = tn.speech
                    coroutineScope.launch {
                        delay(2000)
                        if (!isMounted.value) return@launch
                        runCatching { ttsController.speakIfEnabled(message, voiceOn = voiceOn) }
                    }
                    spokenForTurnIndex = currentTargetPointIndex
                }

                // 20m 이내 → 해당 배지 X축 180° 회전 + 120°에서 초록 swap, 다음 턴으로 진행
                // 라우트 시작 후 2초 유예: 출발 직후 첫 포인트가 이미 20m 이내인 경우 즉시 스킵 방지
                if (dist <= 20.0f && (now - routeStartTime) >= 2000L) {
                    if (tn.type == NodeType.TURN_POINT) {
                        val animKey = majorPointIndices[currentTargetPointIndex]
                        val animFront = activeChildNodes[animKey] as? ViewNode2
                        val animBack = activeBackChildNodes[animKey] as? ViewNode2
                        val animAnchor = activeArNodes[animKey]
                        val animDir = when {
                            tn.turnType == 17 -> BadgeDirection.LEFT
                            tn.turnType == 18 -> BadgeDirection.RIGHT
                            else -> when (turnDirectionMap[currentTargetPointIndex]) {
                                true -> BadgeDirection.RIGHT
                                false -> BadgeDirection.LEFT
                                else -> BadgeDirection.STRAIGHT
                            }
                        }
                        if (animFront != null && animAnchor != null) {
                            activeChildNodes.remove(animKey)
                            activeBackChildNodes.remove(animKey)
                            // ── 턴 통과: 해당 배지 단독 애니메이션 ──
                            launchBadgeSpinAnim(
                                front = animFront,
                                back = animBack,
                                anchor = animAnchor,
                                direction = animDir,
                                spinMs = 800L,        // ← 턴 통과 전용 파라미터
                                switchAngle = 120f,   // ← 턴 통과 전용 파라미터
                                engine = engine,
                                viewNodeManager = viewNodeManager,
                                materialLoader = materialLoader,
                                isMounted = isMounted,
                                scope = coroutineScope,
                            )
                        }
                    }
                    currentTargetPointIndex++
                }
            }

            // 0.5초마다 nearby 화살표 렌더
            if (now - lastChunkRenderTime > 500) {
                lastChunkRenderTime = now
                renderNearbyArrows(
                    earth = earth,
                    fullRouteNodes = fullRouteNodes,
                    majorPointIndices = majorPointIndices,
                    currentTargetPointIndex = currentTargetPointIndex,
                    renderedIndices = renderedIndices,
                    activeArNodes = activeArNodes,
                    activeChildNodes = activeChildNodes,
                    activeBackChildNodes = activeBackChildNodes,
                    anchorCreationAltitudes = anchorCreationAltitudes,
                    turnDirectionMap = turnDirectionMap,
                    engine = engine,
                    materialLoader = materialLoader,
                    viewNodeWindowManager = viewNodeManager,
                    routeNodes = routeNodes,
                )
            }
        },
    )

    // 건물 PIN 오버레이 — FOV+Occlusion 필터된 핀을 화면 투영 위치에 표시
    val positions = navBuildingScreenPositions.value
    navBuildingPins.forEach { pin ->
        val pos = positions[pin.id]
        if (pos != null) {
            val xDp = with(density) { (pos.first * screenWidthPx).toDp() }
            val yDp = with(density) { (pos.second * screenHeightPx).toDp() }
            key(pin.id) {
                ArPoiCard(
                    title = pin.name,
                    subtitle = "건물",
                    modifier = Modifier.offset(x = xDp - 60.dp, y = yDp - 32.dp),
                    onClick = { onBuildingPinClick(pin.name, pin.ufid) },
                )
            }
        }
    }
    } // Box
}

/**
 * AR 렌더 상태(앵커·모델·렌더 인덱스)만 초기화.
 *
 * `turnDirectionMap`은 라우트 도착 시 사전 계산되는 메타데이터라 chunk 진행 시
 * 보존되어야 함 (지우면 두 번째 턴부터 방향 정보 잃어 "직진" 폴백 발생).
 * 새 라우트 도착 시에만 LaunchedEffect에서 명시적으로 별도 clear.
 */
private fun clearArState(
    routeNodes: SnapshotStateList<Node>,
    activeArNodes: MutableMap<Int, AnchorNode>,
    activeChildNodes: MutableMap<Int, Node>,
    activeBackChildNodes: MutableMap<Int, Node>,
    renderedIndices: MutableSet<Int>,
) {
    routeNodes.clear()
    activeArNodes.values.forEach { runCatching { it.destroy() } }
    activeArNodes.clear()
    activeChildNodes.clear()
    activeBackChildNodes.clear()
    renderedIndices.clear()
}

/**
 * 현재 chunk 구간(이전 major point ~ 다음 major point) 안의 노드들을
 * Geospatial 앵커 + Compose 배지(ViewNode2)로 변환해 ARScene에 추가.
 *
 * Path A: Compose UI를 3D 평면 빌보드로 렌더 (.glb 사용 안 함).
 */
private fun renderNearbyArrows(
    earth: Earth,
    fullRouteNodes: List<ArRouteNode>,
    majorPointIndices: List<Int>,
    currentTargetPointIndex: Int,
    renderedIndices: MutableSet<Int>,
    activeArNodes: MutableMap<Int, AnchorNode>,
    activeChildNodes: MutableMap<Int, Node>,
    activeBackChildNodes: MutableMap<Int, Node>,
    anchorCreationAltitudes: MutableMap<Int, Double>,
    turnDirectionMap: MutableMap<Int, Boolean>,
    engine: Engine,
    materialLoader: MaterialLoader,
    viewNodeWindowManager: ViewNode2.WindowManager,
    routeNodes: SnapshotStateList<Node>,
) {
    if (earth.trackingState != TrackingState.TRACKING ||
        majorPointIndices.size < 2 ||
        currentTargetPointIndex >= majorPointIndices.size
    ) return

    val startIdx = majorPointIndices[currentTargetPointIndex - 1]
    val endIdx = majorPointIndices[currentTargetPointIndex]
    val cameraPose = earth.cameraGeospatialPose
    val cameraAlt = cameraPose.altitude

    for (i in startIdx..endIdx) {
        if (renderedIndices.contains(i)) continue
        val node = fullRouteNodes[i]
        if (node.type == NodeType.PATH_POINT || node.type == NodeType.START) {
            // PATH_POINT는 보간점이라 렌더 X. START는 사용자가 이미 그 자리에 있어 불필요.
            renderedIndices.add(i)
            continue
        }
        // TURN_POINT 배지는 80m 이내에서만 렌더 — 멀리서 VPS 오차로 엉뚱한 곳에 박히는 문제 방지
        if (node.type == NodeType.TURN_POINT) {
            val d = FloatArray(1)
            Location.distanceBetween(cameraPose.latitude, cameraPose.longitude, node.lat, node.lng, d)
            if (d[0] > 80f) continue
        }
        // END 배지는 60m 이내에서만 렌더 — 그 밖이면 스킵하고 다음 사이클에 재확인
        if (node.type == NodeType.END) {
            val d = FloatArray(1)
            Location.distanceBetween(cameraPose.latitude, cameraPose.longitude, node.lat, node.lng, d)
            if (d[0] > 60f) continue
        }
        renderedIndices.add(i)

        val yOff = if (node.type == NodeType.TURN_POINT) 0.5 else 1.5
        val anchor = earth.createAnchor(node.lat, node.lng, cameraAlt - yOff, 0f, 0f, 0f, 1f) ?: continue
        val anchorNode = AnchorNode(engine = engine, anchor = anchor)
        activeArNodes[i] = anchorNode
        anchorCreationAltitudes[i] = cameraAlt  // 매 프레임 child 고도 갱신용
        routeNodes.add(anchorNode)

        val direction = resolveBadgeDirection(
            node = node,
            i = i,
            fullRouteNodes = fullRouteNodes,
            majorPointIndices = majorPointIndices,
            turnDirectionMap = turnDirectionMap,
        ) ?: continue

        val rotationY = computeArrowRotation(i, node, fullRouteNodes)

        // Back-to-back 두 평면 (원래 yaw 할당으로 복귀)
        //   앞면 = rotationY
        //   뒷면 = rotationY + 180°, 거울상 콘텐츠
        // 앞면
        runCatching {
            val front = ViewNode2(
                engine = engine,
                windowManager = viewNodeWindowManager,
                materialLoader = materialLoader,
                unlit = true,
            ) {
                MaterialTheme {
                    ArInWorldBadgeContent(direction = direction, isArrived = false)
                }
            }.apply {
                this.rotation = Float3(0f, rotationY, 0f)
            }
            runCatching { front.materialInstance.setCullingMode(Material.CullingMode.BACK) }
            anchorNode.addChildNode(front)
            activeChildNodes[i] = front
        }
        // 뒷면 — yaw +180°
        runCatching {
            val back = ViewNode2(
                engine = engine,
                windowManager = viewNodeWindowManager,
                materialLoader = materialLoader,
                unlit = true,
            ) {
                MaterialTheme {
                    ArInWorldBadgeContent(direction = direction, isArrived = false)
                }
            }.apply {
                this.rotation = Float3(0f, rotationY + 180f, 0f)
            }
            runCatching { back.materialInstance.setCullingMode(Material.CullingMode.BACK) }
            anchorNode.addChildNode(back)
            activeBackChildNodes[i] = back
        }
    }
}

/**
 * 노드 타입과 turnType에 따라 배지 방향을 결정. turnDirectionMap에 좌/우 정보 기록.
 * null이면 배지를 그리지 않음.
 */
private fun resolveBadgeDirection(
    node: ArRouteNode,
    i: Int,
    fullRouteNodes: List<ArRouteNode>,
    majorPointIndices: List<Int>,
    turnDirectionMap: MutableMap<Int, Boolean>,
): BadgeDirection? = when (node.type) {
    NodeType.END -> BadgeDirection.DESTINATION
    NodeType.START -> null  // START는 사용자가 이미 그 자리에 있어 렌더 안 함
    NodeType.TURN_POINT -> {
        // 측면 분기 (turnType 17/18) 처리
        if (node.turnType == 17 || node.turnType == 18) {
            val mi = majorPointIndices.indexOf(i)
            if (mi != -1) turnDirectionMap[mi] = (node.turnType == 18)
            if (node.turnType == 18) BadgeDirection.RIGHT else BadgeDirection.LEFT
        } else {
            // 일반 좌/우/직진 결정
            val isRight: Boolean? = when (node.turnType) {
                13, 19 -> true
                12, 16 -> false
                else -> calcTurnFromBearing(i, node, fullRouteNodes)
            }
            when (isRight) {
                true -> {
                    val mi = majorPointIndices.indexOf(i)
                    if (mi != -1) turnDirectionMap[mi] = true
                    BadgeDirection.RIGHT
                }
                false -> {
                    val mi = majorPointIndices.indexOf(i)
                    if (mi != -1) turnDirectionMap[mi] = false
                    BadgeDirection.LEFT
                }
                null -> BadgeDirection.STRAIGHT
            }
        }
    }
    else -> null
}

/** 진입 방향에 화살표가 정렬되도록 Y축 회전을 계산 (이전 비-turn 노드 → 현재 노드 bearing + 180°). */
private fun computeArrowRotation(i: Int, node: ArRouteNode, fullRouteNodes: List<ArRouteNode>): Float {
    var lbi = i - 1
    while (lbi > 0 && fullRouteNodes[lbi].type == NodeType.TURN_POINT) lbi--
    val lbn = fullRouteNodes.getOrNull(lbi) ?: node
    val ir = FloatArray(2)
    Location.distanceBetween(lbn.lat, lbn.lng, node.lat, node.lng, ir)
    return ir[1] + 180f
}

/** turnType이 모호할 때 앞/뒤 10m 노드의 bearing 차이로 좌/우 추정. */
private fun calcTurnFromBearing(i: Int, node: ArRouteNode, fullRouteNodes: List<ArRouteNode>): Boolean? {
    val d = FloatArray(1)
    var pi = i - 1
    while (pi > 0) {
        val c = fullRouteNodes[pi]
        if (c.type == NodeType.TURN_POINT) break
        Location.distanceBetween(c.lat, c.lng, node.lat, node.lng, d)
        if (d[0] >= 10f) break
        pi--
    }
    var ni = i + 1
    while (ni < fullRouteNodes.size - 1) {
        val c = fullRouteNodes[ni]
        if (c.type == NodeType.TURN_POINT) break
        Location.distanceBetween(node.lat, node.lng, c.lat, c.lng, d)
        if (d[0] >= 10f) break
        ni++
    }
    val ta = calcTurnAngle(
        bearingBetween(fullRouteNodes.getOrNull(pi) ?: node, node),
        bearingBetween(node, fullRouteNodes.getOrNull(ni) ?: node),
    )
    return if (kotlin.math.abs(ta) < 40f) null else ta >= 0f
}

/**
 * 배지(ViewNode2) 단독 X축 스핀 애니메이션.
 * [spinMs]ms 동안 X축 0→180° 회전하며, [switchAngle]° 도달 시 파랑→초록 ViewNode2 교체.
 * 목적지 도착 / 턴 통과 두 곳에서 독립적으로 파라미터를 지정해 호출.
 */
private fun launchBadgeSpinAnim(
    front: ViewNode2,
    back: ViewNode2?,
    anchor: AnchorNode,
    direction: BadgeDirection,
    spinMs: Long,
    switchAngle: Float,
    engine: com.google.android.filament.Engine,
    viewNodeManager: ViewNode2.WindowManager,
    materialLoader: MaterialLoader,
    isMounted: androidx.compose.runtime.State<Boolean>,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val startY = front.rotation.y
    val startPitch = 0f
    scope.launch {
        var switched = false
        var curFront = front
        var curBack = back
        val t0 = System.currentTimeMillis()
        while (isMounted.value) {
            val elapsed = System.currentTimeMillis() - t0
            val progress = (elapsed.toFloat() / spinMs).coerceIn(0f, 1f)
            val angle = progress * 180f

            if (!switched && angle >= switchAngle) {
                switched = true
                val newFront = runCatching {
                    ViewNode2(engine, viewNodeManager, materialLoader, unlit = true) {
                        androidx.compose.material3.MaterialTheme {
                            ArInWorldBadgeContent(direction = direction, isArrived = true)
                        }
                    }.apply {
                        rotation = Float3(90f, startY, 0f)
                    }.also { n -> runCatching { n.materialInstance.setCullingMode(com.google.android.filament.Material.CullingMode.BACK) } }
                }.getOrNull() ?: return@launch
                runCatching { anchor.removeChildNode(curFront) }
                runCatching { engine.renderableManager.destroy(curFront.entity) }
                runCatching { curFront.destroy() }
                anchor.addChildNode(newFront)
                curFront = newFront

                curBack?.let { oldBack ->
                    val newBack = runCatching {
                        ViewNode2(engine, viewNodeManager, materialLoader, unlit = true) {
                            androidx.compose.material3.MaterialTheme {
                                ArInWorldBadgeContent(direction = direction, isArrived = true)
                            }
                        }.apply {
                            rotation = Float3(90f, startY + 180f, 0f)
                        }.also { n -> runCatching { n.materialInstance.setCullingMode(com.google.android.filament.Material.CullingMode.BACK) } }
                    }.getOrNull()
                    runCatching { anchor.removeChildNode(oldBack) }
                    runCatching { engine.renderableManager.destroy(oldBack.entity) }
                    runCatching { oldBack.destroy() }
                    curBack = newBack
                    if (newBack != null) anchor.addChildNode(newBack)
                }
            }

            if (switched) {
                // 초록 배지: 90° → 0° (반대편에서 펼쳐지며 나타남)
                runCatching { curFront.rotation = Float3(180f - angle, startY, 0f) }
                runCatching { curBack?.rotation = Float3(180f - angle, startY + 180f, 0f) }
            } else {
                runCatching { curFront.rotation = Float3(angle, startY, 0f) }
                runCatching { curBack?.rotation = Float3(angle, startY + 180f, 0f) }
            }
            if (progress >= 1f) break
            delay(16)
        }
    }
}
