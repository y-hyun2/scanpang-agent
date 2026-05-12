package com.scanpang.app.components.ar

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    /** 공간증강(주변 건물 인식) 활성 여부. true이면 5초마다 [onPlaceQueryRequest] 호출. */
    spaceAugmentEnabled: Boolean = false,
    /** 공간증강 쿼리 요청. 부모가 백엔드 호출(viewModel.queryPlace)을 처리. */
    onPlaceQueryRequest: (heading: Double, lat: Double, lng: Double, alt: Double, pitch: Double) -> Unit = { _, _, _, _, _ -> },
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

    // 화면이 dispose 됐는지 추적 — 비동기 코루틴이 destroy된 리소스에 접근하는 걸 방지.
    // (홈/뒤로가기 시 모델 로드·TTS 코루틴이 늦게 완료돼 크래시 나는 케이스 차단)
    val isMounted = remember { mutableStateOf(true) }

    // 음성 안내 중복 방지용 상태
    var spokenForTurnIndex by remember { mutableStateOf(-1) }     // 마지막으로 음성 출력한 turn index
    var hasSpokenDeparture by remember { mutableStateOf(false) }  // 출발 안내 출력 여부
    var hasSpokenArrival by remember { mutableStateOf(false) }    // 도착 안내 출력 여부

    // 공간증강 쿼리 throttle용 — 5초 간격 보장
    var lastPlaceQueryTime by remember { mutableStateOf(0L) }

    // AR 노드 + 라우트 상태
    val routeNodes = remember { mutableStateListOf<Node>() }
    var fullRouteNodes by remember { mutableStateOf<List<ArRouteNode>>(emptyList()) }
    val majorPointIndices = remember { mutableStateListOf<Int>() }
    var currentTargetPointIndex by remember { mutableStateOf(1) }
    val renderedIndices = remember { mutableSetOf<Int>() }
    val activeArNodes = remember { mutableMapOf<Int, AnchorNode>() }
    val activeChildNodes = remember { mutableMapOf<Int, Node>() }
    val turnDirectionMap = remember { mutableMapOf<Int, Boolean>() }
    var lastChunkRenderTime by remember { mutableStateOf(0L) }

    // 라우트 응답 도착 → 노드 파싱 + 미니맵용 좌표 전달
    LaunchedEffect(routeData) {
        val route = routeData
        if (route is NavRouteResponse) {
            val nodes = parseNavResponse(route)
            if (nodes.isNotEmpty()) {
                clearArState(routeNodes, activeArNodes, activeChildNodes, renderedIndices)
                turnDirectionMap.clear()  // 새 라우트가 도착할 때만 이전 방향 정보 폐기
                fullRouteNodes = nodes
                majorPointIndices.clear()
                nodes.forEachIndexed { i, n -> if (n.type != NodeType.PATH_POINT) majorPointIndices.add(i) }
                currentTargetPointIndex = 1
                lastChunkRenderTime = 0L

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
                }

                // 출발 안내 음성 (route.speech) — 한 번만, 2초 지연 후 재생
                if (!hasSpokenDeparture && route.speech.isNotBlank()) {
                    val message = route.speech
                    coroutineScope.launch {
                        delay(2000)
                        if (!isMounted.value) return@launch
                        runCatching { ttsController.speakIfEnabled(message, voiceOn = true) }
                    }
                    hasSpokenDeparture = true
                }
                spokenForTurnIndex = -1
                hasSpokenArrival = false
            }
        }
    }

    // 화면 종료 시 AR 리소스 정리 + mounted 플래그 false.
    // dispose 경로에선 AnchorNode.destroy()를 호출하지 않음 — 그 시점엔 ARCore session이
    // SceneView 내부에서 이미 정리됐을 수 있고, 그러면 ArAnchor_detach가 native NPE로
    // SIGSEGV를 냄. SceneView가 lifecycle에서 anchor를 알아서 정리함.
    DisposableEffect(Unit) {
        onDispose {
            isMounted.value = false
            routeNodes.clear()
            activeArNodes.clear()
            activeChildNodes.clear()
            renderedIndices.clear()
        }
    }

    ARScene(
        modifier = modifier.fillMaxSize(),
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
            val earth = session.earth ?: return@ARScene
            if (earth.earthState != Earth.EarthState.ENABLED ||
                earth.trackingState != TrackingState.TRACKING
            ) return@ARScene

            val pose = earth.cameraGeospatialPose
            val lat = pose.latitude
            val lng = pose.longitude
            val now = System.currentTimeMillis()

            onPoseUpdate(lat, lng, pose.heading, pose.altitude, pose.horizontalAccuracy)

            // 카메라 pitch — 폰을 아래로 내려다보는지 판정 (점선 나침반 표시 조건)
            val poseMatrix = FloatArray(16)
            frame.camera.pose.toMatrix(poseMatrix, 0)
            val isLookingDown = poseMatrix[9] > 0.5f

            // 공간증강(주변 건물 인식) — 활성 시 5초마다 쿼리 발사
            if (spaceAugmentEnabled && now - lastPlaceQueryTime > 5000) {
                lastPlaceQueryTime = now
                // pose의 quaternion에서 forward 벡터 분해 → pitch 계산 (ArExploreScreen과 동일)
                val q = pose.eastUpSouthQuaternion
                val fx = 2f * (q[0] * q[2] + q[3] * q[1])
                val fy = 2f * (q[1] * q[2] - q[3] * q[0])
                val fz = 1f - 2f * (q[0] * q[0] + q[1] * q[1])
                val horiz = kotlin.math.sqrt(fx * fx + fz * fz)
                val pitch = Math.toDegrees(kotlin.math.atan2(-fy.toDouble(), horiz.toDouble()))
                onPlaceQueryRequest(pose.heading, lat, lng, pose.altitude, pitch)
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

            if (currentTargetPointIndex < majorPointIndices.size) {
                val tn = fullRouteNodes[majorPointIndices[currentTargetPointIndex]]
                val distRes = FloatArray(2)
                Location.distanceBetween(lat, lng, tn.lat, tn.lng, distRes)
                val dist = distRes[0]

                // 도착 처리
                if (tn.type == NodeType.END && dist <= 15.0f) {
                    clearArState(routeNodes, activeArNodes, activeChildNodes, renderedIndices)
                    val arrivalSpeech = tn.speech.ifBlank { "목적지에 도착했습니다." }
                    if (!hasSpokenArrival) {
                        coroutineScope.launch {
                            delay(2000)
                            if (!isMounted.value) return@launch
                            runCatching { ttsController.speakIfEnabled(arrivalSpeech, voiceOn = true) }
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
                        runCatching { ttsController.speakIfEnabled(message, voiceOn = true) }
                    }
                    spokenForTurnIndex = currentTargetPointIndex
                }

                // 8m 이내 → 다음 턴
                if (dist <= 8.0f) {
                    clearArState(routeNodes, activeArNodes, activeChildNodes, renderedIndices)
                    currentTargetPointIndex++
                }
            }

            // 2초마다 nearby 화살표 렌더
            if (now - lastChunkRenderTime > 2000) {
                lastChunkRenderTime = now
                renderNearbyArrows(
                    earth = earth,
                    fullRouteNodes = fullRouteNodes,
                    majorPointIndices = majorPointIndices,
                    currentTargetPointIndex = currentTargetPointIndex,
                    renderedIndices = renderedIndices,
                    activeArNodes = activeArNodes,
                    activeChildNodes = activeChildNodes,
                    turnDirectionMap = turnDirectionMap,
                    engine = engine,
                    materialLoader = materialLoader,
                    viewNodeWindowManager = viewNodeManager,
                    routeNodes = routeNodes,
                )
            }
        },
    )
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
    renderedIndices: MutableSet<Int>,
) {
    routeNodes.clear()
    activeArNodes.values.forEach { runCatching { it.destroy() } }
    activeArNodes.clear()
    activeChildNodes.clear()
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
        if (node.type == NodeType.PATH_POINT) {
            renderedIndices.add(i)
            continue
        }
        // END 배지는 35m 이내에서만 렌더 — 그 밖이면 스킵하고 다음 사이클에 재확인
        if (node.type == NodeType.END) {
            val d = FloatArray(1)
            Location.distanceBetween(cameraPose.latitude, cameraPose.longitude, node.lat, node.lng, d)
            if (d[0] > 35f) continue
        }
        renderedIndices.add(i)

        val yOff = if (node.type == NodeType.TURN_POINT) 0.5 else 1.5
        val anchor = earth.createAnchor(node.lat, node.lng, cameraAlt - yOff, 0f, 0f, 0f, 1f) ?: continue
        val anchorNode = AnchorNode(engine = engine, anchor = anchor)
        activeArNodes[i] = anchorNode
        routeNodes.add(anchorNode)

        val direction = resolveBadgeDirection(
            node = node,
            i = i,
            fullRouteNodes = fullRouteNodes,
            majorPointIndices = majorPointIndices,
            turnDirectionMap = turnDirectionMap,
        ) ?: continue

        val rotationY = computeArrowRotation(i, node, fullRouteNodes)

        // ViewNode2: Compose UI를 3D 평면 빌보드로 렌더.
        // unlit=true — Geospatial 씬에 광원이 없으므로 색상 그대로 출력.
        runCatching {
            val viewNode = ViewNode2(
                engine = engine,
                windowManager = viewNodeWindowManager,
                materialLoader = materialLoader,
                unlit = true,
            ) {
                MaterialTheme {
                    ArInWorldBadgeContent(direction = direction)
                }
            }.apply {
                // Z(roll) 0° — 회전 baseline 없음. 진입 방향(rotationY)만 적용.
                this.rotation = Float3(0f, rotationY, 0f)
            }
            anchorNode.addChildNode(viewNode)
            activeChildNodes[i] = viewNode
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
    NodeType.START, NodeType.END -> BadgeDirection.DESTINATION
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
