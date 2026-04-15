package com.scanpang.arnavigation.data.repository

import android.util.Log
import com.scanpang.arnavigation.common.NetworkResult
import com.scanpang.arnavigation.data.remote.api.RetrofitClient
import com.scanpang.arnavigation.data.remote.dto.*
import com.scanpang.arnavigation.domain.repository.RouteRepository

class RouteRepositoryImpl : RouteRepository {

    // ── 레거시: Kakao 직접 호출 (기존 코드 유지, 미사용) ──────────────────────
    override suspend fun searchPlace(
        query: String, currentLng: String, currentLat: String
    ): NetworkResult<KakaoSearchResponse> {
        return try {
            val response = RetrofitClient.kakaoApiService.searchPlace(
                query = query, x = currentLng, y = currentLat
            )
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error(response.code(), response.message())
            }
        } catch (e: Exception) {
            NetworkResult.Error(-1, e.localizedMessage ?: "Unknown Error")
        }
    }

    // ── 레거시: TMAP 직접 호출 (기존 코드 유지, 미사용) ──────────────────────
    override suspend fun getPedestrianRoute(
        request: TmapRouteRequest
    ): NetworkResult<TmapRouteResponse> {
        return try {
            val response = RetrofitClient.tmapApiService.getPedestrianRoute(request)
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!)
            } else {
                NetworkResult.Error(response.code(), response.message())
            }
        } catch (e: Exception) {
            NetworkResult.Error(-1, e.localizedMessage ?: "Unknown Error")
        }
    }

    // ── 신규: ScanPang 백엔드 — 장소 검색 ───────────────────────────────────
    override suspend fun searchNavigation(
        message: String, lat: Double, lng: Double
    ): NetworkResult<NavSearchResponse> {
        return try {
            Log.d("ScanPang_API", "🔍 백엔드 검색: message=$message, lat=$lat, lng=$lng")
            val response = RetrofitClient.scanpangApiService.searchNavigation(
                NavSearchRequest(message = message, lat = lat, lng = lng)
            )
            if (response.isSuccessful && response.body() != null) {
                Log.d("ScanPang_API", "✅ 검색 성공: ${response.body()!!.candidates.size}개 후보")
                NetworkResult.Success(response.body()!!)
            } else {
                Log.e("ScanPang_API", "❌ 검색 실패: ${response.code()} ${response.message()}")
                NetworkResult.Error(response.code(), response.message())
            }
        } catch (e: Exception) {
            Log.e("ScanPang_API", "❌ 검색 예외: ${e.message}")
            NetworkResult.Error(-1, e.localizedMessage ?: "Unknown Error")
        }
    }

    // ── 신규: ScanPang 백엔드 — 경로 탐색 ───────────────────────────────────
    override suspend fun getRoute(
        lat: Double, lng: Double, destination: DestinationInput, language: String
    ): NetworkResult<NavRouteResponse> {
        return try {
            Log.d("ScanPang_API", "🧭 경로 요청: ${destination.name} (${destination.pns_lat}, ${destination.pns_lon})")
            val response = RetrofitClient.scanpangApiService.getRoute(
                NavRouteRequest(lat = lat, lng = lng, destination = destination, language = language)
            )
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.d("ScanPang_API", "✅ 경로 성공: ${body.ar_command?.total_distance_m}m, ${body.ar_command?.turn_points?.size}개 턴포인트")
                NetworkResult.Success(body)
            } else {
                Log.e("ScanPang_API", "❌ 경로 실패: ${response.code()} ${response.message()}")
                NetworkResult.Error(response.code(), response.message())
            }
        } catch (e: Exception) {
            Log.e("ScanPang_API", "❌ 경로 예외: ${e.message}")
            NetworkResult.Error(-1, e.localizedMessage ?: "Unknown Error")
        }
    }
}
