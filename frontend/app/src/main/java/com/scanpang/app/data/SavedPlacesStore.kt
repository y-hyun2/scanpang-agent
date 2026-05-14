package com.scanpang.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SavedPlaceEntry(
    val id: String,
    val name: String,
    val category: String,
    val distanceLine: String,
    val tags: List<String>,
    val target: SavedPlaceNavTarget,
    val savedOrder: Long = System.currentTimeMillis(),
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
                            distanceLine = o.optString("distanceLine", o.optString("distance", "")),
                            tags = o.optJSONArray("tags")?.toStringList().orEmpty(),
                            target = parseSavedPlaceNavTarget(
                                o.optString("target", SavedPlaceNavTarget.Restaurant.name),
                            ),
                            savedOrder = o.optLong("savedOrder", 0L),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isSaved(id: String): Boolean = getAll().any { it.id == id }

    fun save(entry: SavedPlaceEntry) {
        val list = getAll().toMutableList()
        list.removeAll { it.id == entry.id }
        list.add(0, entry.copy(savedOrder = System.currentTimeMillis()))
        saveList(list)
    }

    fun remove(id: String) {
        val list = getAll().toMutableList()
        if (list.removeAll { it.id == id }) saveList(list)
    }

    private fun saveList(list: List<SavedPlaceEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("name", e.name)
                    put("category", e.category)
                    put("distanceLine", e.distanceLine)
                    put("tags", JSONArray(e.tags))
                    put("target", e.target.name)
                    put("savedOrder", e.savedOrder)
                },
            )
        }
        prefs.edit().putString(KEY_PLACES, arr.toString()).apply()
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
