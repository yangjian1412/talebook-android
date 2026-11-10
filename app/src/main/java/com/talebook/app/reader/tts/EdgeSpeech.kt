package com.talebook.app.reader.tts

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * Edge 在线语音的接口。
 *
 * 微软这个接口只走 WebSocket（HTTP 的同一个地址是 404），
 * 要带一个按当前时间算出来的 Sec-MS-GEC 令牌，见 [EdgeTtsToken]。
 * 返回的音频是一段一段的 MP3，拼起来就是这一句，全程只在内存里。
 */
object EdgeSpeech {

    /** 默认音色：晓晓，中文女声，效果稳。 */
    const val DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural"

    private const val HOST = "speech.platform.bing.com"
    private const val BASE_PATH = "consumer/speech/synthesize/readaloud"
    private const val OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3"

    /** Edge 客户端的标识；服务用它认这是 Edge 的朗读功能。 */
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0"

    /** 一句最多等多久；自建网络慢的时候也别让朗读一直卡着。 */
    private const val SPEECH_TIMEOUT_MS = 30_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    /** 取一句的音频。 */
    suspend fun synthesize(text: String, options: TtsSpeechOptions): ByteArray = withContext(Dispatchers.IO) {
        val voice = options.voice.ifBlank { DEFAULT_VOICE }
        val audio = withTimeoutOrNull(SPEECH_TIMEOUT_MS) { requestAudio(text, voice, options) }
        if (audio == null) throw IOException("Edge 朗读没在规定时间内返回")
        if (audio.isEmpty()) throw IOException("Edge 朗读没返回音频")
        audio
    }

