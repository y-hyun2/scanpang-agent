package com.scanpang.app.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

interface ScanPangApi {

    // ── Navigation ──
    @POST("navigation/search")
    suspend fun searchNavigation(@Body request: NavSearchRequest): NavSearchResponse

    @POST("navigation/route")
    suspend fun getRoute(@Body request: NavRouteRequest): NavRouteResponse

    // ── Place Insight ──
    @POST("place/query")
    suspend fun queryPlace(@Body request: PlaceQueryRequest): PlaceQueryResponse

    @POST("place/store")
    suspend fun queryStore(@Body request: StoreRequest): StoreResponse

    // ── Convenience ──
    @POST("convenience/query")
    suspend fun queryConvenience(@Body request: ConvenienceRequest): ConvenienceResponse

    // ── Halal ──
    @POST("halal/query")
    suspend fun queryHalal(@Body request: HalalRequest): HalalResponse
}

// ── Navigation DTOs ──

data class NavSearchRequest(
    val message: String,
    val lat: Double,
    val lng: Double,
)

data class NavSearchResponse(
    val speech: String = "",
    val candidates: List<NavCandidate> = emptyList(),
    val intent: String = "",
    val language: String = "ko",
)

data class NavCandidate(
    val poi_id: String = "",
    val name: String = "",
    val address: String = "",
    val pns_lat: Double = 0.0,
    val pns_lon: Double = 0.0,
    val recommended: Boolean = false,
)

data class NavRouteRequest(
    val lat: Double,
    val lng: Double,
    val destination: NavDestination,
    val language: String = "ko",
)

data class NavDestination(
    val poi_id: String = "",
    val pns_lat: Double = 0.0,
    val pns_lon: Double = 0.0,
    val name: String = "",
)

data class NavRouteResponse(
    val speech: String = "",
    val ar_command: ArCommand? = null,
)

data class LatLng(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
)

data class RouteDestination(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val name: String = "",
)

data class ArCommand(
    val type: String = "",
    val route_line: List<LatLng> = emptyList(),
    val turn_points: List<TurnPoint> = emptyList(),
    val destination: RouteDestination? = null,
    val total_distance_m: Int = 0,
    val total_time_min: Int = 0,
)

data class TurnPoint(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val turnType: Int = 0,
    val description: String = "",
    val nearPoiName: String = "",
    val intersectionName: String = "",
    val pointType: String = "",
    val facilityType: String = "",
    val segment_distance_m: Int = 0,
    val speech: String = "",
)

// ── Place Insight DTOs ──

data class PlaceQueryRequest(
    val heading: Double = 0.0,
    val user_lat: Double = 0.0,
    val user_lng: Double = 0.0,
    val user_alt: Double = 0.0,
    val pitch: Double = 0.0,
    val user_message: String = "",
    val language: String = "ko",
)

data class PlaceQueryResponse(
    val ar_overlay: ArOverlay? = null,
    val docent: Docent? = null,
)

data class ArOverlay(
    val name: String = "",
    val category: String = "",
    val floor_info: List<FloorInfo> = emptyList(),
    val halal_info: String = "",
    val image_url: String = "",
    val homepage: String = "",
    val open_hours: String = "",
    val closed_days: String = "",
    val parking_info: String = "",
    val admission_fee: String = "",
    val is_estimated: Boolean = false,
)

data class FloorInfo(
    val floor: String = "",
    val stores: List<String> = emptyList(),
)

data class Docent(
    val speech: String = "",
    val follow_up_suggestions: List<String> = emptyList(),
)

data class StoreRequest(
    val place_id: String,
    val store_name: String,
)

data class StoreResponse(
    val store_name: String = "",
    val place_id: String = "",
    val name_ko: String = "",
    val category: String = "",
    val addr: String = "",
    val phone: String = "",
    val place_url: String = "",
)

// ── Convenience DTOs ──

data class ConvenienceRequest(
    val message: String = "",
    val category: String = "",
    val lat: Double = 37.5636,
    val lng: Double = 126.9822,
    val language: String = "ko",
    val radius: Int = 0,
)

data class ConvenienceResponse(
    val speech: String = "",
    val category: String = "",
    val facilities: List<Facility> = emptyList(),
    val language: String = "ko",
)

data class Facility(
    val name: String = "",
    val distance_m: Double = 0.0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val address: String = "",
    val phone: String = "",
    val open_hours: String = "",
    val extra: Map<String, Any> = emptyMap(),
)

// ── Halal DTOs ──

data class HalalRequest(
    val category: String,
    val message: String = "",
    val lat: Double = 37.5636,
    val lng: Double = 126.9822,
    val language: String = "ko",
    val halal_type: String = "",
    val radius: Int = 1000,
)

data class HalalResponse(
    val speech: String = "",
    val category: String = "",
    val language: String = "ko",
    val prayer_times: PrayerTimeData? = null,
    val qibla: QiblaData? = null,
    val restaurants: List<HalalRestaurant> = emptyList(),
    val prayer_rooms: List<PrayerRoomDetail> = emptyList(),
)

data class PrayerTimeData(
    val fajr: String = "",
    val dhuhr: String = "",
    val asr: String = "",
    val maghrib: String = "",
    val isha: String = "",
    val next_prayer: String = "",
    val next_prayer_time: String = "",
    val hijri_date: String = "",
    val gregorian_date: String = "",
)

data class QiblaData(
    val direction: Double = 0.0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
)

data class HalalRestaurant(
    val restaurant_id: String = "",
    val name_ko: String = "",
    val name_en: String = "",
    val halal_type: String = "",
    val muslim_cooks_available: Boolean = false,
    val no_alcohol_sales: Boolean = false,
    val cuisine_type: List<String> = emptyList(),
    val menu_examples: List<MenuExample> = emptyList(),
    val short_description_ko: String = "",
    val distance_m: Int = 0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val address: String = "",
    val cuisine: String = "",
    val phone: String = "",
    val open_hours: String = "",
    val opening_hours: Map<String, String> = emptyMap(),
    val break_time: Map<String, String> = emptyMap(),
    val last_order: String = "",
)

data class MenuExample(
    val name_ko: String = "",
    val name_en: String = "",
    val price_krw: Int = 0,
)

data class PrayerRoomDetail(
    val name: String = "",
    val name_en: String = "",
    val distance_m: Int = 0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val address: String = "",
    val floor: String = "",
    val open_hours: String = "",
    val phone: String = "",
    val gender: String = "",
    val facilities: PrayerFacilities? = null,
    val availability_status: String = "",
    val capacity: Int = 0,
    val notes: String = "",
)

data class PrayerFacilities(
    val wudu: Boolean = false,
    val gender_separation: Boolean = false,
    val prayer_mat: Boolean = false,
    val quran_available: Boolean = false,
)
