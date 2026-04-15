package com.scanpang.app.ar

import com.scanpang.app.data.remote.PlaceQueryRequest
import com.scanpang.app.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AR 탐색/길안내 화면에서 사용하는 에이전트 서비스.
 * 백엔드 /place/query 엔드포인트와 연동.
 */
interface AgentService {
    suspend fun sendMessage(text: String): String
    suspend fun sendVoice(audioData: ByteArray): String
}

class ScanPangAgentService(
    private val lat: Double = 37.5636,
    private val lng: Double = 126.9822,
    private val heading: Double = 0.0,
) : AgentService {

    private val api = RetrofitClient.api

    override suspend fun sendMessage(text: String): String = withContext(Dispatchers.IO) {
        try {
            val response = api.queryPlace(
                PlaceQueryRequest(
                    heading = heading,
                    user_lat = lat,
                    user_lng = lng,
                    user_message = text,
                )
            )
            response.docent?.speech ?: "응답을 받지 못했습니다."
        } catch (e: Exception) {
            "네트워크 오류: ${e.message}"
        }
    }

    override suspend fun sendVoice(audioData: ByteArray): String {
        return sendMessage("음성 입력 (${audioData.size} bytes)")
    }
}
