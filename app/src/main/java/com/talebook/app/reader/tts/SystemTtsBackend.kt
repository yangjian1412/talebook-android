package com.talebook.app.reader.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * 手机内置的朗读：用系统的 TextToSpeech，也就是系统设置里选中的那个引擎。
 *
 * 不联网、也没有次数限制，是联网引擎失败时的退路。
 */
class SystemTtsBackend(
    context: Context,
    private val listener: TtsListener
) : TtsBackend {

    override val engineLabel: String = "手机内置"

    private val appContext = context.applicationContext
    private val focus = TtsAudioFocus(
        appContext,
        onLost = { listener.onAudioFocusLost() },
        onRegained = { listener.onAudioFocusRegained() }
    )
    private var engine: TextToSpeech? = null
    private var ready = false

    override fun start(onReady: (List<TtsVoice>) -> Unit, onFailure: (String) -> Unit) {
        if (ready) {
            onReady(voiceItems())
            return
        }
        val tts = TextToSpeech(appContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                ready = false
                onFailure("系统朗读初始化失败")
                return@TextToSpeech
            }
            ready = true
            runCatching { engine?.language = Locale.getDefault() }
            onReady(voiceItems())
        }
        engine = tts
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {

            override fun onStart(utteranceId: String) {
                listener.onSpeakStarted(utteranceId)
            }

            override fun onDone(utteranceId: String) {
                listener.onSpeakFinished(utteranceId)
            }

            @Deprecated("旧版系统还在调这个")
            override fun onError(utteranceId: String) {
                listener.onSpeakFailed("朗读失败")
            }

            override fun onError(utteranceId: String, errorCode: Int) {
                listener.onSpeakFailed("朗读失败（错误码 $errorCode）")
            }
        })
    }

    override fun speak(text: String, utteranceId: String, options: TtsSpeechOptions): Boolean {
        val tts = engine ?: return false
        if (!ready || text.isBlank()) return false
        focus.request()
        if (options.voice.isNotBlank()) {
            runCatching {
                tts.voices?.firstOrNull { voice -> voice.name == options.voice }?.let { voice -> tts.voice = voice }
            }
        }
        runCatching { tts.setSpeechRate(options.rate.coerceIn(0.5f, 2.0f)) }
        runCatching { tts.setPitch(options.pitch.coerceIn(0.5f, 2.0f)) }
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.SUCCESS
    }

    override fun stop() {
        runCatching { engine?.stop() }
    }

    override fun release() {
        runCatching { engine?.stop() }
        focus.abandon()
        runCatching { engine?.shutdown() }
        engine = null
        ready = false
    }

    /** 系统给的音色：id 和显示名都用名字，设置里存的也是它。 */
    private fun voiceItems(): List<TtsVoice> {
        val tts = engine ?: return emptyList()
        if (!ready) return emptyList()
        return runCatching {
            tts.voices?.map { voice -> TtsVoice(voice.name, voice.name) }.orEmpty()
        }.getOrNull().orEmpty().sortedBy { item -> item.label }
    }
}
