package com.scanpang.app.screens.ar

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.ar.core.Config
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.scanpang.app.components.ar.ArInWorldBadgeContent
import com.scanpang.app.components.ar.BadgeDirection
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.ViewNode2
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberViewNodeManager
import kotlinx.coroutines.launch

private val MODELS = listOf(
    "straight" to "models/straight.glb",
    "left"     to "models/left.glb",
    "right"    to "models/right.glb",
    "left_arrow"  to "models/left_arrow.glb",
    "right_arrow" to "models/right_arrow.glb",
    "map_pointer" to "models/map_pointer.glb",
)

private enum class RenderMode { GLB, COMPOSE }

/**
 * AR 모델 디버그 미리보기 화면.
 *
 * Geospatial 없이 카메라 정면 N미터 지점에 anchor를 박아 모델/배지를 띄움.
 * 실내에서 슬라이더로 스케일·회전·거리를 즉시 조절하며 시각 검증.
 *
 * - GLB 모드: 기존 .glb 모델 (left.glb, right.glb, straight.glb, map_pointer.glb)
 * - Compose 모드: Figma 스타일 원형 배지 (ViewNode2 + Compose, Path A)
 */
@Composable
fun ArDebugPreviewScreen(navController: NavController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val viewNodeManager = rememberViewNodeManager()
    val coroutineScope = rememberCoroutineScope()

    // 카메라 권한
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { permissionGranted = it },
    )
    LaunchedEffect(Unit) {
        if (!permissionGranted) launcher.launch(Manifest.permission.CAMERA)
    }
    if (!permissionGranted) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black))
        return
    }

    // 공통 슬라이더
    var scale by remember { mutableStateOf(2.0f) }
    var pitchDeg by remember { mutableStateOf(0f) }
    var yawDeg by remember { mutableStateOf(0f) }
    var distanceM by remember { mutableStateOf(3f) }

    // 모드
    var renderMode by remember { mutableStateOf(RenderMode.COMPOSE) }

    // GLB 모드 선택
    var selectedModelKey by remember { mutableStateOf("straight") }

    // Compose 모드 선택
    var selectedDirection by remember { mutableStateOf(BadgeDirection.RIGHT) }
    // ViewNode2 한 번 만들면 content composable이 selectedDirection을 구독해 자동 recompose

    // 현재 노드 핸들
    val routeNodes = remember { mutableStateListOf<Node>() }
    var currentAnchorNode by remember { mutableStateOf<AnchorNode?>(null) }
    var currentModelNode by remember { mutableStateOf<ModelNode?>(null) }
    var currentViewNode by remember { mutableStateOf<ViewNode2?>(null) }

    // 재배치 트리거
    var placeRequest by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { placeRequest = 1 }

    // 툴바 접기/펴기 상태
    var toolbarExpanded by remember { mutableStateOf(true) }

    val selectedModelPath = MODELS.firstOrNull { it.first == selectedModelKey }?.second ?: MODELS[0].second

    // 슬라이더 값 변경 시 현재 노드 즉시 갱신.
    // Z(roll)에 +90° baseline — 아이콘이 자연스러운 좌/우 방향이 되도록 회전 보정.
    LaunchedEffect(scale, pitchDeg, yawDeg) {
        currentModelNode?.let { node ->
            node.scale = Float3(scale, scale, scale)
            node.rotation = Float3(pitchDeg, yawDeg, 90f)
        }
        currentViewNode?.let { node ->
            node.scale = Float3(scale, scale, scale)
            node.rotation = Float3(pitchDeg, yawDeg, 90f)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            routeNodes.clear()
            currentAnchorNode = null
            currentModelNode = null
            currentViewNode = null
        }
    }

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
                config.focusMode = Config.FocusMode.AUTO
                config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                config.depthMode = Config.DepthMode.DISABLED
            },
            onSessionUpdated = { session, frame ->
                if (frame.camera.trackingState != TrackingState.TRACKING) return@ARScene
                if (placeRequest == 0) return@ARScene

                val cameraPose = frame.camera.pose
                val forwardPose = Pose.makeTranslation(0f, 0f, -distanceM)
                val targetPose = cameraPose.compose(forwardPose)
                val anchor = runCatching { session.createAnchor(targetPose) }.getOrNull()
                    ?: return@ARScene

                // 기존 노드 제거
                currentAnchorNode?.let { runCatching { it.destroy() } }
                routeNodes.clear()
                currentModelNode = null
                currentViewNode = null

                val anchorNode = AnchorNode(engine = engine, anchor = anchor)
                routeNodes.add(anchorNode)
                currentAnchorNode = anchorNode
                placeRequest = 0

                val currentScale = scale
                val currentPitch = pitchDeg
                val currentYaw = yawDeg

                when (renderMode) {
                    RenderMode.GLB -> {
                        val modelPath = selectedModelPath
                        coroutineScope.launch {
                            val instance = runCatching { modelLoader.loadModelInstance(modelPath) }.getOrNull()
                                ?: return@launch
                            val modelNode = ModelNode(modelInstance = instance).apply {
                                this.scale = Float3(currentScale, currentScale, currentScale)
                                this.rotation = Float3(currentPitch, currentYaw, 90f)
                            }
                            runCatching {
                                anchorNode.addChildNode(modelNode)
                                currentModelNode = modelNode
                            }
                        }
                    }
                    RenderMode.COMPOSE -> {
                        // ViewNode2: Compose UI를 3D 평면 빌보드로 렌더 (Path A)
                        // unlit=true — light estimation 꺼져있으므로 조명 곱셈 없이 색상 그대로
                        runCatching {
                            val viewNode = ViewNode2(
                                engine = engine,
                                windowManager = viewNodeManager,
                                materialLoader = materialLoader,
                                unlit = true,
                            ) {
                                // 이 컴포저블은 ViewNode2 내부 composition에서 실행됨.
                                // selectedDirection State를 읽으면 자동 recompose.
                                MaterialTheme {
                                    ArInWorldBadgeContent(direction = selectedDirection)
                                }
                            }.apply {
                                this.scale = Float3(currentScale, currentScale, currentScale)
                                this.rotation = Float3(currentPitch, currentYaw, 90f)
                                // pxPerUnits 기본 250 — 120dp 배지가 약 1.4m 폭. scale 슬라이더로 조절.
                            }
                            anchorNode.addChildNode(viewNode)
                            currentViewNode = viewNode
                        }
                    }
                }
            },
        )

        // ── 상단 상태 ──
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.55f),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text("AR 디버그 미리보기", color = Color.White)
                val label = when (renderMode) {
                    RenderMode.GLB -> "GLB | 모델: $selectedModelKey"
                    RenderMode.COMPOSE -> "Compose | 방향: ${selectedDirection.name}"
                }
                Text("$label  |  거리: ${"%.1f".format(distanceM)}m", color = Color.White)
            }
        }

        // ── 하단 컨트롤 ──
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = Color.Black.copy(alpha = 0.7f),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // 접기/펴기 토글 — 항상 보이는 헤더
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (toolbarExpanded) "컨트롤 접기" else "컨트롤 펼치기",
                        color = Color.White,
                        modifier = Modifier
                            .weight(1f),
                    )
                    androidx.compose.material3.IconButton(
                        onClick = { toolbarExpanded = !toolbarExpanded },
                    ) {
                        Icon(
                            imageVector = if (toolbarExpanded)
                                Icons.Rounded.KeyboardArrowDown
                            else
                                Icons.Rounded.KeyboardArrowUp,
                            contentDescription = if (toolbarExpanded) "접기" else "펼치기",
                            tint = Color.White,
                        )
                    }
                }

                if (!toolbarExpanded) {
                    // 접힌 상태: 닫기 버튼만 가볍게 노출
                    Button(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("닫기")
                    }
                } else {

                // 모드 토글
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    FilterChip(
                        selected = renderMode == RenderMode.GLB,
                        onClick = {
                            renderMode = RenderMode.GLB
                            placeRequest = 1
                        },
                        label = { Text("3D GLB") },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    FilterChip(
                        selected = renderMode == RenderMode.COMPOSE,
                        onClick = {
                            renderMode = RenderMode.COMPOSE
                            placeRequest = 1
                        },
                        label = { Text("Compose 배지") },
                    )
                }

                when (renderMode) {
                    RenderMode.GLB -> {
                        Text("모델 선택", color = Color.White)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                        ) {
                            MODELS.forEach { (key, _) ->
                                FilterChip(
                                    selected = selectedModelKey == key,
                                    onClick = {
                                        selectedModelKey = key
                                        placeRequest = 1
                                    },
                                    label = { Text(key) },
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            }
                        }
                    }
                    RenderMode.COMPOSE -> {
                        Text("방향", color = Color.White)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                        ) {
                            BadgeDirection.entries.forEach { dir ->
                                FilterChip(
                                    selected = selectedDirection == dir,
                                    onClick = { selectedDirection = dir },  // ViewNode2 안의 Compose가 자동 recompose
                                    label = { Text(dir.name) },
                                    modifier = Modifier.padding(end = 6.dp),
                                )
                            }
                        }
                    }
                }

                LabeledSlider("Scale: ${"%.2f".format(scale)}", scale, 0.3f..5f) { scale = it }
                LabeledSlider("Pitch: ${"%.0f".format(pitchDeg)}°", pitchDeg, -90f..90f) { pitchDeg = it }
                LabeledSlider("Yaw: ${"%.0f".format(yawDeg)}°", yawDeg, -180f..180f) { yawDeg = it }
                LabeledSlider("Distance: ${"%.1f".format(distanceM)}m", distanceM, 1f..10f) { distanceM = it }

                Button(
                    onClick = { placeRequest = 1 },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text("현재 카메라 방향에 재배치")
                }
                Button(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text("닫기")
                }
                }  // else (toolbarExpanded) 종료
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(label, color = Color.White)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
        )
    }
}
