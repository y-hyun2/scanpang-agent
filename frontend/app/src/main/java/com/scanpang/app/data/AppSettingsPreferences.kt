package com.scanpang.app.data

import android.content.Context

/**
 * 온보딩 외 앱 사용 중 토글로 켜고 끄는 설정값 저장소.
 *
 * 분리 이유:
 * - 회원탈퇴(`OnboardingPreferences.clearAll()`) 시 알림/TTS 같은 디바이스-로컬 설정을
 *   함께 날리면 동일 단말에서 다른 계정으로 재로그인했을 때도 어색하다.
 * - 의미상 "프로필 / 온보딩 선택값" 과 "디바이스 설정" 은 다른 라이프사이클이므로 prefs 파일을 분리.
 */
class AppSettingsPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    // ── TTS 음성 안내 ────────────────────────────────────────────────
    fun isTtsEnabled(): Boolean = prefs.getBoolean(KEY_TTS, true)
    fun setTtsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TTS, enabled).apply()
    }

    // ── 푸시 알림 (마스터 스위치) ─────────────────────────────────────
    fun isPushEnabled(): Boolean = prefs.getBoolean(KEY_PUSH, true)
    fun setPushEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PUSH, enabled).apply()
    }

    // ── 알림 종류 ────────────────────────────────────────────────────
    fun isPrayerAlarmEnabled(): Boolean = prefs.getBoolean(KEY_PRAYER_ALARM, true)
    fun setPrayerAlarmEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRAYER_ALARM, enabled).apply()
    }

    fun isEventPromoEnabled(): Boolean = prefs.getBoolean(KEY_EVENT_PROMO, false)
    fun setEventPromoEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EVENT_PROMO, enabled).apply()
    }

    // ── 방해 금지 모드 ───────────────────────────────────────────────
    fun isDoNotDisturbEnabled(): Boolean = prefs.getBoolean(KEY_DND, false)
    fun setDoNotDisturbEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DND, enabled).apply()
    }

    companion object {
        private const val PREF_FILE = "scanpang_app_settings"
        private const val KEY_TTS = "tts_enabled"
        private const val KEY_PUSH = "push_enabled"
        private const val KEY_PRAYER_ALARM = "prayer_alarm_enabled"
        private const val KEY_EVENT_PROMO = "event_promo_enabled"
        private const val KEY_DND = "do_not_disturb_enabled"
    }
}
