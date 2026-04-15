package com.scanpang.arnavigation.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hufs.arnavigation_com.NavigationState
import com.scanpang.arnavigation.common.NetworkResult
import com.scanpang.arnavigation.data.remote.dto.DestinationInput
import com.scanpang.arnavigation.data.remote.dto.NavRouteResponse
import com.scanpang.arnavigation.domain.repository.RouteRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val repository: RouteRepository) : ViewModel() {

    private val _navigationState = MutableStateFlow(NavigationState.INITIALIZING)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    private val _routeData = MutableStateFlow<Any?>(null)
    val routeData: StateFlow<Any?> = _routeData.asStateFlow()

    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent: SharedFlow<String> = _uiEvent.asSharedFlow()

    fun updateState(newState: NavigationState) {
        _navigationState.value = newState
    }

    /**
     * ScanPang 백엔드를 통한 경로 탐색.
     * 1단계: /navigation/search → LLM이 의도 파악 + 후보 추출
     * 2단계: /navigation/route → TMAP 경로 + LLM TTS 음성 안내
     */
    fun fetchRoute(currentLng: String, currentLat: String, destination: String) {
        viewModelScope.launch {
            val lat = currentLat.toDoubleOrNull() ?: return@launch
            val lng = currentLng.toDoubleOrNull() ?: return@launch

            Log.d("ScanPang_API", "🔍 검색 시작: [$destination] / 좌표: ($lat, $lng)")

            // Step 1: 장소 검색 (LLM 의도 파악)
            val searchResult = repository.searchNavigation(destination, lat, lng)
            val searchData = (searchResult as? NetworkResult.Success)?.data

            if (searchData == null) {
                val errorMsg = (searchResult as? NetworkResult.Error)?.message ?: "검색 실패"
                updateState(NavigationState.LOCALIZING)
                _uiEvent.emit("백엔드 검색 실패: $errorMsg")
                return@launch
            }

            Log.d("ScanPang_API", "검색 결과: ${searchData.candidates.size}개 후보, speech=${searchData.speech}")

            if (searchData.candidates.isEmpty()) {
                updateState(NavigationState.LOCALIZING)
                _uiEvent.emit("목적지를 찾을 수 없습니다. 다른 검색어를 입력해주세요.")
                return@launch
            }

            // recommended 후보 우선, 없으면 첫 번째
            val bestCandidate = searchData.candidates.firstOrNull { it.recommended }
                ?: searchData.candidates[0]

            Log.d("ScanPang_API", "🎯 선택된 목적지: ${bestCandidate.name} (${bestCandidate.pns_lat}, ${bestCandidate.pns_lon})")

            // Step 2: 경로 탐색 (TMAP + LLM TTS)
            val routeResult = repository.getRoute(
                lat = lat,
                lng = lng,
                destination = DestinationInput(
                    poi_id = bestCandidate.poi_id,
                    pns_lat = bestCandidate.pns_lat,
                    pns_lon = bestCandidate.pns_lon,
                    name = bestCandidate.name
                ),
                language = searchData.language
            )

            val routeData = (routeResult as? NetworkResult.Success)?.data

            if (routeData != null && routeData.ar_command != null) {
                Log.d("ScanPang_API", "✅ 경로 수신: ${routeData.ar_command.total_distance_m}m, " +
                        "${routeData.ar_command.turn_points.size}개 턴포인트")
                _routeData.value = routeData
            } else {
                val errorMsg = (routeResult as? NetworkResult.Error)?.message ?: "경로 탐색 실패"
                updateState(NavigationState.LOCALIZING)
                _uiEvent.emit("경로 탐색 실패: $errorMsg")
            }
        }
    }
}
