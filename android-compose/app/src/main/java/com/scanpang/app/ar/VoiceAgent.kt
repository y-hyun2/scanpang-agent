package com.scanpang.app.ar

/**
 * STT로 얻은 텍스트를 에이전트로 보낼 때 사용.
 * 추후 음성 전용 엔드포인트가 생기면 여기서 [AgentService.sendVoice] 등으로 분기하면 됩니다.
 */
suspend fun sendVoiceMessage(text: String, agentService: AgentService): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return ""
    return agentService.sendMessage(trimmed)
}
