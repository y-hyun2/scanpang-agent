package com.scanpang.arnavigation.data.remote.api

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import com.scanpang.arnavigation.data.remote.dto.KakaoSearchResponse
import retrofit2.Response

interface KakaoApiService {
    @GET("v2/local/search/keyword.json")
    suspend fun searchPlace(
        @Query("query") query: String,
        @Query("x") x: String,      // 🚨 내 현재 경도(Longitude) 필수!
        @Query("y") y: String,      // 🚨 내 현재 위도(Latitude) 필수!
        @Query("radius") radius: Int = 5000, // 🚨 내 주변 5km(5000m) 이내만 검색!
        @Query("sort") sort: String = "distance" // 🚨 무조건 가까운 순서대로 1등!
    ): Response<KakaoSearchResponse>
}