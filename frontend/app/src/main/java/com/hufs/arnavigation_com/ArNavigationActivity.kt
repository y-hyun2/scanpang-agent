package com.hufs.arnavigation_com

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
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
import com.scanpang.app.R
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
data class ArRouteNode(val lat: Double, val lng: Double, val type: NodeType, val turnType: Int = 0, val isCalculated: Boolean = false)

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

    private var isLookingDown = false
    private var currentDotAngle = 0f
    private var targetDotAngle = 0f
    private var dotAnimator: android.animation.ValueAnimator? = null
    private var lastTelemetryUpdateTime = 0L

    private val frameCallback = com.hufs.arnavigation_com.util.ArFrameCallback(
        Runnable { updateGeospatialAndChunk() }
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) setupArSceneView()
        else { Toast.makeText(this, "권한 필요", Toast.LENGTH_LONG).show(); finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityArNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val repository = RouteRepositoryImpl()
        viewModel = ViewModelProvider(this, MainViewModelFactory(repository))[MainViewModel::class.java]
        setupSearchUI(); setupOverlayUI()
        sceneView = binding.sceneView
        checkAndRequestPermissions(); observeViewModel()

        // Intent에서 목적지 이름 받기
        intent.getStringExtra("DESTINATION_NAME")?.let { destName ->
            if (destName.isNotEmpty()) {
                targetDestination = destName
                binding.searchInput.setText(destName)
                viewModel.updateState(NavigationState.LOCALIZING)
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); frameCallback.stop(); activeArNodes.values.forEach { it.destroy() } }

    private fun setupSearchUI() {
        binding.searchButton.setOnClickListener {
            val inputText = binding.searchInput.text.toString().trim()
            if (inputText.isNotEmpty()) {
                targetDestination = inputText
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(binding.searchButton.windowToken, 0)
                viewModel.updateState(NavigationState.LOCALIZING)
            }
        }
    }

    private fun setupArSceneView() {
        sceneView.configureSession { _, config ->
            config.geospatialMode = Config.GeospatialMode.ENABLED
            config.focusMode = Config.FocusMode.AUTO
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            config.depthMode = Config.DepthMode.DISABLED
        }
        sceneView.planeRenderer.isEnabled = false
        val lightEntity = com.google.android.filament.EntityManager.get().create()
        com.google.android.filament.LightManager.Builder(com.google.android.filament.LightManager.Type.DIRECTIONAL)
            .color(1.0f, 1.0f, 1.0f).intensity(150_000f).direction(0.0f, -1.0f, 0.0f).castShadows(false)
            .build(sceneView.engine, lightEntity)
        sceneView.scene.addEntity(lightEntity)
        sceneView.onSessionUpdated = { _, frame -> latestFrame = frame }
        frameCallback.start()
    }

    private fun setupOverlayUI() {
        statusTextView = TextView(this).apply { setTextColor(Color.WHITE); textSize = 18f; setBackgroundColor(Color.parseColor("#80000000")); setPadding(32, 32, 32, 32); gravity = Gravity.CENTER }
        telemetryTextView = TextView(this).apply { setTextColor(Color.GREEN); textSize = 14f; setBackgroundColor(Color.parseColor("#99000000")); setPadding(16, 16, 16, 16) }
        binding.uiContainer.addView(statusTextView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM; setMargins(0, 0, 0, 100) })
        binding.uiContainer.addView(telemetryTextView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.START or Gravity.TOP; setMargins(32, 500, 0, 0) })
        (supportFragmentManager.findFragmentById(R.id.miniMap) as com.google.android.gms.maps.SupportMapFragment).getMapAsync { map ->
            googleMap = map; mapInitialized = true
            googleMap.uiSettings.apply { isZoomControlsEnabled = true; isZoomGesturesEnabled = true; isScrollGesturesEnabled = true; isRotateGesturesEnabled = true }
            googleMap.mapType = com.google.android.gms.maps.GoogleMap.MAP_TYPE_NORMAL
        }
        binding.miniMapContainer.setOnTouchListener { v, _ -> v.parent.requestDisallowInterceptTouchEvent(true); false }
    }

    private fun checkAndRequestPermissions() {
        val ps = arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
        if (ps.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) setupArSceneView()
        else requestPermissionLauncher.launch(ps)
    }

    private fun observeViewModel() {
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.navigationState.collect { state -> when (state) {
                NavigationState.INITIALIZING -> updateStatusUI("시스템 초기화 중...")
                NavigationState.LOCALIZING -> updateStatusUI(if (targetDestination.isEmpty()) "목적지를 입력하세요." else "주변을 스캔하세요.")
                NavigationState.READY_TO_ROUTE -> updateStatusUI("탐색 시작.")
            }}
        }}
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.routeData.collect { route -> if (route != null) {
                lifecycleScope.launch(Dispatchers.Default) {
                    val nodes = if (route is NavRouteResponse) parseNavResponse(route) else parseTmapRoute(route as TmapRouteResponse)
                    withContext(Dispatchers.Main) { if (nodes.isNotEmpty()) {
                        clearAllArNodes(); fullRouteNodes = nodes; majorPointIndices.clear()
                        fullRouteNodes.forEachIndexed { i, n -> if (n.type != NodeType.PATH_POINT) majorPointIndices.add(i) }
                        currentTargetPointIndex = 1; statusTextView.visibility = android.view.View.GONE
                        if (mapInitialized) drawRouteOnMiniMap()
                        binding.navCard.visibility = android.view.View.VISIBLE; binding.miniMapContainer.visibility = android.view.View.VISIBLE
                    }}
                }
            }}
        }}
    }

    // ───── ScanPang 백엔드 응답 파싱 ─────
    private fun parseNavResponse(response: NavRouteResponse): List<ArRouteNode> {
        val arCommand = response.ar_command ?: return emptyList()
        val parsedNodes = mutableListOf<ArRouteNode>(); val distRes = FloatArray(1)
        var lastLat = 0.0; var lastLng = 0.0
        arCommand.route_line.forEachIndexed { idx, point ->
            if (idx == 0) { lastLat = point.lat; lastLng = point.lng; return@forEachIndexed }
            Location.distanceBetween(lastLat, lastLng, point.lat, point.lng, distRes)
            if (distRes[0] >= 10.0f) { val segs = (distRes[0] / 10.0f).toInt(); for (j in 1..segs) { val f = (j * 10.0f) / distRes[0]; parsedNodes.add(ArRouteNode(lastLat + (point.lat - lastLat) * f, lastLng + (point.lng - lastLng) * f, NodeType.PATH_POINT)) } }
            lastLat = point.lat; lastLng = point.lng
        }
        arCommand.turn_points.forEach { tp ->
            var bestIdx = -1; var bestDist = Float.MAX_VALUE
            parsedNodes.forEachIndexed { idx, node -> Location.distanceBetween(tp.lat, tp.lng, node.lat, node.lng, distRes); if (distRes[0] < bestDist) { bestDist = distRes[0]; bestIdx = idx } }
            if (bestIdx >= 0 && bestDist < 20f) parsedNodes[bestIdx] = ArRouteNode(tp.lat, tp.lng, NodeType.TURN_POINT, tp.turnType, isCalculated = false)
            else parsedNodes.add(ArRouteNode(tp.lat, tp.lng, NodeType.TURN_POINT, tp.turnType, isCalculated = false))
        }
        if (parsedNodes.isNotEmpty()) { parsedNodes[0] = parsedNodes[0].copy(type = NodeType.START); parsedNodes.add(ArRouteNode(arCommand.destination.lat, arCommand.destination.lng, NodeType.END)) }
        return parsedNodes
    }

    // ───── 경로 파싱 (TMAP) ─────
    private fun parseTmapRoute(routeData: TmapRouteResponse): List<ArRouteNode> {
        val parsedNodes = mutableListOf<ArRouteNode>(); var lastLat = 0.0; var lastLng = 0.0; val distRes = FloatArray(1)
        var lastTurnLat: Double? = null; var lastTurnLng: Double? = null; var prevBearing: Float? = null
        routeData.features.forEach { feature ->
            if (feature.geometry.type == "Point") {
                val coords = feature.geometry.coordinates as? List<*>
                if (coords != null && coords.size >= 2) {
                    val lng = (coords[0] as? Number)?.toDouble() ?: 0.0; val lat = (coords[1] as? Number)?.toDouble() ?: 0.0; val turnType = feature.properties.turnType ?: 0
                    if (lastTurnLat != null) { val d = FloatArray(1); Location.distanceBetween(lastTurnLat!!, lastTurnLng!!, lat, lng, d)
                        if (d[0] < 5f) { val li = parsedNodes.indexOfLast { it.type == NodeType.TURN_POINT }; if (li != -1 && parsedNodes[li].isCalculated) parsedNodes[li] = ArRouteNode(lat, lng, NodeType.TURN_POINT, turnType, false); lastTurnLat = lat; lastTurnLng = lng; lastLat = lat; lastLng = lng; return@forEach } }
                    lastTurnLat = lat; lastTurnLng = lng; parsedNodes.add(ArRouteNode(lat, lng, NodeType.TURN_POINT, turnType, false)); lastLat = lat; lastLng = lng
                }
            } else if (feature.geometry.type == "LineString") {
                prevBearing = null
                (feature.geometry.coordinates as? List<*>)?.forEachIndexed { idx, item -> val coords = item as? List<*>
                    if (coords != null && coords.size >= 2) { val lng = (coords[0] as? Number)?.toDouble() ?: 0.0; val lat = (coords[1] as? Number)?.toDouble() ?: 0.0
                        if (lastLat == 0.0 || idx == 0) { lastLat = lat; lastLng = lng } else {
                            Location.distanceBetween(lastLat, lastLng, lat, lng, distRes); val cb = FloatArray(2); Location.distanceBetween(lastLat, lastLng, lat, lng, cb); val curBearing = cb[1]
                            if (prevBearing != null) { var ad = curBearing - prevBearing!!; while (ad > 180f) ad -= 360f; while (ad < -180f) ad += 360f
                                if (distRes[0] >= 3f && Math.abs(ad) > 45f) { if (parsedNodes.isEmpty() || parsedNodes.last().lat != lastLat || parsedNodes.last().lng != lastLng) parsedNodes.add(ArRouteNode(lastLat, lastLng, NodeType.TURN_POINT, if (ad > 0) 13 else 12, true)); prevBearing = curBearing; lastLat = lat; lastLng = lng; return@forEachIndexed } }
                            prevBearing = curBearing
                            if (distRes[0] >= 10.0f) { val segs = (distRes[0] / 10.0f).toInt(); for (j in 1..segs) { val f = (j * 10.0f) / distRes[0]; parsedNodes.add(ArRouteNode(lastLat + (lat - lastLat) * f, lastLng + (lng - lastLng) * f, NodeType.PATH_POINT)) }; lastLat = lat; lastLng = lng }
                        }
                    }
                }
            }
        }
        if (parsedNodes.isNotEmpty()) { parsedNodes[0] = parsedNodes[0].copy(type = NodeType.START); parsedNodes[parsedNodes.size - 1] = parsedNodes[parsedNodes.size - 1].copy(type = NodeType.END) }
        parsedNodes.forEachIndexed { index, node -> if (node.type == NodeType.TURN_POINT) {
            val direction = when (node.turnType) { 13, 19 -> "우회전"; 12, 16 -> "좌회전"; 17 -> "좌측"; 18 -> "우측"
                else -> { if (index > 0 && index < parsedNodes.size - 1) { val d = FloatArray(1)
                    var pi = index - 1; while (pi > 0) { val c = parsedNodes[pi]; if (c.type == NodeType.TURN_POINT) break; Location.distanceBetween(c.lat, c.lng, node.lat, node.lng, d); if (d[0] >= 10f) break; pi-- }
                    var ni = index + 1; while (ni < parsedNodes.size - 1) { val c = parsedNodes[ni]; if (c.type == NodeType.TURN_POINT) break; Location.distanceBetween(node.lat, node.lng, c.lat, c.lng, d); if (d[0] >= 10f) break; ni++ }
                    val r1 = FloatArray(2); val r2 = FloatArray(2); Location.distanceBetween((parsedNodes.getOrNull(pi) ?: node).lat, (parsedNodes.getOrNull(pi) ?: node).lng, node.lat, node.lng, r1); Location.distanceBetween(node.lat, node.lng, (parsedNodes.getOrNull(ni) ?: node).lat, (parsedNodes.getOrNull(ni) ?: node).lng, r2)
                    var diff = r2[1] - r1[1]; while (diff > 180f) diff -= 360f; while (diff < -180f) diff += 360f
                    if (Math.abs(diff) < 40f) "직진" else if (diff >= 0) "우회전" else "좌회전" } else "알 수 없음" } }
            android.util.Log.d("TurnPoint", "[${if (node.isCalculated) "직접계산" else "T-map제공"}] $index | turnType:${node.turnType} | $direction")
        }}
        return parsedNodes
    }

    // ───── 매 프레임 업데이트 ─────
    private fun updateGeospatialAndChunk() {
        val earth = sceneView.session?.earth ?: return; if (earth.trackingState != TrackingState.TRACKING) return
        val pose = earth.cameraGeospatialPose; val lat = pose.latitude; val lng = pose.longitude; val now = System.currentTimeMillis()
        if (now - lastTelemetryUpdateTime > 500) { lastTelemetryUpdateTime = now; runOnUiThread {
            val ti = if (majorPointIndices.isNotEmpty() && currentTargetPointIndex < majorPointIndices.size) "TurnType: ${fullRouteNodes[majorPointIndices[currentTargetPointIndex]].turnType}" else ""
            telemetryTextView.text = "Acc: ${"%.1f".format(pose.horizontalAccuracy)}m\nLat: ${"%.5f".format(lat)}\nLng: ${"%.5f".format(lng)}\nAlt: ${"%.1f".format(pose.altitude)}m\n$ti"
        }}
        if (viewModel.navigationState.value == NavigationState.LOCALIZING && pose.horizontalAccuracy < 10.0 && targetDestination.isNotEmpty()) { viewModel.updateState(NavigationState.READY_TO_ROUTE); viewModel.fetchRoute(lng.toString(), lat.toString(), targetDestination) }
        if (viewModel.navigationState.value == NavigationState.READY_TO_ROUTE && majorPointIndices.isNotEmpty()) {
            if (lastAnchorAltitude != Double.MAX_VALUE && kotlin.math.abs(pose.altitude - lastAnchorAltitude) > 1.5) {
                val d = (pose.altitude - lastAnchorAltitude).toFloat(); lastAnchorAltitude = pose.altitude; altitudeCorrectionJob?.cancel()
                altitudeCorrectionJob = lifecycleScope.launch { val steps = 700L / 16L; val dps = d / steps; repeat(steps.toInt()) { activeModelNodes.values.forEach { it.position = Float3(it.position.x, it.position.y + dps, it.position.z) }; kotlinx.coroutines.delay(16L) } }
            }
            if (currentTargetPointIndex < majorPointIndices.size) {
                val tn = fullRouteNodes[majorPointIndices[currentTargetPointIndex]]; Location.distanceBetween(lat, lng, tn.lat, tn.lng, distanceResults); val dist = distanceResults[0]
                if (tn.type == NodeType.END && dist <= 6.0f) { clearAllArNodes(); updateNavCard("목적지", "0m", true); statusTextView.visibility = android.view.View.VISIBLE; updateStatusUI("🎉 목적지에 도착했습니다!"); binding.navCard.visibility = android.view.View.GONE; binding.miniMapContainer.visibility = android.view.View.GONE; return }
                val st = when (tn.type) {
                    NodeType.END -> { updateNavCard("목적지", "${dist.toInt()}m", false); "목적지까지 ${dist.toInt()}m" }
                    NodeType.TURN_POINT -> when (turnDirectionMap[currentTargetPointIndex]) {
                        true -> { val l = if (tn.turnType == 18) "우측" else "우회전"; updateNavCard(l, "${dist.toInt()}m", false); "${l}까지 ${dist.toInt()}m" }
                        false -> { val l = if (tn.turnType == 17) "좌측" else "좌회전"; updateNavCard(l, "${dist.toInt()}m", false); "${l}까지 ${dist.toInt()}m" }
                        null -> { updateNavCard("직진", "${dist.toInt()}m", false); "다음 지점까지 ${dist.toInt()}m" } }
                    else -> { updateNavCard("직진", "${dist.toInt()}m", false); "다음 지점까지 ${dist.toInt()}m" } }
                updateStatusUI(st)
                if (mapInitialized) { val cl = com.google.android.gms.maps.model.LatLng(lat, lng); val h = pose.heading.toFloat()
                    if (myLocationMarker == null) myLocationMarker = googleMap.addMarker(com.google.android.gms.maps.model.MarkerOptions().position(cl).icon(createLocationBitmap(h)).anchor(0.5f, 0.5f).flat(true))
                    else { myLocationMarker?.position = cl; myLocationMarker?.setIcon(createLocationBitmap(h)) } }
                if (dist <= 6.0f) { clearAllArNodes(); currentTargetPointIndex++ }
            }
            if (now - lastChunkRenderTime > 2000) { renderNearbyArrows(lat, lng); lastChunkRenderTime = now }
            updateDirectionDots(lat, lng, pose.heading)
        }
    }

    // ───── 내비 카드 ─────
    private fun updateNavCard(direction: String, distance: String, isArrival: Boolean) { runOnUiThread {
        binding.navDistanceText.text = if (isArrival) "도착!" else distance
        binding.navInstructionText.text = when (direction) { "좌회전" -> "좌회전"; "우회전" -> "우회전"; "좌측" -> "좌측"; "우측" -> "우측"; "목적지" -> "목적지 근처"; else -> "직진" }
        binding.directionIcon.setImageResource(when (direction) { "좌회전", "좌측" -> android.R.drawable.ic_media_rew; "우회전", "우측" -> android.R.drawable.ic_media_ff; "목적지" -> android.R.drawable.ic_menu_mylocation; else -> android.R.drawable.ic_media_play })
        binding.directionIcon.rotation = when (direction) { "좌회전", "우회전", "좌측", "우측", "목적지" -> 0f; else -> -90f }
    }}

    // ───── AR 렌더링 ─────
    private fun renderNearbyArrows(currentLat: Double, currentLng: Double) {
        val earth = sceneView.session?.earth ?: return; if (earth.trackingState != TrackingState.TRACKING || majorPointIndices.size < 2 || currentTargetPointIndex >= majorPointIndices.size) return
        val startIdx = majorPointIndices[currentTargetPointIndex - 1]; val endIdx = majorPointIndices[currentTargetPointIndex]
        for (i in startIdx..endIdx) {
            if (renderedIndices.contains(i)) continue; renderedIndices.add(i); val node = fullRouteNodes[i]
            if (node.type == NodeType.PATH_POINT) continue
            val yOff = if (node.type == NodeType.TURN_POINT) 0.5 else 1.5
            val anchor = earth.createAnchor(node.lat, node.lng, earth.cameraGeospatialPose.altitude - yOff, 0f, 0f, 0f, 1f) ?: continue
            if (lastAnchorAltitude == Double.MAX_VALUE) lastAnchorAltitude = earth.cameraGeospatialPose.altitude
            val anchorNode = AnchorNode(engine = sceneView.engine, anchor = anchor)
            if (node.type == NodeType.TURN_POINT) {
                if (node.turnType == 17 || node.turnType == 18) {
                    val mp = if (node.turnType == 17) "models/left_arrow.glb" else "models/right_arrow.glb"
                    var lbi = i - 1; while (lbi > 0 && fullRouteNodes[lbi].type == NodeType.TURN_POINT) lbi--
                    val lbn = fullRouteNodes.getOrNull(lbi) ?: node; val ir = FloatArray(2); Location.distanceBetween(lbn.lat, lbn.lng, node.lat, node.lng, ir)
                    val mi = majorPointIndices.indexOf(i); if (mi != -1) turnDirectionMap[mi] = (node.turnType == 18)
                    lifecycleScope.launch { sceneView.modelLoader.loadModelInstance(mp)?.let { val an = ModelNode(it).apply { scale = Float3(3f, 3f, 3f); rotation = Float3(0f, ir[1] + 180f, 0f) }; anchorNode.addChildNode(an); activeModelNodes[i] = an }; sceneView.addChildNode(anchorNode); activeArNodes[i] = anchorNode }; continue
                }
                val isRight: Boolean? = when (node.turnType) { 13, 19 -> true; 12, 16 -> false; else -> {
                    val d = FloatArray(1); var pi = i - 1; while (pi > 0) { val c = fullRouteNodes[pi]; if (c.type == NodeType.TURN_POINT) break; Location.distanceBetween(c.lat, c.lng, node.lat, node.lng, d); if (d[0] >= 10f) break; pi-- }
                    var ni = i + 1; while (ni < fullRouteNodes.size - 1) { val c = fullRouteNodes[ni]; if (c.type == NodeType.TURN_POINT) break; Location.distanceBetween(node.lat, node.lng, c.lat, c.lng, d); if (d[0] >= 10f) break; ni++ }
                    val ta = calcTurnAngle(bearingBetween(fullRouteNodes.getOrNull(pi) ?: node, node), bearingBetween(node, fullRouteNodes.getOrNull(ni) ?: node)); if (Math.abs(ta) < 40f) null else ta >= 0f } }
                if (isRight == null) { sceneView.addChildNode(anchorNode); activeArNodes[i] = anchorNode; continue }
                val mi = majorPointIndices.indexOf(i); if (mi != -1) turnDirectionMap[mi] = isRight
                val mp = if (isRight) "models/right.glb" else "models/left.glb"
                var lbi = i - 1; while (lbi > 0 && fullRouteNodes[lbi].type == NodeType.TURN_POINT) lbi--
                val lbn = fullRouteNodes.getOrNull(lbi) ?: node; val ir = FloatArray(2); Location.distanceBetween(lbn.lat, lbn.lng, node.lat, node.lng, ir)
                lifecycleScope.launch { sceneView.modelLoader.loadModelInstance(mp)?.let { val an = ModelNode(it).apply { scale = Float3(3f, 3f, 3f); rotation = Float3(0f, ir[1] + 180f, 0f) }; anchorNode.addChildNode(an); activeModelNodes[i] = an }; sceneView.addChildNode(anchorNode); activeArNodes[i] = anchorNode }
            } else {
                val path = when (node.type) { NodeType.START, NodeType.END -> "models/map_pointer.glb"; else -> continue }
                lifecycleScope.launch { sceneView.modelLoader.loadModelInstance(path)?.let { val h = earth.cameraGeospatialPose.heading.toFloat(); val mn = ModelNode(it).apply { scale = Float3(1f, 1f, 1f); rotation = Float3(0f, h, 0f) }; anchorNode.addChildNode(mn); activeModelNodes[i] = mn; sceneView.addChildNode(anchorNode); activeArNodes[i] = anchorNode } }
            }
        }
    }

    // ───── 나침반 ─────
    private fun updateDirectionDots(lat: Double, lng: Double, heading: Double) {
        val frame = latestFrame ?: return; val pm = FloatArray(16); frame.camera.pose.toMatrix(pm, 0)
        if (-pm[9] >= -0.5f) { runOnUiThread { binding.compassView.visibility = android.view.View.GONE }; isLookingDown = false; dotAnimator?.cancel(); return }
        isLookingDown = true; if (fullRouteNodes.isEmpty() || currentTargetPointIndex >= majorPointIndices.size) return
        val tn = fullRouteNodes[majorPointIndices[currentTargetPointIndex]]; val bt = FloatArray(2); Location.distanceBetween(lat, lng, tn.lat, tn.lng, bt)
        var na = bt[1] - heading.toFloat(); while (na > 180f) na -= 360f; while (na < -180f) na += 360f
        if (kotlin.math.abs(na - targetDotAngle) > 2f) { targetDotAngle = na; dotAnimator?.cancel()
            dotAnimator = android.animation.ValueAnimator.ofFloat(currentDotAngle, targetDotAngle).apply { duration = 300L; interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener { currentDotAngle = it.animatedValue as Float; runOnUiThread { binding.compassView.visibility = android.view.View.VISIBLE; binding.compassView.angleDiff = currentDotAngle } }; start() }
        } else runOnUiThread { binding.compassView.visibility = android.view.View.VISIBLE; binding.compassView.angleDiff = currentDotAngle }
    }

    // ───── 미니맵 ─────
    private fun drawRouteOnMiniMap() {
        googleMap.clear(); routeLatLngs.clear(); myLocationMarker = null
        fullRouteNodes.forEach { routeLatLngs.add(com.google.android.gms.maps.model.LatLng(it.lat, it.lng)) }
        googleMap.addPolyline(com.google.android.gms.maps.model.PolylineOptions().addAll(routeLatLngs).width(8f).color(android.graphics.Color.parseColor("#4285F4")))
        fullRouteNodes.firstOrNull()?.let { googleMap.addMarker(com.google.android.gms.maps.model.MarkerOptions().position(com.google.android.gms.maps.model.LatLng(it.lat, it.lng)).title("출발")) }
        fullRouteNodes.lastOrNull()?.let { googleMap.addMarker(com.google.android.gms.maps.model.MarkerOptions().position(com.google.android.gms.maps.model.LatLng(it.lat, it.lng)).title("목적지")) }
        sceneView.session?.earth?.let { if (it.trackingState == TrackingState.TRACKING) { val p = it.cameraGeospatialPose; googleMap.moveCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(com.google.android.gms.maps.model.LatLng(p.latitude, p.longitude), 17f)) } }
    }

    private fun createLocationBitmap(bearing: Float): com.google.android.gms.maps.model.BitmapDescriptor {
        val s = 120; val bm = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888); val cv = android.graphics.Canvas(bm)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#CC0000") }
        val dp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#8B0000") }
        cv.rotate(bearing, s / 2f, s / 2f)
        cv.drawPath(android.graphics.Path().apply { moveTo(s/2f, 10f); lineTo(s/2f-30f, s-20f); lineTo(s/2f, s-40f); close() }, dp)
        cv.drawPath(android.graphics.Path().apply { moveTo(s/2f, 10f); lineTo(s/2f+30f, s-20f); lineTo(s/2f, s-40f); close() }, p)
        return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bm)
    }

    private fun bearingBetween(from: ArRouteNode, to: ArRouteNode): Float { val r = FloatArray(2); Location.distanceBetween(from.lat, from.lng, to.lat, to.lng, r); return r[1] }
    private fun calcTurnAngle(incoming: Float, outgoing: Float): Float { var d = outgoing - incoming; while (d > 180f) d -= 360f; while (d < -180f) d += 360f; return d }

    private fun clearAllArNodes() {
        altitudeCorrectionJob?.cancel(); altitudeCorrectionJob = null; dotAnimator?.cancel(); dotAnimator = null; currentDotAngle = 0f; targetDotAngle = 0f
        activeArNodes.values.forEach { it.destroy() }; activeArNodes.clear(); activeModelNodes.clear(); renderedIndices.clear(); turnDirectionMap.clear(); lastAnchorAltitude = Double.MAX_VALUE
        runOnUiThread { binding.compassView.visibility = android.view.View.GONE }
    }

    private fun updateStatusUI(message: String) { runOnUiThread { statusTextView.text = message } }
}
