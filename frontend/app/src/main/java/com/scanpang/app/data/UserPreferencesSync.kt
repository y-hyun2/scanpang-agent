package com.scanpang.app.data

import android.content.Context
import android.util.Log
import com.scanpang.app.data.auth.AuthRepository
import com.scanpang.app.data.remote.RetrofitClient

/**
 * 로그인/앱 시작 시 `GET /user/preferences/{user_id}` 한 번 호출해서 서버 데이터로
 * 로컬 SharedPreferences 를 채운다. 서버 = source of truth (다기기 동기화 보장).
 *
 * - 게스트 모드(미로그인) 면 skip.
 * - 서버 401/404(첫 로그인 → row 없음) 면 무시 — 로컬값은 첫 setter 호출 시 syncToBackend
 *   로 자연스럽게 서버에 push 된다.
 *
 * 호출 시점:
 *  - SplashScreen: sessionStatus 가 Authenticated 로 확정된 직후 (앱 cold start).
 *  - OAuthLoadingScreen: OAuth 로그인 성공 직후 (계정 전환 / 첫 로그인).
 */
object UserPreferencesSync {

    suspend fun pullFromBackend(context: Context) {
        val userId = AuthRepository.currentUserId() ?: return
        val resp = try {
            RetrofitClient.api.getUserPreferences(userId)
        } catch (e: Exception) {
            Log.w("UserPrefsSync", "pull skip — ${e.message}")
            return
        }

        // 1) Onboarding(display_name / language / value_added)
        OnboardingPreferences(context).applyFromBackend(
            displayName = resp.display_name,
            language    = resp.language,
            valueAdded  = resp.value_added,
        )

        // 2) saved_places — 서버 list 가 있으면 로컬 덮어쓰기.
        //    빈 list 면 로컬 보존 (첫 로그인 + 게스트 데이터 보존 시나리오).
        val savedRaw = resp.saved_places
        if (savedRaw.isNotEmpty()) {
            val entries = savedRaw.mapNotNull { raw ->
                val m = raw as? Map<*, *> ?: return@mapNotNull null
                val id = m["id"] as? String ?: return@mapNotNull null
                val name = m["name"] as? String ?: return@mapNotNull null
                SavedPlaceEntry(
                    id = id,
                    name = name,
                    category = (m["category"] as? String).orEmpty(),
                    distanceLine = (m["distanceLine"] as? String).orEmpty(),
                    tags = (m["tags"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
                    target = parseSavedPlaceNavTarget((m["target"] as? String).orEmpty()),
                    savedOrder = (m["savedOrder"] as? Number)?.toLong() ?: 0L,
                    lat = (m["lat"] as? Number)?.toDouble() ?: 0.0,
                    lng = (m["lng"] as? Number)?.toDouble() ?: 0.0,
                )
            }
            SavedPlacesStore(context).replaceAll(entries)
        }

        // 3) search_history — 서버 list 가 있으면 로컬 덮어쓰기.
        val historyRaw = resp.search_history
        if (historyRaw.isNotEmpty()) {
            val items = historyRaw.mapNotNull { it as? String }
            SearchHistoryPreferences(context).replaceAll(items)
        }

        Log.d(
            "UserPrefsSync",
            "pull OK — user=$userId saved=${savedRaw.size} history=${historyRaw.size}",
        )
    }
}
