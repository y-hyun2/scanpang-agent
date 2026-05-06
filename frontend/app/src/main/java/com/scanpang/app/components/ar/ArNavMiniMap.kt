package com.scanpang.app.components.ar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * AR 길안내용 미니맵 — 사용자 위치 + 라우트 폴리라인 + 목적지 마커.
 *
 * 사용자가 처음 위치를 잡으면 그 위치로 한 번 카메라 줌인하고,
 * 그 후엔 폰을 자유롭게 패닝/줌할 수 있게 둠 (계속 따라가지 않음 — 시연 시 양손이 묶이지 않도록).
 */
@Composable
fun ArNavMiniMap(
    userLat: Double,
    userLng: Double,
    userHeading: Double,
    routePoints: List<Pair<Double, Double>>,
    destinationLat: Double?,
    destinationLng: Double?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cameraPositionState = rememberCameraPositionState()

    // Maps SDK 초기화가 끝난 뒤에만 BitmapDescriptorFactory 사용 가능.
    // 초기화 전에 호출하면 NullPointerException 발생.
    var userMarkerIcon by remember { mutableStateOf<BitmapDescriptor?>(null) }
    LaunchedEffect(Unit) {
        suspendCancellableCoroutine<Unit> { cont ->
            MapsInitializer.initialize(context, MapsInitializer.Renderer.LATEST) {
                cont.resume(Unit)
            }
        }
        userMarkerIcon = runCatching { createUserArrowBitmap() }.getOrNull()
    }

    // 사용자 위치를 처음 받았을 때 한 번만 줌인
    var cameraInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(userLat, userLng) {
        if (!cameraInitialized && (userLat != 0.0 || userLng != 0.0)) {
            cameraPositionState.move(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.fromLatLngZoom(LatLng(userLat, userLng), 17f),
                ),
            )
            cameraInitialized = true
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(mapType = MapType.NORMAL),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            compassEnabled = false,
        ),
    ) {
        // 라우트 폴리라인
        if (routePoints.isNotEmpty()) {
            Polyline(
                points = routePoints.map { LatLng(it.first, it.second) },
                color = Color(0xFF4285F4),
                width = 12f,
            )
        }

        // 목적지 마커
        if (destinationLat != null && destinationLng != null) {
            Marker(
                state = MarkerState(position = LatLng(destinationLat, destinationLng)),
                title = "목적지",
            )
        }

        // 사용자 위치 마커 (커스텀 화살표, heading만큼 회전, flat=true로 지도에 평면 부착)
        // 아이콘이 준비되기 전에는 마커 자체를 그리지 않음 (Maps 초기화 의존)
        if (userMarkerIcon != null && (userLat != 0.0 || userLng != 0.0)) {
            Marker(
                state = MarkerState(position = LatLng(userLat, userLng)),
                icon = userMarkerIcon,
                anchor = Offset(0.5f, 0.5f),
                rotation = userHeading.toFloat(),
                flat = true,
                title = "내 위치",
            )
        }
    }
}

/** 카메라를 사용자 위치로 다시 센터링 (외부에서 버튼 등으로 호출 가능). */
fun CameraPositionState.recenterTo(lat: Double, lng: Double, zoom: Float = 17f) {
    move(
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.fromLatLngZoom(LatLng(lat, lng), zoom),
        ),
    )
}

/**
 * 위쪽(북쪽)을 향한 빨간 삼각형 화살표 비트맵.
 * Marker.rotation으로 heading만큼 돌리면 사용자 진행 방향을 표시.
 * (`ArNavigationActivity.createLocationBitmap` 동등)
 */
private fun createUserArrowBitmap(): BitmapDescriptor {
    val size = 120
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val rightHalf = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#CC0000")
    }
    val leftHalf = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#8B0000")
    }
    val mid = size / 2f
    // 좌측 반쪽 (어두운 빨강)
    canvas.drawPath(
        Path().apply {
            moveTo(mid, 10f)
            lineTo(mid - 30f, size - 20f)
            lineTo(mid, size - 40f)
            close()
        },
        leftHalf,
    )
    // 우측 반쪽 (밝은 빨강)
    canvas.drawPath(
        Path().apply {
            moveTo(mid, 10f)
            lineTo(mid + 30f, size - 20f)
            lineTo(mid, size - 40f)
            close()
        },
        rightHalf,
    )
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
