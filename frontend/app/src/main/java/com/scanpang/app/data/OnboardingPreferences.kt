package com.scanpang.app.data

import android.content.Context

class OnboardingPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    fun isOnboardingComplete(): Boolean = prefs.getBoolean(KEY_COMPLETE, false)

    fun setOnboardingComplete(value: Boolean) {
        prefs.edit().putBoolean(KEY_COMPLETE, value).apply()
    }

    fun getLanguageCode(): String? = prefs.getString(KEY_LANGUAGE, null)?.takeIf { it.isNotBlank() }

    fun setLanguageCode(code: String) {
        prefs.edit().putString(KEY_LANGUAGE, code).apply()
    }

    fun getDisplayName(): String? = prefs.getString(KEY_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() }

    fun setDisplayName(name: String) {
        prefs.edit().putString(KEY_DISPLAY_NAME, name.trim()).apply()
    }

    fun getTravelPreference(): String? = prefs.getString(KEY_TRAVEL_PREF, null)?.takeIf { it.isNotBlank() }

    fun setTravelPreference(value: String) {
        prefs.edit().putString(KEY_TRAVEL_PREF, value).apply()
    }

    /** 로그아웃 시 온보딩·프로필 저장값 초기화 후 스플래시부터 다시 시작할 때 사용 */
    fun clearForLogout() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREF_FILE = "scanpang_onboarding"

        const val KEY_COMPLETE = "onboarding_complete"
        const val KEY_LANGUAGE = "preferred_language"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_TRAVEL_PREF = "travel_preference"

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

        fun travelPreferenceDisplayLabel(pref: String?): String =
            when (pref) {
                TRAVEL_PREF_HALAL -> "할랄 정보 우선"
                TRAVEL_PREF_GENERAL -> "일반 여행 정보"
                else -> ""
            }
    }
}
