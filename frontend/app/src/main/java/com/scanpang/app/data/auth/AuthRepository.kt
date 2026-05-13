package com.scanpang.app.data.auth

import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.Kakao
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Supabase Auth 를 감싸는 단일 진입점.
 *
 * UI 계층은 SDK 타입을 직접 만지지 않고 이 객체만 본다 — 추후 SDK 교체나 mock 주입이 쉬워진다.
 *
 * 호출 패턴:
 *  - 화면이 인증 상태 변화에 반응해야 하면 [sessionStatus] 를 collect (`SessionStatus.Authenticated` 시 진행).
 *  - Splash 처럼 한 번만 확인하면 되는 곳은 [isLoggedIn] / [currentAccessToken] 동기 호출.
 *  - 로그인/로그아웃은 suspend — 화면 코루틴 scope (rememberCoroutineScope 등) 에서 호출.
 */
object AuthRepository {

    private val auth get() = SupabaseProvider.auth

    /** 세션 상태 (Initializing / Authenticated / NotAuthenticated / RefreshFailure 등) — UI flow 구독용 */
    val sessionStatus: StateFlow<SessionStatus> get() = auth.sessionStatus

    /** Retrofit Authorization 헤더에 부착할 access_token 동기 조회. 없으면 null. */
    fun currentAccessToken(): String? = auth.currentSessionOrNull()?.accessToken

    /** Splash 등 단순 분기용. SDK 가 로컬 storage 에서 세션 복원하기 전에 호출하면 false 가 나올 수 있으므로,
     *  반응형 UI 는 [sessionStatus] 를 쓰는 게 정확하다. */
    fun isLoggedIn(): Boolean = auth.currentSessionOrNull() != null

    /**
     * 카카오 OAuth 시작. Custom Tab 으로 Supabase 호스팅 페이지 → 카카오 인증 페이지가 열린다.
     * 결과는 deep link 로 돌아와 [sessionStatus] 가 갱신된다.
     */
    suspend fun signInWithKakao() {
        auth.signInWith(Kakao)
    }

    /** 구글 OAuth — [signInWithKakao] 와 동일한 흐름. */
    suspend fun signInWithGoogle() {
        auth.signInWith(Google)
    }

    /** 로그아웃 — 서버 세션 무효화 + 로컬 토큰 삭제. */
    suspend fun signOut() {
        auth.signOut()
    }
}
