package com.scanpang.app.ar.explore

import android.Manifest
import android.location.Location
import android.opengl.Matrix
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.TrackingState
import com.scanpang.app.data.remote.PlaceQueryRequest
import com.scanpang.app.data.remote.PlaceQueryResponse
import com.scanpang.app.data.remote.RetrofitClient
import io.github.sceneview.ar.ARScene
import io.github.sceneview.rememberEngine
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

data class PlaceData(
    val id: String,
    val name: String,
    val details: String,
    val latitude: Double,
    val longitude: Double,
    val distance: Float,
    val arOverlay: com.scanpang.app.data.remote.ArOverlay? = null,
    val docentSpeech: String = "",
)

enum class RecognitionState { IDLE, SEARCHING, SUCCESS, FAILURE }

class PlaceAugmentingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PlaceAugmentingScreen() }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PlaceAugmentingScreen() {
    val arPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    )
    if (arPermissionsState.allPermissionsGranted) {
        GeospatialARExploreScreen()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "AR 탐색을 위해\n카메라와 위치 정보가 필요합니다.", textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { arPermissionsState.launchMultiplePermissionRequest() }) {
                Text("권한 허용하기")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeospatialARExploreScreen() {
    val engine = rememberEngine()
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val api = remember { RetrofitClient.api }

    var recognitionStatus by remember { mutableStateOf(RecognitionState.IDLE) }
    var trackingMessage by remember { mutableStateOf("ARCore 초기화 중...") }
    val geospatialAnchors = remember { mutableMapOf<String, Anchor>() }
    var anchorScreenPositions by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }
    val dynamicPlaces = remember { mutableStateListOf<PlaceData>() }
    var selectedPlace by remember { mutableStateOf<PlaceData?>(null) }
    var triggerHitTest by remember { mutableStateOf(false) }
    var currentHeading by remember { mutableStateOf(0.0) }
    var currentAltitude by remember { mutableStateOf(0.0) }
    var currentPitch by remember { mutableStateOf(0.0) }
    var hasAchievedHighAccuracy by remember { mutableStateOf(false) }
    val verifiedCache = remember { mutableStateListOf<PlaceData>() }

    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts.value = instance
        }
        onDispose { instance?.stop(); instance?.shutdown() }
    }

    fun speakDocent(speech: String, language: String = "ko") {
        if (speech.isEmpty()) return
        val locale = when (language) {
            "ko" -> Locale.KOREAN; "ja" -> Locale.JAPANESE
            "zh" -> Locale.CHINESE; "ar" -> Locale("ar")
            else -> Locale.ENGLISH
        }
        tts.value?.language = locale
        tts.value?.speak(speech, TextToSpeech.QUEUE_FLUSH, null, "docent")
    }

    suspend fun callPlaceQuery(heading: Double, lat: Double, lng: Double, alt: Double, pitch: Double): PlaceQueryResponse? {
        return try {
            api.queryPlace(PlaceQueryRequest(heading = heading, user_lat = lat, user_lng = lng, user_alt = alt, pitch = pitch))
        } catch (e: Exception) {
            android.util.Log.e("SCANPANG", "API 오류: ${e.message}")
            null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val density = androidx.compose.ui.platform.LocalDensity.current
        val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }.toInt()
        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }.toInt()

        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            planeRenderer = false,
            sessionConfiguration = { session, config ->
                config.geospatialMode = Config.GeospatialMode.ENABLED
                config.depthMode = Config.DepthMode.AUTOMATIC
            },
            onSessionUpdated = { session, frame ->
                val earth = session.earth ?: return@ARScene
                val camera = frame.camera
                if (earth.earthState == com.google.ar.core.Earth.EarthState.ENABLED &&
                    earth.trackingState == TrackingState.TRACKING
                ) {
                    val pose = earth.cameraGeospatialPose
                    val userLat = pose.latitude
                    val userLng = pose.longitude
                    currentHeading = pose.heading
                    currentAltitude = pose.altitude

                    val q = pose.eastUpSouthQuaternion
                    val fx = 2f * (q[0] * q[2] + q[3] * q[1])
                    val fy = 2f * (q[1] * q[2] - q[3] * q[0])
                    val fz = 1f - 2f * (q[0] * q[0] + q[1] * q[1])
                    val horiz = kotlin.math.sqrt(fx * fx + fz * fz)
                    currentPitch = Math.toDegrees(kotlin.math.atan2(-fy.toDouble(), horiz.toDouble()))

                    if (pose.horizontalAccuracy < 1.5) hasAchievedHighAccuracy = true

                    if (hasAchievedHighAccuracy) {
                        trackingMessage = "위치 파악 완료 (오차: ${"%.1f".format(pose.horizontalAccuracy)}m)"
                        val results = FloatArray(1)
                        for (i in dynamicPlaces.indices) {
                            Location.distanceBetween(userLat, userLng, dynamicPlaces[i].latitude, dynamicPlaces[i].longitude, results)
                            dynamicPlaces[i] = dynamicPlaces[i].copy(distance = results[0])
                        }

                        if (triggerHitTest) {
                            triggerHitTest = false
                            recognitionStatus = RecognitionState.SEARCHING
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
                                        geospatialAnchors.values.forEach { it.detach() }
                                        geospatialAnchors.clear()
                                        dynamicPlaces.clear()

                                        var cachedPlace: PlaceData? = null
                                        for (cache in verifiedCache) {
                                            Location.distanceBetween(hitGeoPose.latitude, hitGeoPose.longitude, cache.latitude, cache.longitude, results)
                                            if (results[0] < 2.0f) { cachedPlace = cache; break }
                                        }

                                        val newId = "Target_${System.currentTimeMillis()}"
                                        val newAnchor = earth.createAnchor(hitGeoPose.latitude, hitGeoPose.longitude, hitGeoPose.altitude, 0f, 0f, 0f, 1f)
                                        geospatialAnchors[newId] = newAnchor

                                        if (cachedPlace != null) {
                                            Location.distanceBetween(userLat, userLng, cachedPlace.latitude, cachedPlace.longitude, results)
                                            dynamicPlaces.add(cachedPlace.copy(id = newId, distance = results[0]))
                                            recognitionStatus = RecognitionState.SUCCESS
                                        } else {
                                            Location.distanceBetween(userLat, userLng, hitGeoPose.latitude, hitGeoPose.longitude, results)
                                            dynamicPlaces.add(PlaceData(newId, "분석 중...", "서버와 통신 중.", hitGeoPose.latitude, hitGeoPose.longitude, results[0]))
                                            coroutineScope.launch {
                                                val response = callPlaceQuery(currentHeading, userLat, userLng, currentAltitude, currentPitch)
                                                val index = dynamicPlaces.indexOfFirst { it.id == newId }
                                                if (index != -1) {
                                                    val finalPlace = dynamicPlaces[index].copy(
                                                        name = response?.ar_overlay?.name?.takeIf { it.isNotEmpty() } ?: "알 수 없는 장소",
                                                        details = response?.ar_overlay?.let { "open_hours: ${it.open_hours}" } ?: "",
                                                        arOverlay = response?.ar_overlay,
                                                        docentSpeech = response?.docent?.speech ?: "",
                                                    )
                                                    dynamicPlaces[index] = finalPlace
                                                    verifiedCache.add(finalPlace)
                                                    recognitionStatus = RecognitionState.SUCCESS
                                                }
                                            }
                                        }
                                        foundSurface = true; break
                                    }
                                }
                            }

                            if (!foundSurface && recognitionStatus == RecognitionState.SEARCHING) {
                                geospatialAnchors.values.forEach { it.detach() }
                                geospatialAnchors.clear(); dynamicPlaces.clear()
                                val newId = "Target_${System.currentTimeMillis()}"
                                val newAnchor = earth.createAnchor(userLat, userLng, pose.altitude, 0f, 0f, 0f, 1f)
                                geospatialAnchors[newId] = newAnchor
                                dynamicPlaces.add(PlaceData(newId, "분석 중...", "서버와 통신 중.", userLat, userLng, 0f))
                                coroutineScope.launch {
                                    val response = callPlaceQuery(currentHeading, userLat, userLng, currentAltitude, currentPitch)
                                    val index = dynamicPlaces.indexOfFirst { it.id == newId }
                                    if (index != -1) {
                                        val finalPlace = dynamicPlaces[index].copy(
                                            name = response?.ar_overlay?.name?.takeIf { it.isNotEmpty() } ?: "알 수 없는 장소",
                                            details = response?.ar_overlay?.let { "open_hours: ${it.open_hours}" } ?: "",
                                            arOverlay = response?.ar_overlay,
                                            docentSpeech = response?.docent?.speech ?: "",
                                        )
                                        dynamicPlaces[index] = finalPlace
                                        verifiedCache.add(finalPlace)
                                        recognitionStatus = RecognitionState.SUCCESS
                                    }
                                }
                            }
                        }
                    } else {
                        trackingMessage = "VPS 정밀 탐색 중... (현재 오차: ${"%.1f".format(pose.horizontalAccuracy)}m / 1.5m 미만 필요)"
                    }

                    val newPositions = mutableMapOf<String, Offset>()
                    val viewMatrix = FloatArray(16); camera.getViewMatrix(viewMatrix, 0)
                    val projMatrix = FloatArray(16); camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100.0f)
                    geospatialAnchors.forEach { (id, anchor) ->
                        if (anchor.trackingState == TrackingState.TRACKING) {
                            val anchorPose = anchor.pose
                            val anchorTranslation = floatArrayOf(anchorPose.tx(), anchorPose.ty(), anchorPose.tz(), 1f)
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

        // 조준점
        Box(modifier = Modifier.align(Alignment.Center).size(12.dp).background(Color.White.copy(alpha = 0.5f), CircleShape))

        // AR 마커
        anchorScreenPositions.forEach { (id, offset) ->
            val placeInfo = dynamicPlaces.find { it.id == id }
            if (placeInfo != null) {
                Column(
                    modifier = Modifier
                        .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                        .clickable { selectedPlace = placeInfo }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val isPending = placeInfo.name.contains("분석 중")
                    Text(
                        text = if (isPending) "⏳ 분석 중..." else "📍 ${placeInfo.name}",
                        color = if (isPending) Color.Yellow else Color.Cyan,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    )
                    if (!isPending) {
                        Text(text = "거리: ${"%.1f".format(placeInfo.distance)}m", color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }

        // 상단 상태바
        Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f))) {
                Text(text = trackingMessage, color = Color.White, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            AnimatedVisibility(visible = recognitionStatus != RecognitionState.IDLE, enter = fadeIn(), exit = fadeOut()) {
                val statusText = when (recognitionStatus) {
                    RecognitionState.SEARCHING -> "⏳ 건물 분석 중..."
                    RecognitionState.SUCCESS -> "✅ 건물 식별 완료!"
                    RecognitionState.FAILURE -> "❌ 추출 실패"
                    else -> ""
                }
                val statusColor = when (recognitionStatus) {
                    RecognitionState.SUCCESS -> Color.Green
                    RecognitionState.FAILURE -> Color.Red
                    else -> Color.Yellow
                }
                Text(text = statusText, color = statusColor, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color.Black.copy(alpha = 0.4f)).padding(8.dp))
            }
        }

        // 하단 버튼
        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "💡 불투명한 벽면을 조준하세요.",
                color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(8.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { triggerHitTest = true },
                modifier = Modifier.height(56.dp).fillMaxWidth(0.7f),
                shape = RoundedCornerShape(28.dp),
                enabled = recognitionStatus != RecognitionState.SEARCHING && hasAchievedHighAccuracy,
            ) {
                if (recognitionStatus == RecognitionState.SEARCHING) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else if (!hasAchievedHighAccuracy) {
                    Text("VPS 정밀 탐색 중...", fontWeight = FontWeight.Bold)
                } else {
                    Text("바라보는 지점 정보 가져오기", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Bottom Sheet
        if (selectedPlace != null) {
            val currentPlace = dynamicPlaces.find { it.id == selectedPlace!!.id } ?: selectedPlace!!
            val overlay = currentPlace.arOverlay
            val sheetBg = Color(0xFF1C1C1E)
            val dividerColor = Color(0xFF3A3A3C)
            val floorColors = listOf(Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFFFFBE0B), Color(0xFFFF006E), Color(0xFF8338EC), Color(0xFF3A86FF), Color(0xFF06D6A0), Color(0xFFFF9F1C))

            ModalBottomSheet(onDismissRequest = { selectedPlace = null }, containerColor = sheetBg) {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 48.dp)) {
                    Text(text = currentPlace.name, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!overlay?.category.isNullOrEmpty()) {
                            Box(modifier = Modifier.background(Color(0xFF2C2C2E), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                                Text(text = overlay!!.category, color = Color.LightGray, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "·", color = Color.Gray, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(text = "${"%.0f".format(currentPlace.distance)}m", color = Color.LightGray, fontSize = 13.sp)
                    }

                    if (overlay?.is_estimated == true) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF3B2A00), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠️", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "AI가 이미지를 분석한 추정 정보입니다.", color = Color(0xFFFFBE0B), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
                    Spacer(modifier = Modifier.height(20.dp))

                    if (!overlay?.floor_info.isNullOrEmpty()) {
                        Text(text = "층별 정보", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))
                        overlay!!.floor_info.forEachIndexed { index, floorInfo ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(width = 40.dp, height = 32.dp).background(floorColors[index % floorColors.size], RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                    Text(text = floorInfo.floor, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(text = floorInfo.stores.joinToString(", "), color = Color.LightGray, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    val touristCategories = listOf("관광", "문화", "공연", "극장", "성당", "교회", "박물관", "미술관")
                    val isTouristPlace = touristCategories.any { overlay?.category?.contains(it) == true }
                    if (currentPlace.docentSpeech.isNotEmpty() && isTouristPlace) {
                        Button(onClick = { speakDocent(currentPlace.docentSpeech) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A86FF))) {
                            Text("🎙️ 도슨트 해설 듣기", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(dividerColor))
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    val hasInfo = listOf(overlay?.open_hours, overlay?.closed_days, overlay?.admission_fee, overlay?.parking_info).any { !it.isNullOrEmpty() }
                    if (hasInfo) {
                        Text(text = "이용 정보", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        Spacer(modifier = Modifier.height(12.dp))
                        if (!overlay?.open_hours.isNullOrEmpty()) InfoRow("운영 시간", overlay!!.open_hours)
                        if (!overlay?.closed_days.isNullOrEmpty()) InfoRow("휴무일", overlay!!.closed_days)
                        if (!overlay?.admission_fee.isNullOrEmpty()) InfoRow("입장료", overlay!!.admission_fee)
                        if (!overlay?.parking_info.isNullOrEmpty()) InfoRow("주차", overlay!!.parking_info)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(text = label, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(72.dp))
        Text(text = value, color = Color.LightGray, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}
