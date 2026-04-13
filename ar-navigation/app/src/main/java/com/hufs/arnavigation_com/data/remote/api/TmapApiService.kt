package com.scanpang.arnavigation.data.remote.api

import com.scanpang.arnavigation.data.remote.dto.TmapRouteRequest
import com.scanpang.arnavigation.data.remote.dto.TmapRouteResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface TmapApiService {
    @POST("tmap/routes/pedestrian?version=1")
    suspend fun getPedestrianRoute(
        @Body request: TmapRouteRequest
    ): Response<TmapRouteResponse>
}