package com.scanpang.app.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

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

    // ── Orchestrator ──
    @POST("ar/agent/chat")
    suspend fun agentChat(@Body request: AgentChatRequest): AgentChatResponse

    // ── Restaurant Detail ──
    @POST("restaurant/detail")
    suspend fun getRestaurantDetail(@Body request: RestaurantDetailRequest): GeneralRestaurantDetail

    // ── Search ──
    @POST("place/search")
    suspend fun searchPlaces(@Body request: SearchRequest): SearchResponse

    // ── Place Detail ──
    @POST("place/detail")
    suspend fun getPlaceDetail(@Body request: PlaceDetailRequest): PlaceDetailResponse

    // ── Spatial: H3 청크 단위 건물 ──
    @GET("buildings")
    suspend fun getBuildings(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
    ): BuildingsChunkResponse

    // ── User Preferences ──
    // 온보딩 완료 / 프로필 수정 시 호출. user_id 는 Supabase auth.users.id.
    @POST("user/preferences")
    suspend fun upsertUserPreferences(@Body request: UserPreferencesUpsertRequest): UserPreferencesResponse

    @GET("user/preferences/{user_id}")
    suspend fun getUserPreferences(@retrofit2.http.Path("user_id") userId: String): UserPreferencesResponse
}

data class UserPreferencesUpsertRequest(
    val user_id: String,
    val display_name: String? = null,
    val language: String? = null,
    val value_added: String? = null,
)

data class UserPreferencesResponse(
    val user_id: String = "",
    val display_name: String? = null,
    val language: String? = null,
    val value_added: String? = null,
    val saved_places: List<Any> = emptyList(),
    val search_history: List<Any> = emptyList(),
)

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
    val ufid: String? = null,
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
    // AR 건물 floating 패널의 chip 표시용 — 주소·전화 chip 렌더링.
    val address: String = "",
    val phone: String = "",
    // store_details cache key 의 building 파트. 층별 매장 탭 → /place/store
    // 호출 시 place_id 로 전달해야 store_details row 의 place_id 가 일관됨.
    val ufid: String = "",
    val is_estimated: Boolean = false,
)

data class StoreItem(
    val name: String = "",
    val category: String = "",
)

data class FloorInfo(
    val floor: String = "",
    val stores: List<StoreItem> = emptyList(),
)

data class Docent(
    val speech: String = "",
    val follow_up_suggestions: List<String> = emptyList(),
)

data class StoreRequest(
    val place_id: String,
    val store_name: String,
)

/**
 * `POST /place/store` 응답 — AR 마커 탭 시 뜨는 작은 카드 데이터.
 * 백엔드 `tools/store_tools.py::get_store_detail()` 반환 dict와 1:1 매핑.
 *
 * `details` 안에 카테고리별 가변 필드(메뉴/지하철 출구/화장실 칸 수 등)가
 * 그대로 들어옴 — 매핑 가이드: §4.1 (Notion store_details 페이지) 참고.
 * 매핑 예: [com.scanpang.app.data.toSubwayDetail] (subway 카테고리).
 */
data class StoreResponse(
    val id: String = "",                              // "{place_id}__{store_name}"
    val store_name: String = "",
    val place_id: String = "",
    val name_ko: String = "",
    val category: String = "",
    val category_key: String? = null,                 // cafe/restaurant/subway/restroom/...
    val addr: String = "",
    val phone: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val place_url: String = "",
    val floor: String? = null,                        // "B1", "1F"
    val homepage: String? = null,                     // 공식 홈페이지 (소셜 URL은 백엔드가 필터)
    val open_hours: String? = null,                   // "매일 11:00~22:00" 형식
    val closed_days: String? = null,
    val is_open_now: Boolean? = null,                 // 백엔드 계산 (b55f1e5). null=판정 불가
    val image_urls: List<String> = emptyList(),       // 1~5장 (Naver Place 출처)
    val details: Map<String, Any> = emptyMap(),       // 카테고리별 자유형 — Map 캐스팅 후 사용
    val source: String? = null,                       // "naver_place"/"kakao"/"tago_subway" 등
    val last_updated: String? = null,
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
    val muslim_cooks_available: Boolean? = null,
    val no_alcohol_sales: Boolean? = null,
    val cuisine_type: List<String> = emptyList(),
    val menu_examples: List<String> = emptyList(),
    val short_description_ko: String = "",
    val distance_m: Double = 0.0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val address: String = "",
    val phone: String = "",
    val opening_hours: String = "",
    val break_time: String = "",
    val last_order: String = "",
)

