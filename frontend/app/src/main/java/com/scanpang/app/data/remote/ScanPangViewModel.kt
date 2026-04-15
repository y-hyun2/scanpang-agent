package com.scanpang.app.data.remote

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScanPangViewModel : ViewModel() {

    private val api = RetrofitClient.api

    // ── Loading ──
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    // ── Halal ──
    private val _prayerTimes = MutableStateFlow<PrayerTimeData?>(null)
    val prayerTimes: StateFlow<PrayerTimeData?> = _prayerTimes

    private val _qibla = MutableStateFlow<QiblaData?>(null)
    val qibla: StateFlow<QiblaData?> = _qibla

    private val _restaurants = MutableStateFlow<List<HalalRestaurant>>(emptyList())
    val restaurants: StateFlow<List<HalalRestaurant>> = _restaurants

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

    // ── Convenience ──
    private val _convenienceResult = MutableStateFlow<ConvenienceResponse?>(null)
    val convenienceResult: StateFlow<ConvenienceResponse?> = _convenienceResult

    // ── Halal API ──

    fun loadPrayerTimesAndQibla(lat: Double = 37.5636, lng: Double = 126.9822) {
        viewModelScope.launch {
            try {
                val ptResponse = api.queryHalal(HalalRequest(category = "prayer_time", lat = lat, lng = lng))
                _prayerTimes.value = ptResponse.prayer_times

                val qResponse = api.queryHalal(HalalRequest(category = "qibla", lat = lat, lng = lng))
                _qibla.value = qResponse.qibla
            } catch (e: Exception) {
                Log.e("ScanPangVM", "loadPrayerTimesAndQibla failed", e)
            }
        }
    }

    fun loadRestaurants(lat: Double = 37.5636, lng: Double = 126.9822, halalType: String = "") {
        viewModelScope.launch {
            _loading.value = true
            try {
                val response = api.queryHalal(
                    HalalRequest(category = "restaurant", lat = lat, lng = lng, halal_type = halalType)
                )
                _restaurants.value = response.restaurants
            } catch (e: Exception) {
                Log.e("ScanPangVM", "loadRestaurants failed", e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadPrayerRooms(lat: Double = 37.5636, lng: Double = 126.9822) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val response = api.queryHalal(
                    HalalRequest(category = "prayer_room", lat = lat, lng = lng)
                )
                _prayerRooms.value = response.prayer_rooms
            } catch (e: Exception) {
                Log.e("ScanPangVM", "loadPrayerRooms failed", e)
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Navigation API ──

    fun searchNavigation(message: String, lat: Double, lng: Double) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _navSearchResult.value = api.searchNavigation(
                    NavSearchRequest(message = message, lat = lat, lng = lng)
                )
            } catch (e: Exception) {
                Log.e("ScanPangVM", "searchNavigation failed", e)
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

    fun queryPlace(heading: Double, lat: Double, lng: Double, alt: Double = 0.0, pitch: Double = 0.0, message: String = "") {
        viewModelScope.launch {
            try {
                _placeResult.value = api.queryPlace(
                    PlaceQueryRequest(heading = heading, user_lat = lat, user_lng = lng, user_alt = alt, pitch = pitch, user_message = message)
                )
            } catch (e: Exception) {
                Log.e("ScanPangVM", "queryPlace failed", e)
            }
        }
    }

    fun queryStore(placeId: String, storeName: String) {
        viewModelScope.launch {
            try {
                _storeResult.value = api.queryStore(StoreRequest(place_id = placeId, store_name = storeName))
            } catch (e: Exception) {
                Log.e("ScanPangVM", "queryStore failed", e)
            }
        }
    }

    // ── Convenience API ──

    fun searchConvenience(category: String = "", message: String = "", lat: Double = 37.5636, lng: Double = 126.9822) {
        viewModelScope.launch {
            _loading.value = true
            try {
                _convenienceResult.value = api.queryConvenience(
                    ConvenienceRequest(category = category, message = message, lat = lat, lng = lng)
                )
            } catch (e: Exception) {
                Log.e("ScanPangVM", "searchConvenience failed", e)
            } finally {
                _loading.value = false
            }
        }
    }
}
