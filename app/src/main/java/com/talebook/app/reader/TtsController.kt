package com.talebook.app.reader

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

class TtsController(
    context: Context,
    private val onReady: () -> Unit,
    private val onStart: (String) -> Unit,
    private val onDone: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onFocusLoss: () -> Unit = {},
    private val onFocusGain: () -> Unit = {}
) {
    private var ready = false
    private lateinit var tts: TextToSpeech
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var focusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var hasFocus = false

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
        requestAudioFocus()
        if (voiceName.isNotBlank()) {
            tts.voices?.firstOrNull { it.name == voiceName }?.let { tts.voice = it }
        }
        tts.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
        tts.setPitch(pitch.coerceIn(0.5f, 2.0f))
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.SUCCESS
    }

    private fun requestAudioFocus() {
        if (hasFocus) return
        val listener = focusListener ?: run {
            AudioManager.OnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        hasFocus = false
                        onFocusLoss()
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        hasFocus = true
                        onFocusGain()
                    }
                }
            }.also { focusListener = it }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(listener)
                .setWillPauseWhenDucked(false)
                .build()
                focusRequest = request
            val result = audioManager.requestAudioFocus(request)
            hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        focusListener?.let { listener ->
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) } ?: run {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(listener)
            }
        }
        focusListener = null
        focusRequest = null
        hasFocus = false
    }

    fun voices(): List<Voice> = if (ready) tts.voices?.toList().orEmpty().sortedBy { it.name } else emptyList()

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        abandonAudioFocus()
        tts.shutdown()
    }
}
