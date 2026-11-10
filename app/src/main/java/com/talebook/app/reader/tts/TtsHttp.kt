package com.talebook.app.reader.tts

import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 向朗读服务要音频、取模型和音色清单。
 *
 * 这里的函数都是阻塞的，调用方要在后台线程上跑。
 * 注意一次音频不落盘：拿回来的就是字节，交给 [TtsAudioPlayer] 播。
 */
object TtsHttp {

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    /** 自建服务可能很慢，等待给宽一点。 */
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 请求一句的音频；失败时抛出的异常带人能看懂的原因。 */
    fun synthesize(service: TtsService, text: String, options: TtsSpeechOptions): ByteArray {
        val isChatFormat = service.format == TtsRequestFormat.OPENAI_CHAT
        val url = if (isChatFormat) service.apiPath("/chat/completions") else service.apiPath("/audio/speech")
        val body = if (isChatFormat) chatRequest(service, text, options) else speechRequest(service, text, options)
        val request = requestBuilder(service, url)
            .post(body.toString().toRequestBody(JSON_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) throw IOException(failureMessage(response.code, bytes))
            return if (isChatFormat) chatAudioOf(bytes) else bytes
        }
    }

    /** 服务上能选哪些模型；取不到就返回空列表，界面上让用户自己填。 */
    fun models(service: TtsService): List<TtsVoice> {
        if (service.format == TtsRequestFormat.OPENAI_CHAT) return TtsPresets.MIMO_MODELS
        val json = getJson(service, "/models") ?: return emptyList()
        val items = firstArray(json, "data", "models", "body")
        return items.mapNotNull { element ->
            val id = when {
                element.isJsonPrimitive -> element.asString
                element.isJsonObject -> element.asJsonObject.stringOr("id", "name", "model")
                else -> null
            }?.trim().orEmpty()
            if (id.isBlank()) null else TtsVoice(id, id)
        }
    }

    /** 服务上能选哪些音色；取不到就返回空列表。 */
    fun voices(service: TtsService): List<TtsVoice> {
        val json = getJson(service, "/audio/voices") ?: getJson(service, "/voices") ?: return emptyList()
        val items = firstArray(json, "voices", "data")
        return items.mapNotNull { element ->
            when {
                element.isJsonPrimitive -> {
                    val id = element.asString.trim()
                    if (id.isBlank()) null else TtsVoice(id, id)
                }

                element.isJsonObject -> {
                    val voice = element.asJsonObject
                    val id = voice.stringOr("id", "voice", "name", "shortName")?.trim().orEmpty()
                    if (id.isBlank()) null else TtsVoice(id, voice.stringOr("name", "description", "label")?.trim() ?: id)
                }

                else -> null
            }
        }
    }

    /** OpenAI 的 /audio/speech 请求体。 */
    private fun speechRequest(service: TtsService, text: String, options: TtsSpeechOptions): JsonObject =
        JsonObject().apply {
            addProperty("model", service.model.ifBlank { TtsPresets.OPENAI_SPEECH_MODEL })
            addProperty("input", text)
            addProperty("voice", options.voice.ifBlank { TtsPresets.OPENAI_VOICES.first().id })
            addProperty("response_format", "mp3")
            addProperty("speed", options.rate.coerceIn(0.5f, 2.0f))
        }

    /**
     * 对话接口带 audio 参数的请求体（小米 MiMo 这种）。
     *
     * 它也叫 OpenAI 兼容，但要走 /chat/completions，要读的文本放在 assistant 消息里，
     * 音频以 base64 放在 message.audio.data。
     */
    private fun chatRequest(service: TtsService, text: String, options: TtsSpeechOptions): JsonObject =
        JsonObject().apply {
            addProperty("model", service.model.ifBlank { TtsPresets.MIMO_MODEL })
            add(
                "messages",
                JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "assistant")
                        addProperty("content", text)
                    })
                }
            )
            add(
                "audio",
                JsonObject().apply {
                    addProperty("format", "mp3")
                    addProperty("voice", options.voice.ifBlank { TtsPresets.MIMO_VOICES.first().id })
                }
            )
            addProperty("stream", false)
        }

    /** 从对话接口那种返回里取出音频字节。 */
    private fun chatAudioOf(bytes: ByteArray): ByteArray {
        val text = String(bytes, Charsets.UTF_8)
        val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            ?: throw IOException("服务的返回不是预期的 JSON：${text.take(120)}")
        val encoded = json.getAsJsonArray("choices")?.takeIf { array -> array.size() > 0 }
            ?.get(0)?.asJsonObject?.getAsJsonObject("message")?.getAsJsonObject("audio")
            ?.stringOr("data", "audio")
        if (encoded.isNullOrBlank()) throw IOException("服务的返回里没有音频：${text.take(120)}")
        return runCatching { Base64.decode(encoded, Base64.DEFAULT) }
            .getOrElse { throw IOException("服务返回的音频编码读不懂") }
    }

    private fun requestBuilder(service: TtsService, url: String): Request.Builder {
        val builder = Request.Builder().url(url).header("Accept", "audio/mpeg, application/json")
        if (service.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${service.apiKey}")
            // 小米 MiMo 的文档用的是 api-key 头，两种都带上，省得区分服务。
            builder.header("api-key", service.apiKey)
        }
        return builder
    }

    private fun getJson(service: TtsService, path: String): JsonObject? = runCatching {
        val request = requestBuilder(service, service.apiPath(path))
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string().orEmpty()
            JsonParser.parseString(body).takeIf { element -> element.isJsonObject }?.asJsonObject
        }
    }.getOrNull()

    /** 在几个可能的字段名里找第一个数组。 */
    private fun firstArray(json: JsonObject, vararg names: String): JsonArray {
        for (name in names) {
            val element = json.get(name) ?: continue
            if (element.isJsonArray) return element.asJsonArray
        }
        return JsonArray()
    }

    private fun JsonObject.stringOr(vararg names: String): String? {
        for (name in names) {
            val element = get(name) ?: continue
            if (element.isJsonPrimitive) return element.asString
        }
        return null
    }

    /** 把 HTTP 状态码翻译成人能照着改的话。 */
    private fun failureMessage(code: Int, body: ByteArray): String {
        val detail = runCatching { String(body, Charsets.UTF_8) }.getOrDefault("").trim()
        return when (code) {
            401, 403 -> "服务拒绝了这次请求（$code），检查 API Key"
            404 -> "没有这个接口（$code），地址通常填到 /v1 为止"
            429 -> "服务让我们慢一点（429）"
            else -> "服务返回 $code：${detail.take(160).ifBlank { "没有说明" }}"
        }
    }
}
