package com.scanpang.arnavigation.data.remote.dto

// ── /navigation/search 요청/응답 ─────────────────────────────────────────────

data class NavSearchRequest(
    val message: String,
    val lat: Double,
    val lng: Double
)

data class NavSearchResponse(
    val speech: String,
    val candidates: List<PoiCandidate>,
    val intent: String,
    val language: String
)

data class PoiCandidate(
    val poi_id: String,
    val name: String,
    val address: String,
    val pns_lat: Double,
    val pns_lon: Double,
    val recommended: Boolean = false
)

// ── /navigation/route 요청/응답 ──────────────────────────────────────────────

data class NavRouteRequest(
    val lat: Double,
    val lng: Double,
    val destination: DestinationInput,
    val language: String = "ko"
)

data class DestinationInput(
    val poi_id: String,
    val pns_lat: Double,
    val pns_lon: Double,
    val name: String
)

data class NavRouteResponse(
    val speech: String,
    val ar_command: ArCommand?
)

data class ArCommand(
    val type: String,
    val route_line: List<LatLng>,
    val turn_points: List<TurnPoint>,
    val destination: NavDestination,
    val total_distance_m: Int,
    val total_time_min: Int
)

data class LatLng(
    val lat: Double,
    val lng: Double
)

data class TurnPoint(
    val lat: Double,
    val lng: Double,
    val turnType: Int,
    val description: String,
    val nearPoiName: String,
    val intersectionName: String,
    val pointType: String,
    val facilityType: String,
    val segment_distance_m: Int,
    val speech: String = ""
)

data class NavDestination(
    val lat: Double,
    val lng: Double,
    val name: String
)

// ── /halal/query 요청/응답 ───────────────────────────────────────────────────

data class HalalQueryRequest(
    val category: String = "",
    val message: String = "",
    val lat: Double,
    val lng: Double,
    val language: String = "en",
    val halal_type: String = "",
    val radius: Int = 0
)

data class HalalQueryResponse(
    val speech: String,
    val category: String,
    val language: String,
    val prayer_times: PrayerTimeData?,
    val qibla: QiblaData?,
    val restaurants: List<HalalRestaurant>,
    val prayer_rooms: List<PrayerRoomDetail>
)

data class PrayerTimeData(
    val fajr: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
    val hijri_date: String,
    val gregorian_date: String
)

data class QiblaData(
    val direction: Double,
    val lat: Double,
    val lng: Double
)

data class HalalRestaurant(
    val restaurant_id: String,
    val name_ko: String,
    val name_en: String,
    val halal_type: String,
    val muslim_cooks_available: Boolean?,
    val no_alcohol_sales: Boolean?,
    val cuisine_type: List<String>,
    val menu_examples: List<String>,
    val distance_m: Double,
    val lat: Double,
    val lng: Double,
    val address: String,
    val phone: String,
    val opening_hours: String,
    val break_time: String,
    val last_order: String
)

data class PrayerRoomDetail(
    val name: String,
    val name_en: String,
    val distance_m: Double,
    val lat: Double,
    val lng: Double,
    val address: String,
    val floor: String,
    val open_hours: String,
    val facilities: Map<String, Boolean>,
    val availability_status: String
)
