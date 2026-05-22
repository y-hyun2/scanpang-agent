package com.scanpang.app.data

import android.content.Context
import com.scanpang.app.data.auth.AuthRepository
import com.scanpang.app.data.remote.RetrofitClient
import com.scanpang.app.data.remote.SavedPlacesUpdateRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// SavedPlacesStore.syncToBackend() 용 fire-and-forget IO scope.
private val savedPlacesSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

data class SavedPlaceEntry(
    val id: String,
    val name: String,
    val category: String,
    val tags: List<String>,
    val target: SavedPlaceNavTarget,
    val savedOrder: Long = System.currentTimeMillis(),
    // 매장 좌표 — 표시 시점에 사용자 현재 위치와 Haversine 으로 거리 동적 계산.
    // 거리값(distanceLine) 을 저장 시점 string 으로 박지 않고 항상 좌표로 계산해야
    // 사용자 위치가 바뀌어도 정확. 좌표가 0.0 이면 옛 row → 거리 안 표시.
    val lat: Double = 0.0,
    val lng: Double = 0.0,
)

enum class SavedPlaceNavTarget {
    Restaurant,
    PrayerRoom,
    TouristSpot,
    Shopping,
    ConvenienceStore,
    Cafe,
    Atm,
    Bank,
    Exchange,
    Subway,
    Restroom,
    Lockers,
    Hospital,
    Pharmacy;

    /** 통합 PlaceDetailScreen 라우트용 categoryKey 로 변환. */
    fun toCategoryKey(): String = when (this) {
        Restaurant -> "restaurant"
        PrayerRoom -> "prayer_room"
        TouristSpot -> "tourist"
        Shopping -> "shopping"
        ConvenienceStore -> "convenience"
        Cafe -> "cafe"
        Atm -> "atm"
        Bank -> "bank"
        Exchange -> "exchange"
        Subway -> "subway"
        Restroom -> "restroom"
        Lockers -> "lockers"
        Hospital -> "hospital"
        Pharmacy -> "pharmacy"
    }

    companion object {
        /** categoryKey(backend or 화면) → enum. 매칭 없으면 Restaurant 로 폴백(보수적). */
        fun fromCategoryKey(key: String): SavedPlaceNavTarget = when (key) {
            "restaurant", "halal_restaurant" -> Restaurant
            "prayer_room" -> PrayerRoom
            "tourist", "tourist_spot", "attraction" -> TouristSpot
            "shopping", "mall" -> Shopping
            "convenience", "convenience_store" -> ConvenienceStore
            "cafe" -> Cafe
            "atm" -> Atm
            "bank" -> Bank
            "exchange" -> Exchange
            "subway", "subway_station" -> Subway
            "restroom", "public_restroom" -> Restroom
            "lockers", "locker" -> Lockers
            "hospital" -> Hospital
            "pharmacy" -> Pharmacy
            else -> Restaurant
        }
    }
}

class SavedPlacesStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<SavedPlaceEntry> {
        val raw = prefs.getString(KEY_PLACES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SavedPlaceEntry(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            category = o.getString("category"),
                            tags = o.optJSONArray("tags")?.toStringList().orEmpty(),
                            target = parseSavedPlaceNavTarget(
                                o.optString("target", SavedPlaceNavTarget.Restaurant.name),
                            ),
                            savedOrder = o.optLong("savedOrder", 0L),
                            lat = o.optDouble("lat", 0.0),
                            lng = o.optDouble("lng", 0.0),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isSaved(id: String): Boolean = getAll().any { it.id == id }

    /**
     * 서버 pull 결과로 로컬 list 를 완전히 교체. syncToBackend 안 호출 — pull → write
     * loop 방지. 빈 list 가 들어오면 로컬도 비움.
     */
    fun replaceAll(list: List<SavedPlaceEntry>) {
        saveList(list)
    }

    fun save(entry: SavedPlaceEntry) {
        val list = getAll().toMutableList()
        list.removeAll { it.id == entry.id }
        list.add(0, entry.copy(savedOrder = System.currentTimeMillis()))
        saveList(list)
        syncToBackend(list)
    }

    fun remove(id: String) {
        val list = getAll().toMutableList()
        if (list.removeAll { it.id == id }) {
            saveList(list)
            syncToBackend(list)
        }
    }

    private fun saveList(list: List<SavedPlaceEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("name", e.name)
                    put("category", e.category)
                    put("tags", JSONArray(e.tags))
                    put("target", e.target.name)
                    put("savedOrder", e.savedOrder)
                    put("lat", e.lat)
                    put("lng", e.lng)
                },
            )
        }
        prefs.edit().putString(KEY_PLACES, arr.toString()).apply()
    }

    /**
     * 전체 list 를 backend `user_preferences.saved_places` 로 전송.
     * Supabase Auth 안 돼 있으면 skip (FK 위반 방지). 실패해도 로컬은 유지.
     */
    private fun syncToBackend(list: List<SavedPlaceEntry>) {
        val userId = AuthRepository.currentUserId() ?: return
        val items = list.map { e ->
            mapOf(
                "id" to e.id,
                "name" to e.name,
                "category" to e.category,
                "tags" to e.tags,
                "target" to e.target.name,
                "savedOrder" to e.savedOrder,
                "lat" to e.lat,
                "lng" to e.lng,
            )
        }
        savedPlacesSyncScope.launch {
            try {
                RetrofitClient.api.updateSavedPlaces(userId, SavedPlacesUpdateRequest(items = items))
                android.util.Log.d("SavedPlacesStore", "syncToBackend OK: ${items.size}건")
            } catch (e: Exception) {
                android.util.Log.e("SavedPlacesStore", "syncToBackend FAILED", e)
            }
        }
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (i in 0 until length()) add(getString(i))
    }

    companion object {
        private const val PREFS_NAME = "scanpang_saved_places"
        private const val KEY_PLACES = "places_json"
    }
}

internal fun parseSavedPlaceNavTarget(raw: String): SavedPlaceNavTarget =
    SavedPlaceNavTarget.entries.firstOrNull { it.name == raw }
        ?: SavedPlaceNavTarget.Restaurant
