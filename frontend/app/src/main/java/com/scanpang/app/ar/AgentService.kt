package com.scanpang.app.ar

import com.scanpang.app.data.remote.AgentChatRequest
import com.scanpang.app.data.remote.NavContext
import com.scanpang.app.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

interface AgentService {
    suspend fun sendMessage(text: String, navContext: NavContext? = null): String
    suspend fun sendVoice(audioData: ByteArray): String
}

class ScanPangAgentService(
    private var lat: Double = 37.5636,
    private var lng: Double = 126.9822,
    private var heading: Double = 0.0,
    private var language: String = "ko",
) : AgentService {

    private val api = RetrofitClient.api
    private var sessionId: String = UUID.randomUUID().toString()

    /** ARCore onSessionUpdated에서 실시간으로 호출해 위치를 갱신 */
    fun updatePosition(lat: Double, lng: Double, heading: Double) {
        this.lat = lat
        this.lng = lng
        this.heading = heading
    }

    fun updateLanguage(language: String) {
        this.language = language
    }

    override suspend fun sendMessage(text: String, navContext: NavContext?): String = withContext(Dispatchers.IO) {
        try {
            val response = api.agentChat(
                AgentChatRequest(
                    message = text,
                    lat = lat,
                    lng = lng,
                    heading = heading,
                    language = language,
                    session_id = sessionId,
                    nav_context = navContext,
                )
            )
            if (response.session_id.isNotEmpty()) sessionId = response.session_id
            response.speech.ifEmpty { "응답을 받지 못했습니다." }
        } catch (e: Exception) {
            "네트워크 오류: ${e.message}"
        }
    }

    override suspend fun sendVoice(audioData: ByteArray): String {
        return sendMessage("음성 입력 (${audioData.size} bytes)")
    }
}