data class PrayerRoomDetail(
    val name: String = "",
    val name_en: String = "",
    val distance_m: Double = 0.0,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val address: String = "",
    val floor: String = "",
    val open_hours: String = "",
    val facilities: Map<String, Any> = emptyMap(),
    val availability_status: String = "unknown",
)

// ── Orchestrator DTOs ──

data class AgentChatRequest(
    val message: String,
    val lat: Double,
    val lng: Double,
    val heading: Double = 0.0,
    val language: String = "ko",
    val session_id: String? = null,
)

data class AgentChatResponse(
    val speech: String = "",
    val source_agent: String = "",
    val raw_data: Map<String, Any> = emptyMap(),
    val session_id: String = "",
)

// ── Restaurant Detail DTOs ──

data class RestaurantDetailRequest(
    val name: String,
)

data class GeneralRestaurantMenu(
    val name_ko: String = "",
    val name_en: String = "",
    val price_krw: Int = 0,
)

data class GeneralRestaurantDetail(
    val restaurant_id: String = "",
    val name_ko: String = "",
    val name_en: String = "",
    val cuisine_type: List<String> = emptyList(),
    val food_keywords: List<String> = emptyList(),
    val menu_examples: List<GeneralRestaurantMenu> = emptyList(),
    val short_description_ko: String = "",
    val address: String = "",
    val phone: String = "",
    val opening_hours: String = "",
    val break_time: String = "",
    val last_order: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val source: String = "general",
    val is_open_today: Boolean = true,
)

// ── Search DTOs ──

data class SearchRequest(
    val query: String,
    val limit: Int = 50,
    // outdoor 카테고리(화장실/지하철역/물품보관함/기도실) 자동 라우팅 시 거리 정렬용.
    // 빈 값이면 백엔드가 명동 기본 좌표로 fallback.
    val lat: Double? = null,
    val lng: Double? = null,
)

data class SearchResultItem(
    val id: String = "",
    val store_name: String = "",
    val category: String? = null,
    val category_key: String? = null,
    val addr: String? = null,
    val phone: String? = null,
    val place_id: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val floor: String? = null,
    val image_url: String? = null,
    val is_open_now: Boolean? = null,
    val distance_m: Double? = null,  // outdoor 카테고리 검색 시 거리(m). 그 외 null
    val halal_type: String? = null,  // 할랄 식당 전용 (HALAL MEAT / SEAFOOD / VEGGIE / SALAM SEOUL)
)

data class SearchResponse(
    val query: String = "",
    val count: Int = 0,
    val results: List<SearchResultItem> = emptyList(),
)

// ── Place Detail DTOs ──

data class PlaceDetailRequest(
    val id: String,
)

data class PlaceDetailResponse(
    val id: String = "",
    val store_name: String = "",
    val place_id: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val category: String? = null,
    val category_key: String? = null,
    val addr: String? = null,
    val phone: String? = null,
    val floor: String? = null,
    val homepage: String? = null,
    val place_url: String? = null,
    val open_hours: String? = null,
    val closed_days: String? = null,
    val is_open_now: Boolean? = null,
    val image_urls: List<String> = emptyList(),
    // 카테고리별 자유형 dict — 메뉴/환율/진료과목 등.
    // Gson 디폴트로 LinkedTreeMap 디코드 → 사용처에서 캐스팅.
    val details: Map<String, Any> = emptyMap(),
    val source: String? = null,
    val last_updated: String? = null,
)

// ── Spatial DTOs ──

data class GeoJsonMultiPolygon(
    val type: String = "MultiPolygon",
    val coordinates: List<List<List<List<Double>>>> = emptyList(),  // [polygons][rings][points][lng, lat]
)

data class Building(
    val ufid: String = "",
    val bld_nm: String? = null,
    val render_height: Double = 0.0,
    val h3_index_10: String = "",
    val geom: GeoJsonMultiPolygon = GeoJsonMultiPolygon(),
)

data class BuildingsChunkResponse(
    val center_cell: String = "",
    val cells_queried: List<String> = emptyList(),
    val count: Int = 0,
    val buildings: List<Building> = emptyList(),
)