    /** 音色清单；取不到就返回空列表，界面上会用预置的那些。 */
    fun voicesBlocking(): List<TtsVoice> = runCatching {
        val request = Request.Builder()
            .url("https://$HOST/$BASE_PATH/voices/list?trustedclienttoken=${EdgeTtsToken.TRUSTED_CLIENT_TOKEN}")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            parseVoices(response.body?.string().orEmpty())
        }
    }.getOrDefault(emptyList())

    private fun parseVoices(jsonText: String): List<TtsVoice> {
        val array = runCatching { com.google.gson.JsonParser.parseString(jsonText) }
            .getOrNull()?.takeIf { element -> element.isJsonArray }?.asJsonArray ?: return emptyList()
        val result = ArrayList<TtsVoice>()
        for (element in array) {
            if (!element.isJsonObject) continue
            val voice = element.asJsonObject
            if (voice.stringOf("Status") != "GA") continue
            val shortName = voice.stringOf("ShortName").orEmpty().trim()
            val locale = voice.stringOf("Locale").orEmpty().trim()
            if (shortName.isBlank()) continue
            val isChinese = locale.startsWith("zh", ignoreCase = true)
            val isEnglishUs = locale.startsWith("en-US", ignoreCase = true)
            if (!isChinese && !isEnglishUs) continue
            result.add(TtsVoice(shortName, voiceLabel(shortName, locale, voice.stringOf("Gender"))))
        }
        return result.sortedWith(
            compareBy<TtsVoice> { item -> !item.label.startsWith("zh-CN") }
                .thenBy { item -> item.id }
        ).take(120)
    }

    private fun voiceLabel(shortName: String, locale: String, gender: String?): String {
        val which = when {
            gender.equals("Female", ignoreCase = true) -> "女声"
            gender.equals("Male", ignoreCase = true) -> "男声"
            else -> ""
        }
        return listOf(shortName, locale, which).filter { part -> part.isNotBlank() }.joinToString(" · ")
    }

    private fun com.google.gson.JsonObject.stringOf(name: String): String? =
        get(name)?.takeIf { element -> element.isJsonPrimitive }?.asString

    private suspend fun requestAudio(text: String, voice: String, options: TtsSpeechOptions): ByteArray? =
        suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url(webSocketUrl())
                .header("User-Agent", USER_AGENT)
                .build()
            val audio = ByteArrayOutputStream()
            val finished = AtomicBoolean(false)
            val socket = client.newWebSocket(request, object : WebSocketListener() {

                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(speechConfigMessage())
                    webSocket.send(ssmlMessage(text, voice, options))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (pathOf(text) == "turn.end") {
                        if (finished.compareAndSet(false, true)) continuation.resume(audio.toByteArray())
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    if (bytes.size <= 2) return
                    val headerLength = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
                    val start = headerLength + 2
                    if (headerLength > bytes.size || start >= bytes.size) return
                    audio.write(bytes.toByteArray(), start, bytes.size - start)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    // 正常情况下 turn.end 已经把音频交出去了；这里只是兜底。
                    if (finished.compareAndSet(false, true) && audio.size() > 0) {
                        continuation.resume(audio.toByteArray())
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (finished.compareAndSet(false, true)) {
                        continuation.resumeWithException(
                            IOException("连不上 Edge 朗读服务：${t.message ?: t.javaClass.simpleName}")
                        )
                    }
                }
            })
            continuation.invokeOnCancellation { runCatching { socket.cancel() } }
        }

    private fun webSocketUrl(): String =
        "wss://$HOST/$BASE_PATH/edge/v1" +
            "?TrustedClientToken=${EdgeTtsToken.TRUSTED_CLIENT_TOKEN}" +
            "&Sec-MS-GEC=${EdgeTtsToken.generate()}" +
            "&Sec-MS-GEC-Version=${EdgeTtsToken.SEC_MS_GEC_VERSION}"

    /** 告诉服务要什么格式、要不要词边界。 */
    private fun speechConfigMessage(): String = "X-Timestamp:${timestamp()}\r\n" +
        "Content-Type:application/json; charset=utf-8\r\n" +
        "Path:speech.config\r\n\r\n" +
        """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},""" +
        """"outputFormat":"$OUTPUT_FORMAT"}}}}""" + "\r\n"

    /** 要读的那一句。 */
    private fun ssmlMessage(text: String, voice: String, options: TtsSpeechOptions): String {
        val requestId = java.util.UUID.randomUUID().toString().replace("-", "")
        return "X-RequestId:$requestId\r\n" +
            "Content-Type:application/ssml+xml\r\n" +
            "X-Timestamp:${timestamp()}Z\r\n" +
            "Path:ssml\r\n\r\n" +
            buildSsml(text, voice, options)
    }

    private fun buildSsml(text: String, voice: String, options: TtsSpeechOptions): String {
        val rate = signedPercent((options.rate.coerceIn(0.5f, 2.0f) - 1f) * 100f)
        val pitch = signedPercent((options.pitch.coerceIn(0.5f, 2.0f) - 1f) * 100f)
        return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='${localeOf(voice)}'>" +
            "<voice name='$voice'><prosody pitch='$pitch%' rate='$rate%' volume='+0%'>" +
            escapeXml(text) +
            "</prosody></voice></speak>"
    }

    /** 音色名里带着语种（zh-CN-XiaoxiaoNeural），SSML 的 xml:lang 跟着它写。 */
    private fun localeOf(voice: String): String {
        val parts = voice.split("-")
        return if (parts.size >= 2) "${parts[0]}-${parts[1]}" else "zh-CN"
    }

    private fun signedPercent(value: Float): String {
        val rounded = Math.round(value)
        return if (rounded > 0) "+$rounded" else "$rounded"
    }

    private fun escapeXml(text: String): String = buildString(text.length) {
        text.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }

    /** 消息头里的 Path，用来分辨是音频、元数据还是这一句结束。 */
    private fun pathOf(message: String): String {
        message.lineSequence().forEach { line ->
            if (line.startsWith("Path:")) return line.substring("Path:".length).trim()
        }
        return ""
    }

    /** Edge 客户端用的那种时间写法。 */
    private fun timestamp(): String =
        java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).format(
            java.time.format.DateTimeFormatter
                .ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
        )
}
