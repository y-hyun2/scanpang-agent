package com.scanpang.app.data

import android.content.Context

/**
 * 온보딩 3단계에서 선택하는 부가가치(여행 선호) — 앱 전역에서 Home/Profile 분기에 쓰인다.
 * 저장 시에는 [rawValue] 문자열로, 불러올 땐 [fromRaw] 로 enum 으로 환원한다.
 */
enum class ValueAdded(val rawValue: String) {
    HALAL("halal"),
    VEGAN("vegan"),
    GENERAL("general");

    companion object {
        fun fromRaw(raw: String?): ValueAdded? = entries.firstOrNull { it.rawValue == raw }
    }
}

class OnboardingPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_COMPLETE, false)

    fun setOnboardingComplete(value: Boolean) {
        prefs.edit().putBoolean(KEY_COMPLETE, value).apply()
    }

    fun getLanguageCode(): String? = prefs.getString(KEY_LANGUAGE, null)?.takeIf { it.isNotBlank() }

    fun setLanguageCode(code: String) {
        prefs.edit().putString(KEY_LANGUAGE, code).apply()
        syncToBackend()
    }

    fun getDisplayName(): String? = prefs.getString(KEY_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() }

    fun setDisplayName(name: String) {
        prefs.edit().putString(KEY_DISPLAY_NAME, name.trim()).apply()
        syncToBackend()
    }

    // ── 프로필 사진 (ProfileEditScreen — hufs-cdp `c724b07` 합류) ──
    fun getProfilePhotoUri(): String? = prefs.getString(KEY_PROFILE_PHOTO_URI, null)?.takeIf { it.isNotBlank() }

    fun setProfilePhotoUri(uri: String?) {
        prefs.edit().putString(KEY_PROFILE_PHOTO_URI, uri).apply()
    }

    // ── 부가가치 — enum 기반 신 API (외부 UI에서 사용) ──
    /**
     * 온보딩에서 고른 부가가치(ValueAdded) 를 enum 으로 반환. 미설정 시 null.
     * UI 측은 보통 `?: ValueAdded.GENERAL` 로 기본 분기를 잡으면 된다.
     */
    fun getValueAdded(): ValueAdded? = ValueAdded.fromRaw(prefs.getString(KEY_VALUE_ADDED, null))

    fun setValueAdded(value: ValueAdded) {
        prefs.edit().putString(KEY_VALUE_ADDED, value.rawValue).apply()
        syncToBackend()
    }

    // ── String 기반 구 API (ProfileScreen 등 기존 호출처 호환용) ──
    fun getTravelPreference(): String? = prefs.getString(KEY_VALUE_ADDED, null)?.takeIf { it.isNotBlank() }

    fun setTravelPreference(value: String) {
        prefs.edit().putString(KEY_VALUE_ADDED, value).apply()
    }

    /**
     * 회원탈퇴 시 호출 — 온보딩·프로필·언어·부가가치 등 모든 저장값을 비운다.
     * (로그아웃은 세션만 끊고 데이터는 보존하는 게 표준 UX 이므로 호출하지 않는다.)
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /**
     * 현재 SharedPreferences 값을 backend `user_preferences` 테이블에 upsert.
     * - Supabase Auth 로그인 안 돼 있으면 skip (FK 위반 방지).
     * - GlobalScope fire & forget — 호출자는 await 불필요.
     * - 호출 시점: 온보딩 완료, 프로필 이름/부가가치 변경 직후.
     */
    fun syncToBackend() {
        val userId = com.scanpang.app.data.auth.AuthRepository.currentUserId() ?: return
        val req = com.scanpang.app.data.remote.UserPreferencesUpsertRequest(
            user_id = userId,
            display_name = getDisplayName(),
            language = getLanguageCode(),
            value_added = getValueAdded()?.rawValue?.uppercase(),
        )
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.scanpang.app.data.remote.RetrofitClient.api.upsertUserPreferences(req)
                android.util.Log.d("OnboardingPrefs", "syncToBackend OK: user=$userId display=${req.display_name} value=${req.value_added}")
            } catch (e: Exception) {
                android.util.Log.e("OnboardingPrefs", "syncToBackend FAILED", e)
            }
        }
    }

    companion object {
        private const val PREF_FILE = "scanpang_onboarding"

        const val KEY_COMPLETE = "onboarding_complete"
        const val KEY_LANGUAGE = "preferred_language"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_VALUE_ADDED = "value_added"
        const val KEY_PROFILE_PHOTO_URI = "profile_photo_uri"

        // 구 API 호환 — String 상수
        const val TRAVEL_PREF_HALAL = "halal"
        const val TRAVEL_PREF_GENERAL = "general"

        const val LANG_KO = "ko"
        const val LANG_EN = "en"
        const val LANG_MS = "ms"
        const val LANG_AR = "ar"

        fun languageDisplayLabel(code: String?): String =
            when (code) {
                LANG_KO -> "한국어"
                LANG_EN -> "English"
                LANG_MS -> "Bahasa Melayu"
                LANG_AR -> "العربية"
                else -> "한국어"
            }

        fun valueAddedDisplayLabel(value: ValueAdded?): String =
            when (value) {
                ValueAdded.HALAL -> "할랄 정보 우선"
                ValueAdded.VEGAN -> "비건 정보 우선"
                ValueAdded.GENERAL -> "자유 여행"
                null -> ""
            }

        /** 프로필 등 짧은 태그용. [ValueAdded.GENERAL] 은 온보딩 문구와 달리 "일반"으로만 노출. */
        fun valueAddedShortLabel(value: ValueAdded?): String =
            when (value) {
                ValueAdded.HALAL -> "할랄"
                ValueAdded.VEGAN -> "비건"
                ValueAdded.GENERAL -> "일반"
                null -> ""
            }

        /** 구 API: ProfileScreen 등 String 기반 호출처 호환. */
        fun travelPreferenceDisplayLabel(pref: String?): String =
            valueAddedDisplayLabel(ValueAdded.fromRaw(pref))
    }
}
