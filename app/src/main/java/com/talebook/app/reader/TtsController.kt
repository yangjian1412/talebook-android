package com.talebook.app.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

class TtsController(
    context: Context,
    private val onReady: () -> Unit,
    private val onStart: (String) -> Unit,
    private val onDone: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var ready = false
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                if (::tts.isInitialized) {
                    tts.language = Locale.getDefault()
                }
                onReady()
            } else {
                onError("系统 TTS 初始化失败")
            }
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                this@TtsController.onStart(utteranceId)
            }

            override fun onDone(utteranceId: String) {
                this@TtsController.onDone(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) {
                this@TtsController.onError("朗读失败")
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                this@TtsController.onError("朗读失败：$errorCode")
            }
        })
    }

    fun speak(text: String, utteranceId: String, rate: Float, pitch: Float, voiceName: String): Boolean {
        if (!ready || text.isBlank()) return false
        if (voiceName.isNotBlank()) {
            tts.voices?.firstOrNull { it.name == voiceName }?.let { tts.voice = it }
        }
        tts.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        tts.setPitch(pitch.coerceIn(0.5f, 2.0f))
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.SUCCESS
    }

    fun voices(): List<Voice> = if (ready) tts.voices?.toList().orEmpty().sortedBy { it.name } else emptyList()

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
