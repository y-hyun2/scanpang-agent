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
import com.scanpang.app.ui.theme.ScanPangColors
import com.scanpang.arnavigation.data.remote.dto.NavRouteResponse
import com.scanpang.arnavigation.data.repository.RouteRepositoryImpl
import com.scanpang.arnavigation.presentation.MainViewModel
import com.scanpang.arnavigation.presentation.MainViewModelFactory
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.CoroutineScope
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
) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
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

    // AR 노드 + 라우트 상태
    val routeNodes = remember { mutableStateListOf<Node>() }
    var fullRouteNodes by remember { mutableStateOf<List<ArRouteNode>>(emptyList()) }
    val majorPointIndices = remember { mutableStateListOf<Int>() }
    var currentTargetPointIndex by remember { mutableStateOf(1) }
    val renderedIndices = remember { mutableSetOf<Int>() }
    val activeArNodes = remember { mutableMapOf<Int, AnchorNode>() }
    val activeModelNodes = remember { mutableMapOf<Int, ModelNode>() }
    val turnDirectionMap = remember { mutableMapOf<Int, Boolean>() }
    var lastChunkRenderTime by remember { mutableStateOf(0L) }

    // 라우트 응답 도착 → 노드 파싱 + 미니맵용 좌표 전달
    LaunchedEffect(routeData) {
        val route = routeData
        if (route is NavRouteResponse) {
            val nodes = parseNavResponse(route)
            if (nodes.isNotEmpty()) {
                clearArState(routeNodes, activeArNodes, activeModelNodes, renderedIndices, turnDirectionMap)
                fullRouteNodes = nodes
                majorPointIndices.clear()
                nodes.forEachIndexed { i, n -> if (n.type != NodeType.PATH_POINT) majorPointIndices.add(i) }
                currentTargetPointIndex = 1
                lastChunkRenderTime = 0L

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
            }
        }
    }

    // 화면 종료 시 AR 리소스 정리
    DisposableEffect(Unit) {
        onDispose {
            clearArState(routeNodes, activeArNodes, activeModelNodes, renderedIndices, turnDirectionMap)
        }
    }

    ARScene(
        modifier = modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
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
                if (tn.type == NodeType.END && dist <= 11.0f) {
                    clearArState(routeNodes, activeArNodes, activeModelNodes, renderedIndices, turnDirectionMap)
                    val arrivalSpeech = tn.speech.ifBlank { "🎉 목적지에 도착했습니다!" }
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

                // 다음 턴 이후의 거리 (preview용)
                var nextDist = 0
                if (currentTargetPointIndex + 1 < majorPointIndices.size) {
                    val nn = fullRouteNodes[majorPointIndices[currentTargetPointIndex + 1]]
                    Location.distanceBetween(tn.lat, tn.lng, nn.lat, nn.lng, distRes)
                    nextDist = distRes[0].toInt()
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
                        statusMessage = "${direction}까지 ${dist.toInt()}m",
                        showCompass = isLookingDown,
                        compassAngleDeg = compassAngle,
                        currentSpeech = tn.speech,
                    ),
                )

                // 8m 이내 → 다음 턴
                if (dist <= 8.0f) {
                    clearArState(routeNodes, activeArNodes, activeModelNodes, renderedIndices, turnDirectionMap)
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
                    activeModelNodes = activeModelNodes,
                    turnDirectionMap = turnDirectionMap,
                    engine = engine,
                    modelLoader = modelLoader,
                    routeNodes = routeNodes,
                    coroutineScope = coroutineScope,
                )
            }
        },
    )
}

/** AR 노드 / 렌더 상태를 모두 초기화. (ARScene childNodes도 비움) */
private fun clearArState(
    routeNodes: SnapshotStateList<Node>,
    activeArNodes: MutableMap<Int, AnchorNode>,
    activeModelNodes: MutableMap<Int, ModelNode>,
    renderedIndices: MutableSet<Int>,
    turnDirectionMap: MutableMap<Int, Boolean>,
) {
    routeNodes.clear()
    activeArNodes.values.forEach { runCatching { it.destroy() } }
    activeArNodes.clear()
    activeModelNodes.clear()
    renderedIndices.clear()
    turnDirectionMap.clear()
}

/**
 * 현재 chunk 구간(이전 major point ~ 다음 major point) 안의 노드들을
 * Geospatial 앵커 + 3D 모델 노드로 변환해 ARScene에 추가.
 */
