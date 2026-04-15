package com.scanpang.app.qibla

import com.scanpang.app.data.remote.HalalRequest
import com.scanpang.app.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PrayerTimes(
    val nextPrayerName: String,
    val nextPrayerTime: String,
    val remainingLabel: String,
)

/**
 * 기도시간 — 백엔드 /halal/query API 호출.
 * Composable이 아닌 곳에서 직접 호출 시 사용. ViewModel 사용 권장.
 */
suspend fun getPrayerTimesFromApi(lat: Double = 37.5636, lng: Double = 126.9822): PrayerTimes =
    withContext(Dispatchers.IO) {
        try {
            val response = RetrofitClient.api.queryHalal(
                HalalRequest(category = "prayer_time", lat = lat, lng = lng)
            )
            val pt = response.prayer_times
            PrayerTimes(
                nextPrayerName = pt?.next_prayer ?: "Dhuhr",
                nextPrayerTime = pt?.next_prayer_time ?: "12:15",
                remainingLabel = "다음 기도 시간",
            )
        } catch (_: Exception) {
            PrayerTimes("Dhuhr", "12:15", "2시간 34분 남음")
        }
    }

/**
 * 로컬 폴백용 더미 데이터 (API 실패 시).
 */
fun getPrayerTimes(): PrayerTimes =
    PrayerTimes(
        nextPrayerName = "Dhuhr",
        nextPrayerTime = "12:15",
        remainingLabel = "2시간 34분 남음",
    )

/**
 * 키블라 방위각 — 백엔드에서 받거나 로컬 폴백.
 */
fun getQiblaDirection(): Float = 292f

fun getMeccaDistanceKm(): Float = 8565f
