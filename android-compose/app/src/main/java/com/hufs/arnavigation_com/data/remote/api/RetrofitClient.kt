package com.scanpang.arnavigation.data.remote.api

import com.scanpang.app.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val kakaoAuthInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
            .header("Authorization", "KakaoAK ${BuildConfig.KAKAO_REST_API_KEY}")
            .build()
        chain.proceed(newRequest)
    }

    private val tmapHeaderInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("appKey", BuildConfig.TMAP_APP_KEY)
            .build()
        chain.proceed(newRequest)
    }

    private val kakaoOkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(kakaoAuthInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private val tmapOkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(tmapHeaderInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    val kakaoApiService: KakaoApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://dapi.kakao.com/")
            .client(kakaoOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(KakaoApiService::class.java)
    }

    val tmapApiService: TmapApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://apis.openapi.sk.com/")
            .client(tmapOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TmapApiService::class.java)
    }

    // ── ScanPang 백엔드 (localhost:8000, adb reverse로 터널링) ─────────────
    private val scanpangOkHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)  // LLM 응답 대기
        .build()

    val scanpangApiService: ScanPangApiService by lazy {
        Retrofit.Builder()
            .baseUrl("http://localhost:8000/")
            .client(scanpangOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ScanPangApiService::class.java)
    }
}