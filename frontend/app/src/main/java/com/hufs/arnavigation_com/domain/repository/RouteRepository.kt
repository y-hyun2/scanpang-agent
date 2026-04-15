package com.scanpang.arnavigation.domain.repository

import com.scanpang.arnavigation.common.NetworkResult
import com.scanpang.arnavigation.data.remote.dto.*

interface RouteRepository {
    // ── 기존 (Kakao/TMAP 직접) — 레거시, 미사용 ──
    suspend fun searchPlace(
        query: String,
        currentLng: String,
        currentLat: String
    ): NetworkResult<KakaoSearchResponse>

    suspend fun getPedestrianRoute(
        request: TmapRouteRequest
    ): NetworkResult<TmapRouteResponse>

    // ── 신규 (ScanPang 백엔드) ──
    suspend fun searchNavigation(
        message: String,
        lat: Double,
        lng: Double
    ): NetworkResult<NavSearchResponse>

    suspend fun getRoute(
        lat: Double,
        lng: Double,
        destination: DestinationInput,
        language: String = "ko"
    ): NetworkResult<NavRouteResponse>
}
