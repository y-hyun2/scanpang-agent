package com.scanpang.arnavigation.domain.usecase

import com.scanpang.arnavigation.common.NetworkResult
import com.scanpang.arnavigation.data.remote.dto.TmapRouteRequest // 반드시 Request를 임포트해야 한다.
import com.scanpang.arnavigation.data.remote.dto.TmapRouteResponse
import com.scanpang.arnavigation.data.remote.dto.KakaoSearchResponse
import com.scanpang.arnavigation.domain.repository.RouteRepository

class GetRoutingDataUseCase(private val repository: RouteRepository) {
    suspend operator fun invoke(destination: String, currentLng: String, currentLat: String): NetworkResult<TmapRouteResponse> {
        val kakaoResult = repository.searchPlace(destination, currentLng, currentLat)

        if (kakaoResult is NetworkResult.Success) {
            val kakaoData = kakaoResult.data as? KakaoSearchResponse

            if (kakaoData != null && kakaoData.documents.isNotEmpty()) {
                val firstPlace = kakaoData.documents[0]

                val tmapRequest = TmapRouteRequest(
                    startX = currentLng,
                    startY = currentLat,
                    endX = firstPlace.x,
                    endY = firstPlace.y,
                    startName = "Current",
                    endName = firstPlace.place_name
                )

                return repository.getPedestrianRoute(tmapRequest)
            }
        }
        return NetworkResult.Error(404, "해당 목적지를 찾을 수 없다.")
    }
}