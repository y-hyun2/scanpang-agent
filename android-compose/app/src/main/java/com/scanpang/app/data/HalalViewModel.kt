package com.scanpang.app.data

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 전체 백엔드 API를 관리하는 ViewModel.
 * Halal, Place Insight, Navigation, Convenience 전부 포함.
 */
class HalalViewModel : ViewModel() {

    // ── Halal: 기도 시간 / 키블라 ────────────────────────────────────────────
    private val _prayerTimes = MutableStateFlow<PrayerTimeData?>(null)
    val prayerTimes: StateFlow<PrayerTimeData?> = _prayerTimes.asStateFlow()

    private val _qibla = MutableStateFlow<QiblaData?>(null)
    val qibla: StateFlow<QiblaData?> = _qibla.asStateFlow()

    // ── Halal: 식당 / 기도실 ────────────────────────────────────────────────
    private val _restaurants = MutableStateFlow<List<HalalRestaurant>>(emptyList())
    val restaurants: StateFlow<List<HalalRestaurant>> = _restaurants.asStateFlow()

    private val _prayerRooms = MutableStateFlow<List<PrayerRoomDetail>>(emptyList())
    val prayerRooms: StateFlow<List<PrayerRoomDetail>> = _prayerRooms.asStateFlow()

    // ── Place Insight: 건물 인식 ────────────────────────────────────────────
    private val _placeResult = MutableStateFlow<PlaceQueryResponse?>(null)
    val placeResult: StateFlow<PlaceQueryResponse?> = _placeResult.asStateFlow()

    private val _storeResult = MutableStateFlow<StoreResponse?>(null)
    val storeResult: StateFlow<StoreResponse?> = _storeResult.asStateFlow()

    // ── Navigation: 길찾기 ──────────────────────────────────────────────────
    private val _navSearchResult = MutableStateFlow<NavSearchResponse?>(null)
    val navSearchResult: StateFlow<NavSearchResponse?> = _navSearchResult.asStateFlow()

    private val _navRouteResult = MutableStateFlow<NavRouteResponse?>(null)
    val navRouteResult: StateFlow<NavRouteResponse?> = _navRouteResult.asStateFlow()

    // ── Convenience: 편의시설 ───────────────────────────────────────────────
    private val _convenienceResult = MutableStateFlow<ConvenienceResponse?>(null)
    val convenienceResult: StateFlow<ConvenienceResponse?> = _convenienceResult.asStateFlow()

    // ── 공통 ────────────────────────────────────────────────────────────────
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { loadPrayerTimesAndQibla() }

    // ── Halal 기능 ──────────────────────────────────────────────────────────

    fun loadPrayerTimesAndQibla() {
        viewModelScope.launch {
            try {
                _prayerTimes.value = ScanPangClient.api.halalQuery(
                    HalalRequest(category = "prayer_time")
                ).prayer_times
                _qibla.value = ScanPangClient.api.halalQuery(
                    HalalRequest(category = "qibla")
                ).qibla
            } catch (e: Exception) {
                Log.e("ScanPangVM", "기도시간/키블라 로드 실패: ${e.message}")
            }
        }
    }

    fun loadRestaurants(halalType: String = "") {
        viewModelScope.launch {
            _loading.value = true
            try {
                _restaurants.value = ScanPangClient.api.halalQuery(
                    HalalRequest(category = "restaurant", halal_type = halalType)
                ).restaurants
            } catch (e: Exception) {
                Log.e("ScanPangVM", "식당 로드 실패: ${e.message}")
            }
            _loading.value = false
        }
    }

    fun loadPrayerRooms() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _prayerRooms.value = ScanPangClient.api.halalQuery(
                    HalalRequest(category = "prayer_room")
                ).prayer_rooms
            } catch (e: Exception) {
                Log.e("ScanPangVM", "기도실 로드 실패: ${e.message}")
            }
            _loading.value = false
        }
    }

    // ── Place Insight 기능 ──────────────────────────────────────────────────

    fun queryPlace(heading: Double, lat: Double, lng: Double, alt: Double = 0.0, pitch: Double = 0.0) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _placeResult.value = ScanPangClient.api.placeQuery(
                    PlaceQueryRequest(heading = heading, user_lat = lat, user_lng = lng, user_alt = alt, pitch = pitch)
                )
                Log.d("ScanPangVM", "건물 인식: ${_placeResult.value?.ar_overlay?.name}, 층별: ${_placeResult.value?.ar_overlay?.floor_info?.size}개")
            } catch (e: Exception) {
                Log.e("ScanPangVM", "건물 인식 실패: ${e.message}")
            }
            _loading.value = false
        }
    }

    fun queryStore(placeId: String, storeName: String) {
        viewModelScope.launch {
            try {
                _storeResult.value = ScanPangClient.api.placeStore(
                    StoreRequest(place_id = placeId, store_name = storeName)
                )
            } catch (e: Exception) {
                Log.e("ScanPangVM", "매장 조회 실패: ${e.message}")
            }
        }
    }

    // ── Navigation 기능 ─────────────────────────────────────────────────────

    fun searchNavigation(message: String, lat: Double, lng: Double) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _navSearchResult.value = ScanPangClient.api.navSearch(
                    NavSearchRequest(message = message, lat = lat, lng = lng)
                )
                Log.d("ScanPangVM", "길찾기 검색: ${_navSearchResult.value?.candidates?.size}개 후보")
            } catch (e: Exception) {
                Log.e("ScanPangVM", "길찾기 검색 실패: ${e.message}")
            }
            _loading.value = false
        }
    }

    fun getRoute(lat: Double, lng: Double, candidate: PoiCandidate, language: String = "ko") {
        viewModelScope.launch {
            _loading.value = true
            try {
                _navRouteResult.value = ScanPangClient.api.navRoute(
                    NavRouteRequest(
                        lat = lat, lng = lng, language = language,
                        destination = DestinationInput(
                            poi_id = candidate.poi_id, pns_lat = candidate.pns_lat,
                            pns_lon = candidate.pns_lon, name = candidate.name,
                        )
                    )
                )
                Log.d("ScanPangVM", "경로: ${_navRouteResult.value?.ar_command?.total_distance_m}m")
            } catch (e: Exception) {
                Log.e("ScanPangVM", "경로 탐색 실패: ${e.message}")
            }
            _loading.value = false
        }
    }

    // ── Convenience 기능 ────────────────────────────────────────────────────

    fun searchConvenience(category: String = "", message: String = "") {
        viewModelScope.launch {
            _loading.value = true
            try {
                _convenienceResult.value = ScanPangClient.api.convenienceQuery(
                    ConvenienceRequest(category = category, message = message)
                )
            } catch (e: Exception) {
                Log.e("ScanPangVM", "편의시설 검색 실패: ${e.message}")
            }
            _loading.value = false
        }
    }
}
