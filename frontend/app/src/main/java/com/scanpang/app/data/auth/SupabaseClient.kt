package com.scanpang.app.data.auth

import com.scanpang.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient

/**
 * 앱 전역에서 공유하는 Supabase 클라이언트.
 *
 * - URL/anon key 는 `local.properties` → BuildConfig 경유로 주입된다.
 *   (둘 중 하나라도 비면 [IllegalStateException] 으로 fail-fast — 키 누락을 런타임 깊은 곳에서 발견하면 디버깅이 어렵다.)
 * - Auth 의 [FlowType.PKCE] 는 OAuth 콜백을 deep link 로 받아도 인가코드 교환을 안전하게 수행한다.
 *   (Implicit flow 는 토큰이 URL fragment 로 노출되어 deep link 환경에 부적합)
 * - 토큰 저장소는 supabase-kt 기본(SharedPreferences). 추후 EncryptedSharedPreferences 로 wrap 예정.
 */
object SupabaseProvider {

    val client: SupabaseClient by lazy {
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_ANON_KEY
        check(url.isNotBlank() && key.isNotBlank()) {
            "SUPABASE_URL / SUPABASE_ANON_KEY 가 비어있습니다. local.properties 를 확인하세요."
        }
        createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key,
        ) {
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = BuildConfig.OAUTH_REDIRECT_SCHEME
                host = BuildConfig.OAUTH_REDIRECT_HOST
            }
        }
    }

    /** 짧은 별칭 — 호출부에서 `SupabaseProvider.auth.signInWith(...)` 처럼 쓰기 위함. */
    val auth: Auth get() = client.auth
}
