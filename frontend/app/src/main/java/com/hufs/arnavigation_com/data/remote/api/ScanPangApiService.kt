package com.scanpang.arnavigation.data.remote.api

import com.scanpang.arnavigation.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ScanPangApiService {

    @POST("navigation/search")
    suspend fun searchNavigation(
        @Body request: NavSearchRequest
    ): Response<NavSearchResponse>

    @POST("navigation/route")
    suspend fun getRoute(
        @Body request: NavRouteRequest
    ): Response<NavRouteResponse>

    @POST("halal/query")
    suspend fun halalQuery(
        @Body request: HalalQueryRequest
    ): Response<HalalQueryResponse>
}
