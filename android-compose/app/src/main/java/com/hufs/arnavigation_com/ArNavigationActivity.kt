package com.hufs.arnavigation_com

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.view.Choreographer
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import com.scanpang.app.databinding.ActivityArNavigationBinding
import com.scanpang.arnavigation.data.remote.dto.NavRouteResponse
import com.scanpang.arnavigation.data.remote.dto.TmapRouteResponse
import com.scanpang.arnavigation.data.repository.RouteRepositoryImpl
import com.scanpang.arnavigation.presentation.MainViewModel
import com.scanpang.arnavigation.presentation.MainViewModelFactory
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class NavigationState { INITIALIZING, LOCALIZING, READY_TO_ROUTE }
enum class NodeType { START, END, TURN_POINT, PATH_POINT }
data class ArRouteNode(
    val lat: Double,
    val lng: Double,
    val type: NodeType,
    val turnType: Int = 0,
    val isCalculated: Boolean = false

)

@SuppressLint("SetTextI18n")
class ArNavigationActivity : AppCompatActivity() {

    private lateinit var sceneView: ARSceneView
    private lateinit var binding: ActivityArNavigationBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var statusTextView: TextView
    private lateinit var telemetryTextView: TextView

    private var targetDestination: String = ""
    private var fullRouteNodes: List<ArRouteNode> = emptyList()
    private var renderedIndices: MutableSet<Int> = mutableSetOf()
    private val activeArNodes = mutableMapOf<Int, AnchorNode>()
    private val activeModelNodes = mutableMapOf<Int, ModelNode>()

    private var majorPointIndices = mutableListOf<Int>()
    private var currentTargetPointIndex = 1
    private var lastChunkRenderTime = 0L

    private val distanceResults = FloatArray(2)
    private val bearingResults = FloatArray(2)
    private var latestFrame: com.google.ar.core.Frame? = null
    private var lastAnchorAltitude = Double.MAX_VALUE
    private var altitudeCorrectionJob: kotlinx.coroutines.Job? = null

    private var myLocationMarker: com.google.android.gms.maps.model.Marker? = null
    private val turnDirectionMap = mutableMapOf<Int, Boolean>()
    private lateinit var googleMap: com.google.android.gms.maps.GoogleMap
    private var mapInitialized = false
    private val routeLatLngs = mutableListOf<com.google.android.gms.maps.model.LatLng>()

    // 나침반 관련
    //private var isLookingDown = false
    private var currentDotAngle = 0f
    private var targetDotAngle = 0f
    private var dotAnimator: android.animation.ValueAnimator? = null

