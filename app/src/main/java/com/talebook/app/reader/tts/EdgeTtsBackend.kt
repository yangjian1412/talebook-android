package com.talebook.app.reader.tts

import android.content.Context
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Edge 在线语音的引擎。
 *
 * 不要地址也不要 Key，微软那边免费；音色一大堆，语速和音调都能调（走 SSML 的 prosody）。
 * 一句一句取音频，同时提前取下一句，音频只在内存里。
 */
class EdgeTtsBackend(
    context: Context,
    private val service: TtsService,
    private val listener: TtsListener
) : TtsBackend {

    override val engineLabel: String = service.displayName

    override fun start(onReady: (List<TtsVoice>) -> Unit, onFailure: (String) -> Unit) {
        scope.launch {
            val voices = EdgeSpeech.voicesBlocking().ifEmpty { TtsPresets.EDGE_VOICES }
            withContext(Dispatchers.Main) { onReady(voices) }
        }
    }

    private val player = TtsAudioPlayer(context, listener)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var speechJob: Job? = null

    /** 正在读的是哪一句；取回来的音频对不上就丢掉。 */
    private var speakingId: String? = null

    /** 提前取好的下一句，只留一条。 */
    private val prepared = ConcurrentHashMap<String, ByteArray>()

    private var lastOptions = TtsSpeechOptions()

    override fun preload(text: String, utteranceId: String) {
        if (text.isBlank() || prepared.containsKey(utteranceId)) return
        val options = lastOptions
        prepared.keys.filter { id -> id != utteranceId }.forEach { id -> prepared.remove(id) }
        scope.launch {
            runCatching { EdgeSpeech.synthesize(text, options) }
                .onSuccess { bytes -> prepared[utteranceId] = bytes }
        }
    }

    override fun speak(text: String, utteranceId: String, options: TtsSpeechOptions): Boolean {
        if (text.isBlank()) return false
        if (options != lastOptions) {
            // 语速、音调或音色变了，提前取好的那句就不作数了。
            prepared.clear()
            lastOptions = options
        }
        speakingId = utteranceId
        speechJob?.cancel()
        val ready = prepared.remove(utteranceId)
        speechJob = scope.launch {
            if (ready != null) {
                play(utteranceId, ready)
                return@launch
            }
            val result = runCatching { EdgeSpeech.synthesize(text, options) }
            if (speakingId != utteranceId || !isActive) return@launch
            result.fold(
                onSuccess = { bytes -> play(utteranceId, bytes) },
                onFailure = { error -> reportFailure(error) }
            )
        }
        return true
    }

    private suspend fun play(utteranceId: String, bytes: ByteArray) {
        val started = withContext(Dispatchers.Main) { player.play(utteranceId, bytes) }
        if (!started) reportFailure(IOException("Edge 返回的音频放不出来"))
    }

    private suspend fun reportFailure(error: Throwable) {
        val message = error.message?.takeIf { it.isNotBlank() } ?: "Edge 朗读出了点问题"
        withContext(Dispatchers.Main) { listener.onSpeakFailed(message) }
    }

    override fun stop() {
        speakingId = null
        player.stop()
    }

    override fun release() {
        speakingId = null
        speechJob?.cancel()
        prepared.clear()
        player.release()
        scope.cancel()
    }
}
