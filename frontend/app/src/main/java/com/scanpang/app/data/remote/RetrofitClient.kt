package com.scanpang.app.data.remote

import android.util.Log
import com.scanpang.app.BuildConfig
import com.scanpang.app.data.auth.AuthRepository
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    val BASE_URL: String = BuildConfig.SERVER_URL

    init {
        Log.d("RetrofitClient", "BASE_URL = $BASE_URL")
    }

    /**
     * 매 요청마다 [AuthRepository.currentAccessToken] 를 읽어 `Authorization: Bearer <jwt>` 부착.
     * Supabase SDK 가 background 로 refresh 를 처리하므로 인터셉터는 단순히 현재 토큰만 읽으면 된다.
     * 비로그인 상태(토큰 null) 면 헤더 미부착 — 서버는 401 로 응답하고, 401 처리는 호출부 책임.
     */
    private class SupabaseAuthInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val token = AuthRepository.currentAccessToken()
            val request = if (token != null) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            return chain.proceed(request)
        }
    }

    /**
     * ngrok 무료 플랜은 첫 요청에 HTML 경고 페이지를 띄움 → JSON 파싱 실패.
     * `ngrok-skip-browser-warning` 헤더로 우회.
     */
    private class NgrokSkipWarningInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request().newBuilder()
                .header("ngrok-skip-browser-warning", "true")
                .header("User-Agent", "ScanPangAndroid/1.0")
                .build()
            return chain.proceed(request)
        }
    }

    val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(NgrokSkipWarningInterceptor())
        .addInterceptor(SupabaseAuthInterceptor())
        .addInterceptor(
            HttpLoggingInterceptor { message ->
                Log.d("OkHttp", message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
                // 토큰을 logcat 에 그대로 흘리지 않도록 마스킹. value 가 "**REDACTED**" 로 표시된다.
                redactHeader("Authorization")
            },
        )
        .build()

    val api: ScanPangApi by lazy {
        Log.d("RetrofitClient", "Creating ScanPangApi with baseUrl=$BASE_URL")
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ScanPangApi::class.java)
    }
}