    private val frameCallback = com.hufs.arnavigation_com.util.ArFrameCallback {
        updateGeospatialAndChunk()
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) setupArSceneView()
        else { Toast.makeText(this, "권한 필요", Toast.LENGTH_LONG).show(); finish() }
    }

    // ───────────────────────────── Lifecycle ─────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val repository = RouteRepositoryImpl()
        val factory = MainViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setupSearchUI()
        setupOverlayUI()

        sceneView = binding.sceneView
        checkAndRequestPermissions()
        observeViewModel()
    }

    override fun onDestroy() {
        super.onDestroy()
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        activeArNodes.values.forEach { it.destroy() }
    }

    // ───────────────────────────── Setup ─────────────────────────────

    private fun setupSearchUI() {
        binding.searchButton.setOnClickListener {
            val inputText = binding.searchInput.text.toString().trim()
            if (inputText.isNotEmpty()) {
                targetDestination = inputText
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.searchButton.windowToken, 0)
                viewModel.updateState(NavigationState.LOCALIZING)
            }
        }
    }

    private fun setupArSceneView() {
        sceneView.configureSession { session, config ->
            config.geospatialMode = Config.GeospatialMode.ENABLED
            config.focusMode = Config.FocusMode.AUTO
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC))
                config.depthMode = Config.DepthMode.AUTOMATIC
        }
        sceneView.planeRenderer.isEnabled = false

        // ✅ 임시 디버그 — 관련 필드 이름 찾기
        var clazz: Class<*>? = sceneView.javaClass
        while (clazz != null) {
            clazz.declaredFields.forEach { field ->
                val name = field.name.lowercase()
                if (name.contains("point") || name.contains("cloud") ||
                    name.contains("render") || name.contains("visual")) {
                    android.util.Log.d("FieldScan", "${clazz?.simpleName}: ${field.name} (${field.type.simpleName})")
                }
            }
            clazz = clazz.superclass
        }


        val lightEntity = com.google.android.filament.EntityManager.get().create()
        com.google.android.filament.LightManager.Builder(
            com.google.android.filament.LightManager.Type.DIRECTIONAL
        )
            .color(1.0f, 1.0f, 1.0f)
            .intensity(150_000f)
            .direction(0.0f, -1.0f, 0.0f)
            .castShadows(false)
            .build(sceneView.engine, lightEntity)
        sceneView.scene.addEntity(lightEntity)

        sceneView.onSessionUpdated = { _, frame -> latestFrame = frame }
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    private fun setupOverlayUI() {
        statusTextView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(Color.parseColor("#80000000"))
            setPadding(32, 32, 32, 32)
            gravity = Gravity.CENTER
        }
        telemetryTextView = TextView(this).apply {
            setTextColor(Color.GREEN)
            textSize = 14f
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(16, 16, 16, 16)
        }
        binding.uiContainer.addView(
            statusTextView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM; setMargins(0, 0, 0, 100) }
        )
        binding.uiContainer.addView(
            telemetryTextView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.START or Gravity.TOP; setMargins(32, 500, 0, 0) }
        )
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.miniMap) as com.google.android.gms.maps.SupportMapFragment
        mapFragment.getMapAsync { map ->
            googleMap = map
            mapInitialized = true
            googleMap.uiSettings.isZoomControlsEnabled = true
            googleMap.uiSettings.isZoomGesturesEnabled = true
            googleMap.uiSettings.isScrollGesturesEnabled = true
            googleMap.uiSettings.isRotateGesturesEnabled = true
            googleMap.mapType = com.google.android.gms.maps.GoogleMap.MAP_TYPE_NORMAL
        }
        binding.miniMapContainer.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }
    }

    private fun checkAndRequestPermissions() {
        val ps = arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        if (ps.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED })
            setupArSceneView()
        else
            requestPermissionLauncher.launch(ps)
    }

    // ───────────────────────────── ViewModel 관찰 ─────────────────────────────

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigationState.collect { state ->
                    when (state) {
                        NavigationState.INITIALIZING -> updateStatusUI("시스템 초기화 중...")
                        NavigationState.LOCALIZING -> {
                            if (targetDestination.isEmpty()) updateStatusUI("목적지를 입력하세요.")
                            else updateStatusUI("주변을 스캔하세요.")
                        }
                        NavigationState.READY_TO_ROUTE -> updateStatusUI("탐색 시작.")
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.routeData.collect { route ->
                    if (route != null) {
                        lifecycleScope.launch(Dispatchers.Default) {
                            // 백엔드 NavRouteResponse → ArRouteNode 변환
                            val downsampledNodes = if (route is NavRouteResponse) {
                                parseNavResponse(route)
                            } else {
                                parseTmapRoute(route as TmapRouteResponse)
                            }
                            withContext(Dispatchers.Main) {
                                if (downsampledNodes.isNotEmpty()) {
                                    clearAllArNodes()
                                    fullRouteNodes = downsampledNodes
                                    majorPointIndices.clear()
                                    fullRouteNodes.forEachIndexed { index, node ->
                                        if (node.type != NodeType.PATH_POINT)
                                            majorPointIndices.add(index)
                                    }
                                    currentTargetPointIndex = 1
                                    statusTextView.visibility = android.view.View.GONE
                                    if (mapInitialized) drawRouteOnMiniMap()
                                    binding.navCard.visibility = android.view.View.VISIBLE
                                    binding.miniMapContainer.visibility = android.view.View.VISIBLE
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ───────────────────────────── ScanPang 백엔드 응답 파싱 ─────────────────

    private fun parseNavResponse(response: NavRouteResponse): List<ArRouteNode> {
        val arCommand = response.ar_command ?: return emptyList()
        val parsedNodes = mutableListOf<ArRouteNode>()
        val distRes = FloatArray(1)

        // 1) turn_points → TURN_POINT 노드로 변환
        val turnPointSet = mutableSetOf<Pair<Double, Double>>()
        arCommand.turn_points.forEach { tp ->
            turnPointSet.add(Pair(tp.lat, tp.lng))
        }

        // 2) route_line → PATH_POINT 경로 노드 (10m 간격 보간)
        var lastLat = 0.0
        var lastLng = 0.0

        arCommand.route_line.forEachIndexed { idx, point ->
            if (idx == 0) {
                lastLat = point.lat
                lastLng = point.lng
                return@forEachIndexed
            }

            Location.distanceBetween(lastLat, lastLng, point.lat, point.lng, distRes)

            if (distRes[0] >= 10.0f) {
                val segs = (distRes[0] / 10.0f).toInt()
                for (j in 1..segs) {
                    val f = (j * 10.0f) / distRes[0]
                    parsedNodes.add(
                        ArRouteNode(
                            lastLat + (point.lat - lastLat) * f,
                            lastLng + (point.lng - lastLng) * f,
                            NodeType.PATH_POINT
                        )
                    )
                }
            }
            lastLat = point.lat
            lastLng = point.lng
        }

        // 3) turn_points를 route_line 위에 삽입 (가장 가까운 PATH_POINT를 TURN_POINT로 교체)
        arCommand.turn_points.forEach { tp ->
            var bestIdx = -1
            var bestDist = Float.MAX_VALUE
            parsedNodes.forEachIndexed { idx, node ->
                Location.distanceBetween(tp.lat, tp.lng, node.lat, node.lng, distRes)
                if (distRes[0] < bestDist) {
                    bestDist = distRes[0]
                    bestIdx = idx
                }
            }
            if (bestIdx >= 0 && bestDist < 20f) {
                parsedNodes[bestIdx] = ArRouteNode(
                    tp.lat, tp.lng, NodeType.TURN_POINT, tp.turnType, isCalculated = false
                )
            } else {
                // 근접 노드 없으면 추가
                parsedNodes.add(
                    ArRouteNode(tp.lat, tp.lng, NodeType.TURN_POINT, tp.turnType, isCalculated = false)
                )
            }
        }

        // 4) 시작/끝 마킹
        if (parsedNodes.isNotEmpty()) {
            parsedNodes[0] = parsedNodes[0].copy(type = NodeType.START)
            // 목적지 노드 추가
            val dest = arCommand.destination
            parsedNodes.add(ArRouteNode(dest.lat, dest.lng, NodeType.END))
        }

        android.util.Log.d("ScanPang_API", "parseNavResponse: ${parsedNodes.size}개 노드 (TURN=${
            parsedNodes.count { it.type == NodeType.TURN_POINT }
        })")

        return parsedNodes
    }

    // ───────────────────────────── 경로 파싱 (레거시 TMAP 직접) ─────────────

    private fun parseTmapRoute(routeData: TmapRouteResponse): List<ArRouteNode> {
        val parsedNodes = mutableListOf<ArRouteNode>()
        var lastLat = 0.0
        var lastLng = 0.0
        val distRes = FloatArray(1)
        var lastTurnLat: Double? = null
        var lastTurnLng: Double? = null
        var prevBearing: Float? = null  // ✅ 함수 레벨로 이동


        routeData.features.forEach { feature ->
            if (feature.geometry.type == "Point") {
                val coords = feature.geometry.coordinates as? List<*>
                if (coords != null && coords.size >= 2) {
                    val lng = (coords[0] as? Number)?.toDouble() ?: 0.0
                    val lat = (coords[1] as? Number)?.toDouble() ?: 0.0
                    val turnType = feature.properties.turnType ?: 0

                    if (lastTurnLat != null && lastTurnLng != null) {
                        val distArr = FloatArray(1)
                        Location.distanceBetween(lastTurnLat!!, lastTurnLng!!, lat, lng, distArr)
                        if (distArr[0] < 5f) {
                            // ✅ 5m 이내에 직접계산 TURN_POINT가 있으면 T-map 제공으로 교체
                            val lastIdx = parsedNodes.indexOfLast { it.type == NodeType.TURN_POINT }
                            if (lastIdx != -1 && parsedNodes[lastIdx].isCalculated) {
                                parsedNodes[lastIdx] = ArRouteNode(
                                    lat, lng, NodeType.TURN_POINT, turnType, isCalculated = false
                                )
                            }
                            lastTurnLat = lat; lastTurnLng = lng
                            lastLat = lat; lastLng = lng
                            return@forEach
                        }
                    }

                    lastTurnLat = lat
                    lastTurnLng = lng
                    parsedNodes.add(ArRouteNode(lat, lng, NodeType.TURN_POINT, turnType, isCalculated = false))
                    lastLat = lat; lastLng = lng
                }
            } else if (feature.geometry.type == "LineString") {
                prevBearing = null  // ✅ 각 LineString Feature 시작 시 초기화
                (feature.geometry.coordinates as? List<*>)?.forEachIndexed { idx, item ->
                    val coords = item as? List<*>
                    if (coords != null && coords.size >= 2) {
                        val lng = (coords[0] as? Number)?.toDouble() ?: 0.0
                        val lat = (coords[1] as? Number)?.toDouble() ?: 0.0
                        if (lastLat == 0.0 || idx == 0) {  // ✅ idx == 0 추가
                            lastLat = lat; lastLng = lng
                        } else {
                            Location.distanceBetween(lastLat, lastLng, lat, lng, distRes)

                            val curBearingArr = FloatArray(2)
                            Location.distanceBetween(lastLat, lastLng, lat, lng, curBearingArr)
                            val curBearing = curBearingArr[1]

                            if (prevBearing != null) {
                                var angleDiff = curBearing - prevBearing!!
                                while (angleDiff > 180f) angleDiff -= 360f
                                while (angleDiff < -180f) angleDiff += 360f
                                // ✅ 임시 디버그 로그
                                android.util.Log.d("BearingDebug",
                                    "lat: $lastLat, $lastLng | dist: ${distRes[0]} | angleDiff: ${angleDiff.toInt()}")

                                //[직접계산] 45도 이상일시 turn point로 판단
                                if (distRes[0] >= 3f && Math.abs(angleDiff) > 45f) {
                                    if (parsedNodes.isEmpty() ||
                                        parsedNodes.last().lat != lastLat ||
                                        parsedNodes.last().lng != lastLng) {
                                        parsedNodes.add(
                                            ArRouteNode(
                                                lastLat, lastLng,
                                                NodeType.TURN_POINT,
                                                turnType = if (angleDiff > 0) 13 else 12,
                                                isCalculated = true
                                            )
                                        )
                                    }
                                    prevBearing = curBearing  // ✅ 갱신
                                    lastLat = lat; lastLng = lng
                                    return@forEachIndexed
                                }
                            }
                            prevBearing = curBearing  // ✅ 갱신

                            if (distRes[0] >= 10.0f) {
                                val segs = (distRes[0] / 10.0f).toInt()
                                for (j in 1..segs) {
                                    val f = (j * 10.0f) / distRes[0]
                                    parsedNodes.add(
                                        ArRouteNode(
                                            lastLat + (lat - lastLat) * f,
                                            lastLng + (lng - lastLng) * f,
                                            NodeType.PATH_POINT
                                        )
                                    )
                                }
                                lastLat = lat; lastLng = lng
                            }
                        }
                    }
                }
            }
        }

        if (parsedNodes.isNotEmpty()) {
            parsedNodes[0] = parsedNodes[0].copy(type = NodeType.START)
            parsedNodes[parsedNodes.size - 1] = parsedNodes[parsedNodes.size - 1].copy(type = NodeType.END)
        }

        // ✅ 모든 TURN_POINT와 turnType, 좌/우 판별 로그 출력 (10m+ 기반)
        parsedNodes.forEachIndexed { index, node ->
            if (node.type == NodeType.TURN_POINT) {
                val direction = when (node.turnType) {
                    13, 18, 19 -> "우회전 (turnType 기반)"
                    12, 16, 17 -> "좌회전 (turnType 기반)"
                    else -> {
                        if (index > 0 && index < parsedNodes.size - 1) {
                            val distArr = FloatArray(1)
                            var stablePrevIdx = index - 1
                            while (stablePrevIdx > 0) {
                                val candidate = parsedNodes[stablePrevIdx]
                                // TURN_POINT를 만나면 거기서 멈춤
                                if (candidate.type == NodeType.TURN_POINT) break
                                Location.distanceBetween(
                                    candidate.lat, candidate.lng,
                                    node.lat, node.lng, distArr
                                )
                                if (distArr[0] >= 10f) break
                                stablePrevIdx--
                            }
                            val stablePrev = parsedNodes.getOrNull(stablePrevIdx) ?: node

                            var stableNextIdx = index + 1
                            while (stableNextIdx < parsedNodes.size - 1) {
                                val candidate = parsedNodes[stableNextIdx]
                                if (candidate.type == NodeType.TURN_POINT) break  // ✅ 추가
                                Location.distanceBetween(
                                    node.lat, node.lng,
                                    candidate.lat, candidate.lng, distArr
                                )
                                if (distArr[0] >= 10f) break
                                stableNextIdx++
                            }
                            val stableNext = parsedNodes.getOrNull(stableNextIdx) ?: node

                            val result1 = FloatArray(2)
                            val result2 = FloatArray(2)
                            Location.distanceBetween(
                                stablePrev.lat,
                                stablePrev.lng,
                                node.lat,
                                node.lng,
                                result1
                            )
                            Location.distanceBetween(
                                node.lat,
                                node.lng,
                                stableNext.lat,
                                stableNext.lng,
                                result2
                            )
                            var diff = result2[1] - result1[1]
                            while (diff > 180f) diff -= 360f
                            while (diff < -180f) diff += 360f

                            if (Math.abs(diff) < 40f) "직진 (bearing fallback, 각도: ${diff.toInt()}도)"
                            else if (diff >= 0) "우회전 (bearing fallback, 각도: ${diff.toInt()}도)"
                            else "좌회전 (bearing fallback, 각도: ${diff.toInt()}도)"
                        } else "알 수 없음"
                    }
                }
                val source = if (node.isCalculated) "직접계산" else "T-map제공"
                android.util.Log.d(
                    "TurnPoint",
                    "[$source] index: $index | turnType: ${node.turnType} | 판별: $direction | lat: ${node.lat}, ${node.lng}")
            }
        }

        return parsedNodes
    }

    // ───────────────────────────── 매 프레임 업데이트 ─────────────────────────────

    private fun updateGeospatialAndChunk() {
        val earth = sceneView.session?.earth ?: return
        if (earth.trackingState != TrackingState.TRACKING) return

        val pose = earth.cameraGeospatialPose
        val lat = pose.latitude
        val lng = pose.longitude

        runOnUiThread {
            val turnTypeInfo = if (majorPointIndices.isNotEmpty() &&
                currentTargetPointIndex < majorPointIndices.size) {
                val targetNode = fullRouteNodes[majorPointIndices[currentTargetPointIndex]]
                "TurnType: ${targetNode.turnType}"
            } else ""

            telemetryTextView.text =
                "Acc: ${String.format(Locale.getDefault(), "%.1f", pose.horizontalAccuracy)}m\n" +
                        "Lat: ${String.format(Locale.getDefault(), "%.5f", lat)}\n" +
                        "Lng: ${String.format(Locale.getDefault(), "%.5f", lng)}\n" +
                        "Alt: ${String.format(Locale.getDefault(), "%.1f", pose.altitude)}m\n" +
                        turnTypeInfo
        }

        if (viewModel.navigationState.value == NavigationState.LOCALIZING
            && pose.horizontalAccuracy < 10.0
            && targetDestination.isNotEmpty()
        ) {
            viewModel.updateState(NavigationState.READY_TO_ROUTE)
            viewModel.fetchRoute(lng.toString(), lat.toString(), targetDestination)
        }

        if (viewModel.navigationState.value == NavigationState.READY_TO_ROUTE
            && majorPointIndices.isNotEmpty()
        ) {
            // 고도 보정 (애니메이션)
            if (lastAnchorAltitude != Double.MAX_VALUE &&
                kotlin.math.abs(pose.altitude - lastAnchorAltitude) > 1.5
            ) {
                val altitudeDelta = (pose.altitude - lastAnchorAltitude).toFloat()
                lastAnchorAltitude = pose.altitude
                altitudeCorrectionJob?.cancel()
                altitudeCorrectionJob = lifecycleScope.launch {
                    val durationMs = 700L
                    val intervalMs = 16L
                    val steps = durationMs / intervalMs
                    val deltaPerStep = altitudeDelta / steps
                    repeat(steps.toInt()) {
                        activeModelNodes.values.forEach { modelNode ->
                            val current = modelNode.position
                            modelNode.position = Float3(current.x, current.y + deltaPerStep, current.z)
                        }
                        kotlinx.coroutines.delay(intervalMs)
                    }
                }
            }

            if (currentTargetPointIndex < majorPointIndices.size) {
                val targetNode = fullRouteNodes[majorPointIndices[currentTargetPointIndex]]
                Location.distanceBetween(lat, lng, targetNode.lat, targetNode.lng, distanceResults)
                val dist = distanceResults[0]

                if (targetNode.type == NodeType.END && dist <= 6.0f) {
                    clearAllArNodes()
                    updateNavCard("목적지", "0m", isArrival = true)
                    statusTextView.visibility = android.view.View.VISIBLE
                    updateStatusUI("🎉 목적지에 도착했습니다!")
                    binding.navCard.visibility = android.view.View.GONE
                    binding.miniMapContainer.visibility = android.view.View.GONE
                    return
                }

                val statusText = when (targetNode.type) {
                    NodeType.END -> {
                        updateNavCard("목적지", "${dist.toInt()}m", isArrival = false)
                        "목적지까지 ${dist.toInt()}m 남음"
                    }
                    NodeType.TURN_POINT -> {
                        when (turnDirectionMap[currentTargetPointIndex]) {
                            true -> {
                                updateNavCard("우회전", "${dist.toInt()}m", isArrival = false)
                                "우회전까지 ${dist.toInt()}m 남음"
                            }
                            false -> {
                                updateNavCard("좌회전", "${dist.toInt()}m", isArrival = false)
                                "좌회전까지 ${dist.toInt()}m 남음"
                            }
                            null -> {
                                updateNavCard("직진", "${dist.toInt()}m", isArrival = false)
                                "다음 지점까지 ${dist.toInt()}m 남음"
                            }
                        }
                    }
                    else -> {
                        updateNavCard("직진", "${dist.toInt()}m", isArrival = false)
                        "다음 지점까지 ${dist.toInt()}m 남음"
                    }
                }
                updateStatusUI(statusText)

                // ✅ 중복 if (mapInitialized) 블록 제거
                if (mapInitialized) {
                    val currentLatLng = com.google.android.gms.maps.model.LatLng(lat, lng)
                    val heading = pose.heading.toFloat()
                    if (myLocationMarker == null) {
                        myLocationMarker = googleMap.addMarker(
                            com.google.android.gms.maps.model.MarkerOptions()
                                .position(currentLatLng)
                                .icon(createLocationBitmap(heading))
                                .anchor(0.5f, 0.5f)
                                .flat(true)
                        )
                    } else {
                        myLocationMarker?.position = currentLatLng
                        myLocationMarker?.setIcon(createLocationBitmap(heading))
                    }
                    googleMap.animateCamera(
                        com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f)
                    )
                }

                if (dist <= 6.0f) {
                    clearAllArNodes()
                    currentTargetPointIndex++
                }
            }

            val now = System.currentTimeMillis()
            if (now - lastChunkRenderTime > 2000) {
                renderNearbyArrows(lat, lng)
                lastChunkRenderTime = now
            }

            updateDirectionDots(lat, lng, pose.heading)
        }
    }

    // ───────────────────────────── 내비 카드 ─────────────────────────────

    private fun updateNavCard(direction: String, distance: String, isArrival: Boolean) {
        runOnUiThread {
            binding.navDistanceText.text = if (isArrival) "도착!" else distance
            binding.navInstructionText.text = when (direction) {
                "좌회전" -> "좌회전"
                "우회전" -> "우회전"
                "목적지" -> "목적지 근처"
                else -> "직진"
            }
            binding.directionIcon.setImageResource(
                when (direction) {
                    "좌회전" -> android.R.drawable.ic_media_rew
                    "우회전" -> android.R.drawable.ic_media_ff
                    "목적지" -> android.R.drawable.ic_menu_mylocation
                    else -> android.R.drawable.ic_media_play
                }
            )
            binding.directionIcon.rotation = when (direction) {
                "좌회전", "우회전", "목적지" -> 0f
                else -> -90f  // 직진, 횡단보도
            }
        }
    }

    // ───────────────────────────── AR 렌더링 ─────────────────────────────

    private fun renderNearbyArrows(currentLat: Double, currentLng: Double) {
        val earth = sceneView.session?.earth ?: return
        if (earth.trackingState != TrackingState.TRACKING
            || majorPointIndices.size < 2
            || currentTargetPointIndex >= majorPointIndices.size
        ) return

        val startIdx = majorPointIndices[currentTargetPointIndex - 1]
        val endIdx = majorPointIndices[currentTargetPointIndex]

        for (i in startIdx..endIdx) {
            if (renderedIndices.contains(i)) continue
            renderedIndices.add(i)

            val node = fullRouteNodes[i]
            val nextNode = if (i < fullRouteNodes.size - 1) fullRouteNodes[i + 1] else fullRouteNodes[i]
            Location.distanceBetween(node.lat, node.lng, nextNode.lat, nextNode.lng, bearingResults)
            val bearing = bearingResults[1]

            val yOffset = if (node.type == NodeType.TURN_POINT) 0.5 else 1.5
            val anchor = earth.createAnchor(
                node.lat, node.lng,
                earth.cameraGeospatialPose.altitude - yOffset,
                0f, 0f, 0f, 1f
            ) ?: continue

            if (lastAnchorAltitude == Double.MAX_VALUE) {
                lastAnchorAltitude = earth.cameraGeospatialPose.altitude
            }

            val anchorNode = AnchorNode(engine = sceneView.engine, anchor = anchor)

            if (node.type == NodeType.TURN_POINT) {
                // ✅ turnType 기반 좌/우 판별 + fallback
                val isRightTurn: Boolean? = when (node.turnType) {
                    13, 18, 19 -> true
                    12, 16, 17 -> false
                    else -> {
                        // 10m 이상 떨어진 이전 노드 찾기
                        val distArr = FloatArray(1)
                        var stablePrevIdx = i - 1
                        while (stablePrevIdx > 0) {
                            val candidate = fullRouteNodes[stablePrevIdx]
                            if (candidate.type == NodeType.TURN_POINT) break
                            Location.distanceBetween(
                                candidate.lat, candidate.lng,
                                node.lat, node.lng, distArr
                            )
                            if (distArr[0] >= 10f) break
                            stablePrevIdx--
                        }
                        val stablePrev = fullRouteNodes.getOrNull(stablePrevIdx) ?: node

                        // 10m 이상 떨어진 다음 노드 찾기
                        var stableNextIdx = i + 1
                        while (stableNextIdx < fullRouteNodes.size - 1) {
                            val candidate = fullRouteNodes[stableNextIdx]
                            if (candidate.type == NodeType.TURN_POINT) break  // ✅ 추가
                            Location.distanceBetween(node.lat, node.lng, candidate.lat, candidate.lng, distArr)
                            if (distArr[0] >= 10f) break
                            stableNextIdx++
                        }
                        val stableNext = fullRouteNodes.getOrNull(stableNextIdx) ?: node

                        val stableIncoming = bearingBetween(stablePrev, node)
                        val stableOutgoing = bearingBetween(node, stableNext)
                        val turnAngle = calcTurnAngle(stableIncoming, stableOutgoing)

                        // [횡단보도]40도 미만이면 직진으로 판단 → 렌더링 스킵
                        if (Math.abs(turnAngle) < 40f) null
                        else turnAngle >= 0f
                    }
                }

                // ✅ null이면 렌더링 스킵
                if (isRightTurn == null) {
                    sceneView.addChildNode(anchorNode)
                    activeArNodes[i] = anchorNode
                    continue
                }

                val majorIdx = majorPointIndices.indexOf(i)
                if (majorIdx != -1) turnDirectionMap[majorIdx] = isRightTurn

                val modelPath = if (isRightTurn) "models/right2.glb" else "models/left.glb"

                // ✅ 진입 방향 계산 — 뒤로 돌아보며 첫 번째 PATH_POINT 찾기
                var lookBackIdx = i - 1
                while (lookBackIdx > 0 &&
                    fullRouteNodes[lookBackIdx].type == NodeType.TURN_POINT) {
                    lookBackIdx--
                }
                val lookBackNode = fullRouteNodes.getOrNull(lookBackIdx) ?: node
                val incomingResult = FloatArray(2)
                Location.distanceBetween(
                    lookBackNode.lat, lookBackNode.lng,
                    node.lat, node.lng,
                    incomingResult
                )
                val stableIncomingBearing = incomingResult[1]

                lifecycleScope.launch {
                    val instance = sceneView.modelLoader.loadModelInstance(modelPath)
                    if (instance != null) {
                        val arrowNode = ModelNode(instance).apply {
                            scale = Float3(0.5f, 0.5f, 0.5f)
                            rotation = Float3(0f, stableIncomingBearing + 180f, 0f)
                        }
                        anchorNode.addChildNode(arrowNode)
                        activeModelNodes[i] = arrowNode
                    }
                    sceneView.addChildNode(anchorNode)
                    activeArNodes[i] = anchorNode
                }

            }
            // ✅ PATH_POINT는 렌더링 스킵
            else {
                if (node.type == NodeType.PATH_POINT) continue  // ← 이 한 줄 추가

                val path = when (node.type) {
                    NodeType.START, NodeType.END -> "models/map_pointer.glb"
                    else -> "models/arrow2.glb"
                }
                lifecycleScope.launch {
                    val instance = sceneView.modelLoader.loadModelInstance(path)
                    if (instance != null) {
                        val mNode = ModelNode(instance).apply {
                            scale = Float3(1.0f, 1.0f, 1.0f)
                            rotation = Float3(0f, bearing - 90f, 0f)
                        }
            /* Parth Arrow 렌더링 스킵
            else {
                // ✅ PATH_POINT가 주요 지점 15m 이내면 렌더링 스킵
                if (node.type == NodeType.PATH_POINT) {
                    val distResult = FloatArray(1)
                    val tooClose = majorPointIndices.any { majorIdx ->
                        val majorNode = fullRouteNodes[majorIdx]
                        if (majorNode.type == NodeType.TURN_POINT ||
                            majorNode.type == NodeType.START ||
                            majorNode.type == NodeType.END
                        ) {
                            Location.distanceBetween(
                                node.lat, node.lng,
                                majorNode.lat, majorNode.lng,
                                distResult
                            )
                            distResult[0] < 15f
                        } else false
                    }
                    if (tooClose) continue
                }

                val path = when (node.type) {
                    NodeType.START, NodeType.END -> "models/map_pointer.glb"
                    else -> "models/path_arrow.glb"
                }
                lifecycleScope.launch {
                    val instance = sceneView.modelLoader.loadModelInstance(path)
                    if (instance != null) {
                        val mNode = ModelNode(instance).apply {
                            val s = if (node.type == NodeType.PATH_POINT) 0.15f else 1.0f
                            scale = Float3(s, s, s)
                            rotation = Float3(0f, bearing - 90f, 0f)
                        } */
                        anchorNode.addChildNode(mNode)
                        activeModelNodes[i] = mNode
                        sceneView.addChildNode(anchorNode)
                        activeArNodes[i] = anchorNode
                    }
                }
            }
        }
    }

    // ───────────────────────────── 나침반/점선 ─────────────────────────────

    private fun updateDirectionDots(lat: Double, lng: Double, heading: Double) {
        val frame = latestFrame ?: return
        val camera = frame.camera

        /* Head 내리기 감지
        val poseMatrix = FloatArray(16)
        camera.pose.toMatrix(poseMatrix, 0)
        val cameraForwardY = -poseMatrix[9]
        val lookingDown = cameraForwardY < -0.5f

        if (!lookingDown) {
            runOnUiThread { binding.compassView.visibility = android.view.View.GONE }
            isLookingDown = false
            dotAnimator?.cancel()
            return
        }
        isLookingDown = true
        */
        if (fullRouteNodes.isEmpty() || currentTargetPointIndex >= majorPointIndices.size) return

        val targetNode = fullRouteNodes[majorPointIndices[currentTargetPointIndex]]
        val bearingToTarget = FloatArray(2)
        Location.distanceBetween(lat, lng, targetNode.lat, targetNode.lng, bearingToTarget)
        val targetBearing = bearingToTarget[1]

        var newAngle = targetBearing - heading.toFloat()
        while (newAngle > 180f) newAngle -= 360f
        while (newAngle < -180f) newAngle += 360f

        if (kotlin.math.abs(newAngle - targetDotAngle) > 2f) {
            targetDotAngle = newAngle
            dotAnimator?.cancel()
            dotAnimator = android.animation.ValueAnimator.ofFloat(currentDotAngle, targetDotAngle).apply {
                duration = 300L
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { animator ->
                    currentDotAngle = animator.animatedValue as Float
                    runOnUiThread {
                        binding.compassView.visibility = android.view.View.VISIBLE
                        binding.compassView.angleDiff = currentDotAngle
                    }
                }
                start()
            }
        } else {
            runOnUiThread {
                binding.compassView.visibility = android.view.View.VISIBLE
                binding.compassView.angleDiff = currentDotAngle
            }
        }
    }

    // ───────────────────────────── 미니맵 ─────────────────────────────

    private fun drawRouteOnMiniMap() {
        googleMap.clear()
        routeLatLngs.clear()
        myLocationMarker = null

        fullRouteNodes.forEach { node ->
            routeLatLngs.add(com.google.android.gms.maps.model.LatLng(node.lat, node.lng))
        }
        googleMap.addPolyline(
            com.google.android.gms.maps.model.PolylineOptions()
                .addAll(routeLatLngs)
                .width(8f)
                .color(android.graphics.Color.parseColor("#4285F4"))
        )
        fullRouteNodes.firstOrNull()?.let {
            googleMap.addMarker(
                com.google.android.gms.maps.model.MarkerOptions()
                    .position(com.google.android.gms.maps.model.LatLng(it.lat, it.lng))
                    .title("출발")
            )
        }
        fullRouteNodes.lastOrNull()?.let {
            googleMap.addMarker(
                com.google.android.gms.maps.model.MarkerOptions()
                    .position(com.google.android.gms.maps.model.LatLng(it.lat, it.lng))
                    .title("목적지")
            )
        }
        val earth = sceneView.session?.earth
        if (earth != null && earth.trackingState == TrackingState.TRACKING) {
            val pose = earth.cameraGeospatialPose
            val currentLatLng = com.google.android.gms.maps.model.LatLng(pose.latitude, pose.longitude)
            googleMap.moveCamera(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(currentLatLng, 17f)
            )
        }
    }

    private fun createLocationBitmap(bearing: Float): com.google.android.gms.maps.model.BitmapDescriptor {
        val size = 120
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#CC0000")
        }
        val darkPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#8B0000")
        }
        canvas.rotate(bearing, size / 2f, size / 2f)
        val leftPath = android.graphics.Path().apply {
            moveTo(size / 2f, 10f)
            lineTo(size / 2f - 30f, size - 20f)
            lineTo(size / 2f, size - 40f)
            close()
        }
        canvas.drawPath(leftPath, darkPaint)
        val rightPath = android.graphics.Path().apply {
            moveTo(size / 2f, 10f)
            lineTo(size / 2f + 30f, size - 20f)
            lineTo(size / 2f, size - 40f)
            close()
        }
        canvas.drawPath(rightPath, paint)
        return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // ───────────────────────────── 유틸 ─────────────────────────────

    private fun bearingBetween(from: ArRouteNode, to: ArRouteNode): Float {
        val result = FloatArray(2)
        Location.distanceBetween(from.lat, from.lng, to.lat, to.lng, result)
        return result[1]
    }

    private fun calcTurnAngle(incoming: Float, outgoing: Float): Float {
        var diff = outgoing - incoming
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        return diff
    }

    private fun clearAllArNodes() {
        altitudeCorrectionJob?.cancel()
        altitudeCorrectionJob = null
        dotAnimator?.cancel()
        dotAnimator = null
        currentDotAngle = 0f
        targetDotAngle = 0f
        activeArNodes.values.forEach { it.destroy() }
        activeArNodes.clear()
        activeModelNodes.clear()
        renderedIndices.clear()
        turnDirectionMap.clear()
        lastAnchorAltitude = Double.MAX_VALUE
        runOnUiThread {
            binding.compassView.visibility = android.view.View.GONE
        }
    }

    private fun updateStatusUI(message: String) {
        runOnUiThread { statusTextView.text = message }
    }
}