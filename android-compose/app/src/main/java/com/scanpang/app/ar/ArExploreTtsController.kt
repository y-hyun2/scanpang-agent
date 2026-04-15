package com.scanpang.app.ar

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class ArExploreTtsController(
    context: Context,
    private val onPlayingChange: (Boolean) -> Unit,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false

    fun start() {
        tts = TextToSpeech(appContext) { status ->
            val engine = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val koResult = engine.setLanguage(Locale.KOREAN)
            if (koResult == TextToSpeech.LANG_MISSING_DATA || koResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.language = Locale.ENGLISH
            }
            engine.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post { onPlayingChange(true) }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post { onPlayingChange(false) }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post { onPlayingChange(false) }
                    }
                },
            )
            ready = true
        }
    }

    fun speakIfEnabled(text: String, voiceOn: Boolean) {
        if (!voiceOn || text.isBlank() || !ready) return
        val engine = tts ?: return
        val id = "ar_${System.nanoTime()}"
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() {
        tts?.stop()
        mainHandler.post { onPlayingChange(false) }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
