package com.scanpang.app.ar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class ArSpeechRecognizerHelper(
    context: Context,
    private val onListeningChange: (Boolean) -> Unit,
    private val onResult: (String) -> Unit,
    private val onErrorCode: (Int) -> Unit,
) {
    private val appContext = context.applicationContext
    private val recognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)

    init {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onListeningChange(true)
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                onListeningChange(false)
            }

            override fun onError(error: Int) {
                onListeningChange(false)
                if (error != SpeechRecognizer.ERROR_CLIENT) {
                    onErrorCode(error)
                }
            }

            override fun onResults(results: Bundle?) {
                onListeningChange(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim().orEmpty()
                if (text.isNotEmpty()) onResult(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun isRecognitionAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(appContext)

    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        recognizer.startListening(intent)
    }

    fun stopListening() {
        recognizer.stopListening()
        onListeningChange(false)
    }

    fun destroy() {
        recognizer.destroy()
    }
}