private fun renderNearbyArrows(
    earth: Earth,
    fullRouteNodes: List<ArRouteNode>,
    majorPointIndices: List<Int>,
    currentTargetPointIndex: Int,
    renderedIndices: MutableSet<Int>,
    activeArNodes: MutableMap<Int, AnchorNode>,
    activeModelNodes: MutableMap<Int, ModelNode>,
    turnDirectionMap: MutableMap<Int, Boolean>,
    engine: Engine,
    modelLoader: ModelLoader,
    routeNodes: SnapshotStateList<Node>,
    coroutineScope: CoroutineScope,
) {
    if (earth.trackingState != TrackingState.TRACKING ||
        majorPointIndices.size < 2 ||
        currentTargetPointIndex >= majorPointIndices.size
    ) return

    val startIdx = majorPointIndices[currentTargetPointIndex - 1]
    val endIdx = majorPointIndices[currentTargetPointIndex]
    val cameraAlt = earth.cameraGeospatialPose.altitude
    val cameraHeading = earth.cameraGeospatialPose.heading.toFloat()

    for (i in startIdx..endIdx) {
        if (renderedIndices.contains(i)) continue
        renderedIndices.add(i)
        val node = fullRouteNodes[i]
        if (node.type == NodeType.PATH_POINT) continue

        val yOff = if (node.type == NodeType.TURN_POINT) 0.0 else 1.5
        val anchor = earth.createAnchor(node.lat, node.lng, cameraAlt - yOff, 0f, 0f, 0f, 1f) ?: continue
        val anchorNode = AnchorNode(engine = engine, anchor = anchor)
        activeArNodes[i] = anchorNode
        routeNodes.add(anchorNode)

        // 모델 경로 + 회전 결정
        val (modelPath, rotationY, scale) = resolveModelForNode(
            node = node,
            i = i,
            fullRouteNodes = fullRouteNodes,
            majorPointIndices = majorPointIndices,
            turnDirectionMap = turnDirectionMap,
            cameraHeading = cameraHeading,
        )

        if (modelPath != null) {
            coroutineScope.launch {
                val instance = runCatching { modelLoader.loadModelInstance(modelPath) }.getOrNull()
                if (instance != null) {
                    val modelNode = ModelNode(modelInstance = instance).apply {
                        this.scale = scale
                        this.rotation = Float3(0f, rotationY, 0f)
                    }
                    anchorNode.addChildNode(modelNode)
                    activeModelNodes[i] = modelNode
                }
            }
        }
    }
}

/**
 * 노드 타입과 turnType에 따라 (모델 경로, Y축 회전, 스케일)을 계산.
 * turnDirectionMap에 좌/우 정보를 기록.
 * 모델 경로가 null이면 화살표를 그리지 않고 앵커만 추가됨.
 */
private fun resolveModelForNode(
    node: ArRouteNode,
    i: Int,
    fullRouteNodes: List<ArRouteNode>,
    majorPointIndices: List<Int>,
    turnDirectionMap: MutableMap<Int, Boolean>,
    cameraHeading: Float,
): Triple<String?, Float, Float3> {
    val defaultScale = Float3(1f, 1f, 1f)
    val turnScale = Float3(2.5f, 2.5f, 2.5f)

    return when (node.type) {
        NodeType.START, NodeType.END -> Triple("models/map_pointer.glb", cameraHeading, defaultScale)

        NodeType.TURN_POINT -> {
            // 측면 분기 (turnType 17/18) 처리
            if (node.turnType == 17 || node.turnType == 18) {
                val mp = if (node.turnType == 17) "models/left_arrow.glb" else "models/right_arrow.glb"
                val rotation = computeArrowRotation(i, node, fullRouteNodes)
                val mi = majorPointIndices.indexOf(i)
                if (mi != -1) turnDirectionMap[mi] = (node.turnType == 18)
                return Triple(mp, rotation, turnScale)
            }

            // 일반 좌/우/직진 결정
            val isRight: Boolean? = when (node.turnType) {
                13, 19 -> true
                12, 16 -> false
                else -> calcTurnFromBearing(i, node, fullRouteNodes)
            }
            if (isRight == null) {
                Triple(null, 0f, defaultScale)
            } else {
                val mp = if (isRight) "models/right.glb" else "models/left.glb"
                val rotation = computeArrowRotation(i, node, fullRouteNodes)
                val mi = majorPointIndices.indexOf(i)
                if (mi != -1) turnDirectionMap[mi] = isRight
                Triple(mp, rotation, turnScale)
            }
        }

        else -> Triple(null, 0f, defaultScale)
    }
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
