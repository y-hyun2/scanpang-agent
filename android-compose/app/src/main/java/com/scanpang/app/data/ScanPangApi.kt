package com.scanpang.app.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

// ── API 인터페이스 ──────────────────────────────────────────────────────────

interface ScanPangApiService {
    @POST("halal/query")
    suspend fun halalQuery(@Body request: HalalRequest): HalalResponse

    @POST("place/query")
    suspend fun placeQuery(@Body request: PlaceQueryRequest): PlaceQueryResponse

    @POST("place/store")
    suspend fun placeStore(@Body request: StoreRequest): StoreResponse

    @POST("navigation/search")
    suspend fun navSearch(@Body request: NavSearchRequest): NavSearchResponse

    @POST("navigation/route")
    suspend fun navRoute(@Body request: NavRouteRequest): NavRouteResponse

    @POST("convenience/query")
    suspend fun convenienceQuery(@Body request: ConvenienceRequest): ConvenienceResponse
}

// ── 싱글톤 클라이언트 ───────────────────────────────────────────────────────

object ScanPangClient {
    private const val BASE_URL = "http://10.0.2.2:8000/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: ScanPangApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ScanPangApiService::class.java)
    }
}

// ── Halal Agent DTO ─────────────────────────────────────────────────────────

data class HalalRequest(
    val category: String = "", val message: String = "",
    val lat: Double = 37.5636, val lng: Double = 126.9822,
    val language: String = "ko", val halal_type: String = "", val radius: Int = 0,
)

data class HalalResponse(
    val speech: String, val category: String, val language: String,
    val prayer_times: PrayerTimeData?, val qibla: QiblaData?,
    val restaurants: List<HalalRestaurant>, val prayer_rooms: List<PrayerRoomDetail>,
)

data class PrayerTimeData(
    val fajr: String, val dhuhr: String, val asr: String,
    val maghrib: String, val isha: String,
    val hijri_date: String, val gregorian_date: String,
)

data class QiblaData(val direction: Double, val lat: Double, val lng: Double)

data class HalalRestaurant(
    val restaurant_id: String, val name_ko: String, val name_en: String,
    val halal_type: String, val muslim_cooks_available: Boolean?,
    val no_alcohol_sales: Boolean?, val cuisine_type: List<String>,
    val menu_examples: List<String>, val distance_m: Double,
    val lat: Double, val lng: Double, val address: String,
    val phone: String, val opening_hours: String,
    val break_time: String, val last_order: String,
)

data class PrayerRoomDetail(
    val name: String, val name_en: String, val distance_m: Double,
    val lat: Double, val lng: Double, val address: String,
    val floor: String, val open_hours: String,
    val facilities: Map<String, Boolean>, val availability_status: String,
)

// ── Place Insight Agent DTO ─────────────────────────────────────────────────

data class PlaceQueryRequest(
    val heading: Double, val user_lat: Double, val user_lng: Double,
    val user_alt: Double = 0.0, val pitch: Double = 0.0,
    val user_message: String = "이 건물에 대해 알려줘", val language: String = "ko",
)

data class PlaceQueryResponse(
    val ar_overlay: ArOverlay, val docent: Docent,
)

data class ArOverlay(
    val name: String, val category: String,
    val floor_info: List<FloorInfo>,
    val halal_info: String, val image_url: String,
    val homepage: String, val open_hours: String,
    val closed_days: String, val parking_info: String,
    val admission_fee: String, val is_estimated: Boolean,
)

data class FloorInfo(val floor: String, val stores: List<String>)

data class Docent(val speech: String, val follow_up_suggestions: List<String>)

// ── Store Detail DTO ────────────────────────────────────────────────────────

data class StoreRequest(val place_id: String, val store_name: String)

data class StoreResponse(
    val store_name: String, val place_id: String,
    val name_ko: String, val category: String,
    val addr: String, val phone: String, val place_url: String,
)

// ── Navigation Agent DTO ────────────────────────────────────────────────────

data class NavSearchRequest(val message: String, val lat: Double, val lng: Double)

data class NavSearchResponse(
    val speech: String, val candidates: List<PoiCandidate>,
    val intent: String, val language: String,
)

data class PoiCandidate(
    val poi_id: String, val name: String, val address: String,
    val pns_lat: Double, val pns_lon: Double, val recommended: Boolean,
)

data class NavRouteRequest(
    val lat: Double, val lng: Double,
    val destination: DestinationInput, val language: String = "ko",
)

data class DestinationInput(
    val poi_id: String, val pns_lat: Double, val pns_lon: Double, val name: String,
)

data class NavRouteResponse(
    val speech: String, val ar_command: ArCommand?,
)

data class ArCommand(
    val type: String, val route_line: List<LatLng>,
    val turn_points: List<TurnPoint>, val destination: NavDestination,
    val total_distance_m: Int, val total_time_min: Int,
)

data class LatLng(val lat: Double, val lng: Double)

data class TurnPoint(
    val lat: Double, val lng: Double, val turnType: Int,
    val description: String, val nearPoiName: String,
    val intersectionName: String, val pointType: String,
    val facilityType: String, val segment_distance_m: Int, val speech: String,
)

data class NavDestination(val lat: Double, val lng: Double, val name: String)

// ── Convenience Agent DTO ───────────────────────────────────────────────────

data class ConvenienceRequest(
    val message: String = "", val category: String = "",
    val lat: Double = 37.5636, val lng: Double = 126.9822,
    val language: String = "ko", val radius: Int = 0,
)

data class ConvenienceResponse(
    val speech: String, val category: String,
    val facilities: List<Facility>, val language: String,
)

data class Facility(
    val name: String, val distance_m: Double,
    val lat: Double, val lng: Double,
    val address: String, val phone: String,
    val open_hours: String, val extra: Map<String, Any>,
)
