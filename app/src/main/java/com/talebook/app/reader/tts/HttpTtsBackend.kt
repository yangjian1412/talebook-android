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
 * 联网的朗读引擎：OpenAI 兼容的 /audio/speech（自建服务或其他兼容网关），
 * 以及小米 MiMo 那种走 /chat/completions 的格式。
 *
 * 一句一句取音频，取到就播；同时提前取好下一句，不然每句之间都会空一下。
 * 音频只在内存里，不写文件。
 */
class HttpTtsBackend(
    context: Context,
    private val config: TtsEngineConfig,
    private val listener: TtsListener
) : TtsBackend {

    override val engineLabel: String =
        if (config.engine == TtsEngine.OPENAI_CHAT) "MiMo 在线朗读" else "在线模型朗读"

    /** 这类接口都没有调音色的参数，界面上的音调滑杆要藏起来。 */
    override val supportsPitch: Boolean = false

    private val player = TtsAudioPlayer(context, listener)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var speechJob: Job? = null

    /** 正在读的是哪一句；取回来的音频如果对不上了就直接丢掉。 */
    private var speakingId: String? = null

    /** 提前取好的下一句。只留一条，多了没用。 */
    private val prepared = ConcurrentHashMap<String, ByteArray>()

    /** 上一次用过的参数，用来判断提前取好的音频还能不能用。 */
    private var lastOptions = TtsSpeechOptions()

    override fun start(onReady: (List<TtsVoice>) -> Unit, onFailure: (String) -> Unit) {
        if (!config.isUsable) {
            scope.launch { withContext(Dispatchers.Main) { onFailure("还没填朗读服务的地址") } }
            return
        }
        scope.launch {
            val voices = availableVoices()
            withContext(Dispatchers.Main) { onReady(voices) }
        }
    }

    /** 服务能列出来就用它的，列不出来就用预置的，界面上至少有的选。 */
    private fun availableVoices(): List<TtsVoice> {
        val fromService = runCatching { TtsHttp.voices(config) }.getOrNull().orEmpty()
        if (fromService.isNotEmpty()) return fromService
        return if (config.engine == TtsEngine.OPENAI_CHAT) TtsPresets.MIMO_VOICES else TtsPresets.OPENAI_VOICES
    }

    override fun preload(text: String, utteranceId: String) {
        if (text.isBlank() || prepared.containsKey(utteranceId)) return
        val options = lastOptions
        prepared.keys.filter { id -> id != utteranceId }.forEach { id -> prepared.remove(id) }
        scope.launch {
            runCatching { TtsHttp.synthesize(config, text, options) }
                .onSuccess { bytes -> prepared[utteranceId] = bytes }
        }
    }

    override fun speak(text: String, utteranceId: String, options: TtsSpeechOptions): Boolean {
        if (text.isBlank()) return false
        if (options != lastOptions) {
            // 语速或音色变了，提前取好的那句就不作数了。
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
            val result = runCatching { TtsHttp.synthesize(config, text, options) }
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
        if (!started) reportFailure(IOException("取回来的音频放不出来"))
    }

    private suspend fun reportFailure(error: Throwable) {
        val message = when (error) {
            is IOException -> error.message?.takeIf { it.isNotBlank() } ?: "连不上朗读服务"
            else -> error.message ?: "朗读服务出了问题"
        }
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
