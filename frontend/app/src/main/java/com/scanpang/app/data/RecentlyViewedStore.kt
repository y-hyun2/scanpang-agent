package com.scanpang.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 사용자가 상세 화면에 실제로 들어가 본 장소 한 건의 기록.
 * Home "최근 본 장소" 섹션은 [viewedAt] 내림차순으로 이 리스트를 그대로 표시한다.
 */
data class RecentlyViewedEntry(
    val id: String,
    val name: String,
    val category: String,
    val distanceLine: String,
    val target: SavedPlaceNavTarget,
    val viewedAt: Long = System.currentTimeMillis(),
)

/**
 * SharedPreferences 기반 단순 LRU — [SavedPlacesStore] 와 같은 형태를 따른다.
 * 검색어가 아니라 "사용자가 상세 화면을 실제로 열어본 장소" 만 누적한다.
 */
class RecentlyViewedStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<RecentlyViewedEntry> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        RecentlyViewedEntry(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            category = o.optString("category", ""),
                            distanceLine = o.optString("distanceLine", ""),
                            target = parseSavedPlaceNavTarget(
                                o.optString("target", SavedPlaceNavTarget.Restaurant.name),
                            ),
                            viewedAt = o.optLong("viewedAt", 0L),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 상세 화면 진입 시 호출 — 같은 id 가 다시 들어오면 timestamp 만 갱신해 최상단으로 끌어올린다.
     * 리스트 길이는 [MAX_ITEMS] 로 캡.
     */
    fun record(entry: RecentlyViewedEntry) {
        val list = getAll().toMutableList()
        list.removeAll { it.id == entry.id }
        list.add(0, entry.copy(viewedAt = System.currentTimeMillis()))
        while (list.size > MAX_ITEMS) list.removeAt(list.lastIndex)
        saveList(list)
    }

    fun remove(id: String) {
        val list = getAll().toMutableList()
        if (list.removeAll { it.id == id }) saveList(list)
    }

    fun clearAll() {
        prefs.edit().remove(KEY_ITEMS).apply()
    }

    private fun saveList(list: List<RecentlyViewedEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("name", e.name)
                    put("category", e.category)
                    put("distanceLine", e.distanceLine)
                    put("target", e.target.name)
                    put("viewedAt", e.viewedAt)
                },
            )
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    companion object {
        const val MAX_ITEMS = 20
        private const val PREFS_NAME = "scanpang_recently_viewed"
        private const val KEY_ITEMS = "items_json"
    }
}
