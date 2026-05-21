package com.scanpang.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 앱 전역 TTS 온/오프 상태.
 * AR 탐색, AR 길안내, 프로필 세 곳에서 같은 상태를 공유하기 위해 싱글턴 StateFlow 사용.
 * 화면 진입 시 [init] 으로 SharedPreferences 값을 읽어 초기화하고,
 * 토글 시 [toggle] / [set] 으로 SharedPreferences 와 StateFlow 를 함께 갱신한다.
 */
object TtsState {
    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun init(prefs: AppSettingsPreferences) {
        _enabled.value = prefs.isTtsEnabled()
    }

    fun set(enabled: Boolean, prefs: AppSettingsPreferences) {
        _enabled.value = enabled
        prefs.setTtsEnabled(enabled)
    }

    fun toggle(prefs: AppSettingsPreferences) = set(!_enabled.value, prefs)
}
