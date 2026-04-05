package com.scanpang.placeaugmenting

import android.Manifest
import android.location.Location
import android.opengl.Matrix
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import io.github.sceneview.ar.ARScene
import io.github.sceneview.rememberEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

// ── ScanPang 백엔드 데이터 클래스 ────────────────────────────────────────────

data class PlaceQueryRequest(
    val place_id: String = "",
    val building_name: String,
    val user_message: String = "What is this building?",
    val user_lat: Double,
    val user_lng: Double,
    val language: String = "en"
)

data class FloorInfo(val floor: String, val stores: List<String>)

data class ArOverlay(
    val name: String,
    val category: String,
    val floor_info: List<FloorInfo>,
    val open_hours: String,
    val closed_days: String,
    val homepage: String,
    val parking_info: String,
    val admission_fee: String
)

data class Docent(val speech: String, val follow_up_suggestions: List<String>)

data class PlaceQueryResponse(val ar_overlay: ArOverlay, val docent: Docent)

interface ScanpangApi {
    @POST("place/query")
    suspend fun queryPlace(@Body request: PlaceQueryRequest): PlaceQueryResponse
}

private val scanpangApi: ScanpangApi by lazy {
    Retrofit.Builder()
        .baseUrl("http://10.0.2.2:8000/")  // 에뮬레이터 → localhost:8000
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ScanpangApi::class.java)
}

// ── PlaceData ─────────────────────────────────────────────────────────────────

data class PlaceData(
    val id: String,
    val name: String,
    val details: String,
    val latitude: Double,
    val longitude: Double,
    val distance: Float,
    val arOverlay: ArOverlay? = null,
    val docentSpeech: String = ""
)

enum class RecognitionState {
    IDLE,
    SEARCHING,
    SUCCESS,
    FAILURE
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainAppScreen()
        }
    }
}

