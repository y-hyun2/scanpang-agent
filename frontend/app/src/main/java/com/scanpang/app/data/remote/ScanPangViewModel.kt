package com.scanpang.app.data.remote

import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScanPangViewModel : ViewModel() {

    private val api = RetrofitClient.api

    init {
        Log.d("ScanPangVM", "ViewModel CREATED, BASE_URL=${com.scanpang.app.BuildConfig.SERVER_URL}")
    }

    // ── Loading ──
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    // ── 사용자 현재 위치 — HomeScreen 이 GPS 받으면 setUserLocation() 으로 갱신.
    // SearchDefaultScreen 등 다른 화면이 outdoor 카테고리 거리 검색 시 참조.
    private val _userLat = MutableStateFlow<Double?>(null)
    val userLat: StateFlow<Double?> = _userLat
    private val _userLng = MutableStateFlow<Double?>(null)
    val userLng: StateFlow<Double?> = _userLng

    fun setUserLocation(lat: Double, lng: Double) {
        _userLat.value = lat
        _userLng.value = lng
    }

    // ── Halal ──
    private val _prayerTimes = MutableStateFlow<PrayerTimeData?>(null)
    val prayerTimes: StateFlow<PrayerTimeData?> = _prayerTimes

    private val _qibla = MutableStateFlow<QiblaData?>(null)
    val qibla: StateFlow<QiblaData?> = _qibla

    private val _restaurants = MutableStateFlow<List<HalalRestaurant>>(emptyList())
    val restaurants: StateFlow<List<HalalRestaurant>> = _restaurants

    private val _generalRestaurant = MutableStateFlow<GeneralRestaurantDetail?>(null)
    val generalRestaurant: StateFlow<GeneralRestaurantDetail?> = _generalRestaurant

    private val _prayerRooms = MutableStateFlow<List<PrayerRoomDetail>>(emptyList())
    val prayerRooms: StateFlow<List<PrayerRoomDetail>> = _prayerRooms

    // ── Navigation ──
    private val _navSearchResult = MutableStateFlow<NavSearchResponse?>(null)
    val navSearchResult: StateFlow<NavSearchResponse?> = _navSearchResult

    private val _navRouteResult = MutableStateFlow<NavRouteResponse?>(null)
    val navRouteResult: StateFlow<NavRouteResponse?> = _navRouteResult

    // ── Place Insight ──
    private val _placeResult = MutableStateFlow<PlaceQueryResponse?>(null)
    val placeResult: StateFlow<PlaceQueryResponse?> = _placeResult

    private val _storeResult = MutableStateFlow<StoreResponse?>(null)
    val storeResult: StateFlow<StoreResponse?> = _storeResult

    private val _storeLoadingAt = MutableStateFlow<Long?>(null)
    val storeLoadingAt: StateFlow<Long?> = _storeLoadingAt

    private val _buildingLoadingAt = MutableStateFlow<Long?>(null)
    val buildingLoadingAt: StateFlow<Long?> = _buildingLoadingAt

    // ── Convenience ──
    private val _convenienceResult = MutableStateFlow<ConvenienceResponse?>(null)
    val convenienceResult: StateFlow<ConvenienceResponse?> = _convenienceResult

    // ── Search ──
    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults

    // ── Autocomplete ──
    private val _autocompleteSuggestions = MutableStateFlow<List<String>>(emptyList())
    val autocompleteSuggestions: StateFlow<List<String>> = _autocompleteSuggestions.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // ── Place Detail ──
    private val _placeDetail = MutableStateFlow<PlaceDetailResponse?>(null)
    val placeDetail: StateFlow<PlaceDetailResponse?> = _placeDetail

    /**
     * 마지막으로 요청한 detail (id, lat, lng) — 같은 화면 진입 시 중복 호출 회피용.
     * lat/lng 도 포함해야 첫 호출(userLoc=null) 후 GPS 가 collect 됐을 때 재호출이
     * 캐시에 막히지 않음 — 그 케이스가 distance_m null 의 원인이었다.
     */
    private var _lastPlaceDetailKey: Triple<String, Double?, Double?>? = null


    // ── Spatial: H3 청크 ──
    private val _buildingsChunk = MutableStateFlow<List<Building>>(emptyList())
    val buildingsChunk: StateFlow<List<Building>> = _buildingsChunk

    private val _currentH3Cell = MutableStateFlow<String?>(null)
    val currentH3Cell: StateFlow<String?> = _currentH3Cell

    private val _buildingsCache = MutableStateFlow<Map<String, Building>>(emptyMap())
    val buildingsCache: StateFlow<Map<String, Building>> = _buildingsCache.asStateFlow()

    // ── Halal API ──

    fun loadPrayerTimesAndQibla(lat: Double = 37.5636, lng: Double = 126.9822) {
        viewModelScope.launch {
            Log.d("ScanPangVM", "loadPrayerTimesAndQibla START (lat=$lat, lng=$lng)")
            try {
                val ptResponse = api.queryHalal(HalalRequest(category = "prayer_time", lat = lat, lng = lng))
                Log.d("ScanPangVM", "loadPrayerTimesAndQibla prayer_times=${ptResponse.prayer_times}")
                _prayerTimes.value = ptResponse.prayer_times

                val qResponse = api.queryHalal(HalalRequest(category = "qibla", lat = lat, lng = lng))
                Log.d("ScanPangVM", "loadPrayerTimesAndQibla qibla=${qResponse.qibla}")
                _qibla.value = qResponse.qibla
            } catch (e: Exception) {
                Log.e("ScanPangVM", "loadPrayerTimesAndQibla FAILED", e)
            }
        }
    }

    fun loadRestaurants(lat: Double = 37.5636, lng: Double = 126.9822, halalType: String = "") {
        viewModelScope.launch {
            _loading.value = true
            Log.d("ScanPangVM", "loadRestaurants START (lat=$lat, lng=$lng, type=$halalType)")
            try {
                val response = api.queryHalal(
                    HalalRequest(category = "restaurant", lat = lat, lng = lng, halal_type = halalType)
                )
                Log.d("ScanPangVM", "loadRestaurants OK: ${response.restaurants.size} restaurants")
                _restaurants.value = response.restaurants
            } catch (e: Exception) {
                Log.e("ScanPangVM", "loadRestaurants FAILED", e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadGeneralRestaurant(name: String) {
        viewModelScope.launch {
            _loading.value = true
            _generalRestaurant.value = null
            Log.d("ScanPangVM", "loadGeneralRestaurant START (name=$name)")
            try {
                _generalRestaurant.value = api.getRestaurantDetail(RestaurantDetailRequest(name = name))
                Log.d("ScanPangVM", "loadGeneralRestaurant OK: ${_generalRestaurant.value?.name_ko}")
            } catch (e: Exception) {
                Log.e("ScanPangVM", "loadGeneralRestaurant FAILED", e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadPrayerRooms(lat: Double = 37.5636, lng: Double = 126.9822) {
        viewModelScope.launch {
            _loading.value = true
            Log.d("ScanPangVM", "loadPrayerRooms START (lat=$lat, lng=$lng)")
            try {
                val response = api.queryHalal(
                    HalalRequest(category = "prayer_room", lat = lat, lng = lng)
                )
                Log.d("ScanPangVM", "loadPrayerRooms OK: ${response.prayer_rooms.size} rooms")
                _prayerRooms.value = response.prayer_rooms
            } catch (e: Exception) {
                Log.e("ScanPangVM", "loadPrayerRooms FAILED", e)
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Navigation API ──

    fun searchNavigation(message: String, lat: Double, lng: Double) {
        viewModelScope.launch {
            _loading.value = true
            Log.d("ScanPangVM", "searchNavigation START (msg=$message)")
            try {
                _navSearchResult.value = api.searchNavigation(
                    NavSearchRequest(message = message, lat = lat, lng = lng)
                )
                Log.d("ScanPangVM", "searchNavigation OK: ${_navSearchResult.value?.candidates?.size} candidates")
            } catch (e: Exception) {
                Log.e("ScanPangVM", "searchNavigation FAILED", e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun getRoute(lat: Double, lng: Double, destination: NavDestination) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _navRouteResult.value = api.getRoute(
                    NavRouteRequest(lat = lat, lng = lng, destination = destination)
                )
            } catch (e: Exception) {
                Log.e("ScanPangVM", "getRoute failed", e)
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Place Insight API ──

    fun queryPlace(heading: Double, lat: Double, lng: Double, alt: Double = 0.0, pitch: Double = 0.0, message: String = "", ufid: String? = null) {
        setUserLocation(lat, lng)
        viewModelScope.launch {
            _buildingLoadingAt.value = System.currentTimeMillis()
            try {
                _placeResult.value = api.queryPlace(
                    PlaceQueryRequest(heading = heading, user_lat = lat, user_lng = lng, user_alt = alt, pitch = pitch, user_message = message, ufid = ufid)
                )
            } catch (e: Exception) {
                Log.e("ScanPangVM", "queryPlace failed", e)
            } finally {
                _buildingLoadingAt.value = null
            }
        }
    }

    fun queryStore(placeId: String, storeName: String) {
        viewModelScope.launch {
            _storeLoadingAt.value = System.currentTimeMillis()
            try {
                _storeResult.value = api.queryStore(StoreRequest(place_id = placeId, store_name = storeName))
            } catch (e: Exception) {
                Log.e("ScanPangVM", "queryStore failed", e)
            } finally {
                _storeLoadingAt.value = null
            }
        }
    }

    // ── Convenience API ──

    fun searchConvenience(category: String = "", message: String = "", lat: Double = 37.5636, lng: Double = 126.9822) {
        viewModelScope.launch {
            _loading.value = true
            Log.d("ScanPangVM", "searchConvenience START (cat=$category)")
            try {
                _convenienceResult.value = api.queryConvenience(
                    ConvenienceRequest(category = category, message = message, lat = lat, lng = lng)
                )
                Log.d("ScanPangVM", "searchConvenience OK: ${_convenienceResult.value?.facilities?.size} facilities")
            } catch (e: Exception) {
                Log.e("ScanPangVM", "searchConvenience FAILED", e)
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Search API ──

    /**
     * @param lat,lng outdoor 카테고리(화장실/지하철역/물품보관함/기도실) 검색 시 거리 정렬에 사용.
     *               null 이면 백엔드가 명동 기본 좌표로 fallback.
     */
    fun searchPlaces(
        query: String,
        lat: Double? = null,
        lng: Double? = null,
        limit: Int = 50,
        language: String = "ko",
    ) {
        _searchQuery.value = query
        if (lat != null && lng != null) setUserLocation(lat, lng)
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _loading.value = true
            Log.d("ScanPangVM", "searchPlaces START (q=$trimmed, lat=$lat, lng=$lng, lang=$language)")
            try {
                val response = api.searchPlaces(
                    SearchRequest(query = trimmed, limit = limit, lat = lat, lng = lng, language = language)
                )
                Log.d("ScanPangVM", "searchPlaces OK: ${response.count} results")
                _searchResults.value = response.results
            } catch (e: Exception) {
                Log.e("ScanPangVM", "searchPlaces FAILED", e)
                _searchResults.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    fun fetchAutocomplete(q: String, lat: Double? = null, lng: Double? = null) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) {
            _autocompleteSuggestions.value = emptyList()
            return
        }
        viewModelScope.launch {
            runCatching {
                api.autocomplete(AutocompleteRequest(q = trimmed, lat = lat, lng = lng))
            }.onSuccess { res ->
                _autocompleteSuggestions.value = res.suggestions
            }.onFailure {
                Log.e("ScanPangVM", "fetchAutocomplete FAILED", it)
                _autocompleteSuggestions.value = emptyList()
            }
        }
    }

    fun clearAutocomplete() {
        _autocompleteSuggestions.value = emptyList()
    }

    /**
     * 통합 PlaceDetailScreen 에서 backend store_details row 를 가져온다.
     * 빈 id 면 호출 안 함 — Phase A 시절 NearbyHalal 등에서 id 없이 진입한 경우
     * 화면이 DummyData 폴백으로 동작.
     */
    fun loadPlaceDetail(
        id: String,
        userLat: Double? = null,
        userLng: Double? = null,
        language: String = "ko",
    ) {
        if (id.isBlank()) {
            _placeDetail.value = null
            _lastPlaceDetailKey = null
            return
        }
        val key = Triple(id, userLat, userLng)
        if (key == _lastPlaceDetailKey && _placeDetail.value != null) return
        _lastPlaceDetailKey = key
        viewModelScope.launch {
            _loading.value = true
            Log.d("ScanPangVM", "loadPlaceDetail START (id=$id, lang=$language)")
            try {
                _placeDetail.value = api.getPlaceDetail(
                    PlaceDetailRequest(id = id, user_lat = userLat, user_lng = userLng, language = language)
                )
                Log.d("ScanPangVM", "loadPlaceDetail OK: ${_placeDetail.value?.store_name}")
            } catch (e: Exception) {
                Log.e("ScanPangVM", "loadPlaceDetail FAILED for id=$id", e)
                _placeDetail.value = null
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearPlaceDetail() {
        _placeDetail.value = null
        _lastPlaceDetailKey = null
    }

    /**
     * outdoor 카테고리(subway/restroom/locker) 전용 detail 로더.
     * store_details 테이블에 row 가 없어 /place/detail 이 404 → /convenience/query 결과에서 name 매칭.
     *
     * @param placeId  `__outdoor__{category}__{name}` 형태. name 파싱용
     * @param apiCategory  convenience API category 키 (subway / restroom / locker)
     */
    fun loadOutdoorPlaceDetail(
        placeId: String,
        apiCategory: String,
        userLat: Double? = null,
        userLng: Double? = null,
    ) {
        if (placeId.isBlank()) {
            _placeDetail.value = null
            _lastPlaceDetailKey = null
            return
        }
        val key = Triple(placeId, userLat, userLng)
        if (key == _lastPlaceDetailKey && _placeDetail.value != null) return
        _lastPlaceDetailKey = key

        val targetName = placeId.substringAfterLast("__").trim()
        val lat = userLat ?: 37.5636
        val lng = userLng ?: 126.9822

        viewModelScope.launch {
            _loading.value = true
            Log.d("ScanPangVM", "loadOutdoorPlaceDetail START (id=$placeId, cat=$apiCategory, name=$targetName)")
            try {
                val resp = api.queryConvenience(
                    ConvenienceRequest(category = apiCategory, lat = lat, lng = lng)
                )
                val match = resp.facilities.firstOrNull { it.name == targetName }
                    ?: resp.facilities.firstOrNull { it.name.contains(targetName) || targetName.contains(it.name) }
                if (match == null) {
                    Log.w("ScanPangVM", "loadOutdoorPlaceDetail: no match for name=$targetName in ${resp.facilities.size} facilities")
                    _placeDetail.value = null
                    return@launch
                }
                _placeDetail.value = PlaceDetailResponse(
                    id = placeId,
                    store_name = match.name,
                    place_id = "__outdoor__",
                    lat = match.lat,
                    lng = match.lng,
                    distance_m = match.distance_m,
                    category = apiCategory,
                    category_key = apiCategory,
                    addr = match.address,
                    phone = match.phone,
                    open_hours = match.open_hours,
                    details = match.extra,
                    source = "convenience",
                )
                Log.d("ScanPangVM", "loadOutdoorPlaceDetail OK: ${match.name}")
            } catch (e: Exception) {
                Log.e("ScanPangVM", "loadOutdoorPlaceDetail FAILED for id=$placeId", e)
                _placeDetail.value = null
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Spatial API ──

    private var lastFetchLat: Double = 0.0
    private var lastFetchLng: Double = 0.0

    fun updateLocationForChunk(lat: Double, lng: Double) {
        setUserLocation(lat, lng)
        // 50m 미만 이동 시 페치 스킵 (네트워크 절약)
        if (lastFetchLat != 0.0) {
            val r = FloatArray(1)
            Location.distanceBetween(lastFetchLat, lastFetchLng, lat, lng, r)
            if (r[0] < 50f) return
        }
        lastFetchLat = lat
        lastFetchLng = lng

        viewModelScope.launch {
            try {
                Log.d("ScanPangVM", "fetchBuildings START lat=$lat, lng=$lng")
                val response = api.getBuildings(lat, lng)

                // [핵심] 누적 캐시 — 셀 바뀌어도 기존 건물 보존, 새 거 추가
                val newCache = _buildingsCache.value.toMutableMap()
                response.buildings.forEach { b ->
                    val key = b.ufid ?: "${b.h3_index_10}_${b.hashCode()}"
                    newCache[key] = b
                }
                _buildingsCache.value = newCache

                _buildingsChunk.value = response.buildings    // 호환용 — 다른 곳서 쓸 수 있음
                _currentH3Cell.value = response.center_cell

                val namedCount = response.buildings.count { it.bld_nm != null }
                Log.d("ScanPangVM", buildString {
                    append("✓ H3 청크 페치 완료\n")
                    append("   이번 페치: ${response.count}개 (이름 있음: ${namedCount}개)\n")
                    append("   누적 캐시 총 ${newCache.size}개\n")
                    append("   중심 셀: ${response.center_cell}")
                })
            } catch (e: Exception) {
                Log.e("ScanPangVM", "fetchBuildings FAILED for lat=$lat, lng=$lng", e)
            }
        }
    }

}
