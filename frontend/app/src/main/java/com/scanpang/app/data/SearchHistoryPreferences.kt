package com.scanpang.app.data

import android.content.Context
import com.scanpang.app.data.auth.AuthRepository
import com.scanpang.app.data.remote.RetrofitClient
import com.scanpang.app.data.remote.SearchHistoryUpdateRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray

private val searchHistorySyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

class SearchHistoryPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRecent(): List<String> {
        val raw = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    add(arr.getString(i))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val current = getRecent().toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        while (current.size > MAX_ITEMS) current.removeAt(current.lastIndex)
        saveList(current)
        syncToBackend(current)
    }

    fun remove(query: String) {
        val current = getRecent().toMutableList()
        if (current.remove(query)) {
            saveList(current)
            syncToBackend(current)
        }
    }

    fun clearAll() {
        prefs.edit().remove(KEY_RECENT).apply()
        syncToBackend(emptyList())
    }

    /** 서버 pull 결과로 로컬 list 를 완전히 교체. syncToBackend 안 호출. */
    fun replaceAll(list: List<String>) {
        saveList(list)
    }

    private fun saveList(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY_RECENT, arr.toString()).apply()
    }

    /** backend user_preferences.search_history 로 fire-and-forget sync. */
    private fun syncToBackend(list: List<String>) {
        val userId = AuthRepository.currentUserId() ?: return
        searchHistorySyncScope.launch {
            try {
                RetrofitClient.api.updateSearchHistory(userId, SearchHistoryUpdateRequest(items = list))
                android.util.Log.d("SearchHistoryPrefs", "syncToBackend OK: ${list.size}건")
            } catch (e: Exception) {
                android.util.Log.e("SearchHistoryPrefs", "syncToBackend FAILED", e)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "scanpang_search_history"
        private const val KEY_RECENT = "recent_queries"
        private const val MAX_ITEMS = 30
    }
}