suspend fun verifyBuildingFromVworld(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
    try {
        val apiKey = "BuildConfig.VWORLD_API_KEY"
        val domain = "http://localhost"
        val urlString = "http://api.vworld.kr/req/wfs?key=$apiKey&domain=$domain&SERVICE=WFS&VERSION=1.1.0&REQUEST=GetFeature&TYPENAME=lt_c_bldginfo&OUTPUTFORMAT=application/json&SRSNAME=EPSG:4326&CQL_FILTER=INTERSECTS(ag_geom,%20POINT($lng%20$lat))"

        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(responseText)
            val features = jsonObject.optJSONArray("features")

            if (features != null && features.length() > 0) {
                val feature = features.getJSONObject(0)
                val properties = feature.optJSONObject("properties") ?: return@withContext null

                val bldNm = properties.optString("bld_nm", "")
                return@withContext if (bldNm.isNotEmpty() && bldNm != "null") bldNm else "이름 없는 건물"
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext null
}

suspend fun searchBuildingNameFromKakao(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
    try {
        val kakaoApiKey = "BuildConfig.KAKAO_API_KEY"
        val urlString = "https://dapi.kakao.com/v2/local/geo/coord2address.json?x=$lng&y=$lat"

        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "KakaoAK $kakaoApiKey")
        connection.connectTimeout = 3000
        connection.readTimeout = 3000

        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(responseText)
            val documents = jsonObject.optJSONArray("documents")

            if (documents != null && documents.length() > 0) {
                val document = documents.optJSONObject(0)
                val roadAddress = document?.optJSONObject("road_address")
                val buildingName = roadAddress?.optString("building_name", "")

                if (!buildingName.isNullOrEmpty()) {
                    return@withContext buildingName
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return@withContext null
}

suspend fun getFinalBuildingName(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
    val vworldResult = verifyBuildingFromVworld(lat, lng) ?: return@withContext "알 수 없는 장소"
    val kakaoBuildingName = searchBuildingNameFromKakao(lat, lng)
    return@withContext kakaoBuildingName ?: vworldResult
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MainAppScreen() {
    val arPermissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    if (arPermissionsState.allPermissionsGranted) {
        GeospatialARScreen()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "AR 내비게이션 구동을 위해\n카메라와 위치 정보가 필요합니다.", textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { arPermissionsState.launchMultiplePermissionRequest() }) {
                Text("권한 허용하기")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeospatialARScreen() {
    val engine = rememberEngine()
    val coroutineScope = rememberCoroutineScope()

    var recognitionStatus by remember { mutableStateOf(RecognitionState.IDLE) }
    var trackingMessage by remember { mutableStateOf("ARCore 초기화 중...") }
    val geospatialAnchors = remember { mutableMapOf<String, Anchor>() }
    var anchorScreenPositions by remember { mutableStateOf<Map<String, Offset>>(emptyMap()) }
    val dynamicPlaces = remember { mutableStateListOf<PlaceData>() }
    var selectedPlace by remember { mutableStateOf<PlaceData?>(null) }
    var triggerHitTest by remember { mutableStateOf(false) }

    val verifiedCache = remember { mutableStateListOf<PlaceData>() }

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

                if (earth.earthState == com.google.ar.core.Earth.EarthState.ENABLED) {
                    if (earth.trackingState == TrackingState.TRACKING) {

                        val pose = earth.cameraGeospatialPose
                        val userLat = pose.latitude
                        val userLng = pose.longitude

                        if (pose.horizontalAccuracy < 2.0) {
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
                                        trackable is com.google.ar.core.DepthPoint) {

                                        if (hitResult.distance > 1.5f) {
                                            val hitGeoPose = earth.getGeospatialPose(hitResult.hitPose)
                                            val targetLat = hitGeoPose.latitude
                                            val targetLng = hitGeoPose.longitude

                                            geospatialAnchors.values.forEach { it.detach() }
                                            geospatialAnchors.clear()
                                            dynamicPlaces.clear()

                                            var cachedPlace: PlaceData? = null
                                            for (cache in verifiedCache) {
                                                Location.distanceBetween(targetLat, targetLng, cache.latitude, cache.longitude, results)
                                                if (results[0] < 2.0f) {
                                                    cachedPlace = cache
                                                    break
                                                }
                                            }

                                            val newId = "Target_${System.currentTimeMillis()}"
                                            val newAnchor = earth.createAnchor(targetLat, targetLng, hitGeoPose.altitude, 0f, 0f, 0f, 1f)
                                            geospatialAnchors[newId] = newAnchor

                                            if (cachedPlace != null) {
                                                Location.distanceBetween(userLat, userLng, cachedPlace.latitude, cachedPlace.longitude, results)
                                                dynamicPlaces.add(cachedPlace.copy(id = newId, distance = results[0]))
                                                recognitionStatus = RecognitionState.SUCCESS
                                            } else {
                                                Location.distanceBetween(userLat, userLng, targetLat, targetLng, results)
                                                val placeData = PlaceData(
                                                    id = newId,
                                                    name = "분석 중...",
                                                    details = "서버와 통신 중.",
                                                    latitude = targetLat,
                                                    longitude = targetLng,
                                                    distance = results[0]
                                                )
                                                dynamicPlaces.add(placeData)

                                                coroutineScope.launch {
                                                    val finalName = getFinalBuildingName(targetLat, targetLng)

                                                    // ScanPang 백엔드 호출
                                                    var arOverlay: ArOverlay? = null
                                                    var docentSpeech = ""
                                                    try {
                                                        val response = scanpangApi.queryPlace(
                                                            PlaceQueryRequest(
                                                                building_name = finalName,
                                                                user_lat = targetLat,
                                                                user_lng = targetLng
                                                            )
                                                        )
                                                        arOverlay = response.ar_overlay
                                                        docentSpeech = response.docent.speech
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }

                                                    val index = dynamicPlaces.indexOfFirst { it.id == newId }
                                                    if (index != -1) {
                                                        val finalPlace = dynamicPlaces[index].copy(
                                                            name = arOverlay?.name?.takeIf { it.isNotEmpty() } ?: finalName,
                                                            details = if (arOverlay != null) "open_hours: ${arOverlay.open_hours}" else "데이터 교차 검증 완료",
                                                            arOverlay = arOverlay,
                                                            docentSpeech = docentSpeech
                                                        )
                                                        dynamicPlaces[index] = finalPlace
                                                        verifiedCache.add(finalPlace)
                                                        recognitionStatus = RecognitionState.SUCCESS
                                                    }
                                                }
                                            }
                                            foundSurface = true
                                            break
                                        }
                                    }
                                }
                                if (!foundSurface && recognitionStatus == RecognitionState.SEARCHING) {
                                    recognitionStatus = RecognitionState.FAILURE
                                }
                            }
                        } else {
                            trackingMessage = "VPS 탐색 중... 주변 건물을 비춰주세요. (현재 오차: ${"%.1f".format(pose.horizontalAccuracy)}m)"
                        }

                        val newPositions = mutableMapOf<String, Offset>()
                        val viewMatrix = FloatArray(16)
                        camera.getViewMatrix(viewMatrix, 0)
                        val projMatrix = FloatArray(16)
                        camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100.0f)

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

                    } else {
                        trackingMessage = "ARCore 트래킹 대기 중... 스마트폰을 천천히 움직이세요."
                    }
                } else {
                    trackingMessage = "GCP 서버 통신 실패 또는 VPS 미지원 지역입니다."
                }
            }
        )

        Box(modifier = Modifier.align(Alignment.Center).size(12.dp).background(Color.White.copy(alpha = 0.5f), CircleShape))

        anchorScreenPositions.forEach { (id, offset) ->
            val placeInfo = dynamicPlaces.find { it.id == id }
            if (placeInfo != null) {
                Column(
                    modifier = Modifier
                        .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                        .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                        .clickable { selectedPlace = placeInfo }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val isPending = placeInfo.name.contains("분석 중")
                    Text(
                        text = if (isPending) "⏳ 분석 중..." else "📍 ${placeInfo.name}",
                        color = if (isPending) Color.Yellow else Color.Cyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    if (!isPending) {
                        Text(text = "거리: ${"%.1f".format(placeInfo.distance)}m", color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }

        Column(modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f))) {
                Text(text = trackingMessage, color = Color.White, modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            AnimatedVisibility(visible = recognitionStatus != RecognitionState.IDLE, enter = fadeIn(), exit = fadeOut()) {
                val statusText = when(recognitionStatus) {
                    RecognitionState.SEARCHING -> "⏳ 거리를 측정하여 연산 중..."
                    RecognitionState.SUCCESS -> "✅ 건물 식별 및 거리 갱신 완료!"
                    RecognitionState.FAILURE -> "❌ 추출 실패: 대상을 찾을 수 없음."
                    else -> ""
                }
                val statusColor = if(recognitionStatus == RecognitionState.SUCCESS) Color.Green else if(recognitionStatus == RecognitionState.FAILURE) Color.Red else Color.Yellow
                Text(text = statusText, color = statusColor, fontWeight = FontWeight.Bold, modifier = Modifier.background(Color.Black.copy(alpha = 0.4f)).padding(8.dp))
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "💡 정확한 인식을 위해 유리가 아닌 불투명한 벽면을 조준하세요.",
                color = Color.White,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp)).padding(8.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { triggerHitTest = true },
                modifier = Modifier.height(56.dp).fillMaxWidth(0.7f),
                shape = RoundedCornerShape(28.dp),
                enabled = recognitionStatus != RecognitionState.SEARCHING
            ) {
                if (recognitionStatus == RecognitionState.SEARCHING) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("바라보는 지점 정보 가져오기", fontWeight = FontWeight.Bold)
                }
            }
        }

        if (selectedPlace != null) {
            val currentPlace = dynamicPlaces.find { it.id == selectedPlace!!.id } ?: selectedPlace!!

            ModalBottomSheet(onDismissRequest = { selectedPlace = null }, containerColor = Color.White) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(text = "건물 상세 정보", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = currentPlace.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "나와의 거리: ${"%.1f".format(currentPlace.distance)} m", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = currentPlace.details, color = Color.DarkGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}