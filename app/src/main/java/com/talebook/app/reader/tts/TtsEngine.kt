package com.talebook.app.reader.tts

/**
 * 可选的朗读引擎。
 *
 * BUILT_IN 用系统自带的 TTS，不联网也没有额度限制；其余都要联网，
 * 区别只在请求格式：[OPENAI_SPEECH] 是 /v1/audio/speech（自建服务、OpenAI 兼容网关），
 * [OPENAI_CHAT] 是小米 MiMo 那种走 /v1/chat/completions 带 audio 参数的格式。
 */
enum class TtsEngine {

    /** 手机内置的 TTS（系统设置里选的那个引擎）。 */
    BUILT_IN,

    /** OpenAI 兼容的 /v1/audio/speech，自建或在线都可以。 */
    OPENAI_SPEECH,

    /** 小米 MiMo 这类走 /v1/chat/completions + audio 的接口。 */
    OPENAI_CHAT;

    companion object {

        /** 设置里存的是名字；不认识的一律当内置处理。 */
        fun fromName(name: String?): TtsEngine = values().firstOrNull { engine -> engine.name == name }
            ?: BUILT_IN
    }
}

/** 朗读引擎的配置，存在设置里。 */
data class TtsEngineConfig(
    val engine: TtsEngine = TtsEngine.BUILT_IN,
    /** 服务地址，形如 https://…/v1 或 http://192.168.1.20:8880/v1。 */
    val baseUrl: String = "",
    /** 访问口令；自建服务一般可以留空。 */
    val apiKey: String = "",
    /** 模型名，界面上从服务返回的列表里选。 */
    val model: String = "",
    /** 音色 id。 */
    val voice: String = "",
    /** 只在 Wi-Fi 下用联网引擎，用移动数据时自动改回内置。 */
    val wifiOnly: Boolean = true
) {

    /** 联网引擎才需要这些；缺地址就没有意义。 */
    val isUsable: Boolean
        get() = engine != TtsEngine.BUILT_IN && baseUrl.isNotBlank()

    /** 拼一个接口地址，例如 apiPath("/audio/speech")。 */
    fun apiPath(path: String): String = baseUrl.trim().trimEnd('/') + path
}

/**
 * 各家引擎的预置：官方地址、模型名、常见音色。
 *
 * 列表只是给界面一个起点；能取到服务真实列表时以服务的为准。
 */
object TtsPresets {

    /** 小米 MiMo 的官方地址和朗读模型。 */
    const val MIMO_BASE_URL = "https://api.xiaomimimo.com/v1"
    const val MIMO_MODEL = "mimo-v2.5-tts"

    /** MiMo 的预置音色（官方文档里的中文/英文音色）。 */
    val MIMO_VOICES = listOf(
        TtsVoice("mimo_default", "MiMo 默认"),
        TtsVoice("冰糖", "冰糖（中文女声）"),
        TtsVoice("茉莉", "茉莉（中文女声）"),
        TtsVoice("苏打", "苏打（中文男声）"),
        TtsVoice("白桦", "白桦（中文男声）"),
        TtsVoice("Mia", "Mia（英文女声）"),
        TtsVoice("Chloe", "Chloe（英文女声）"),
        TtsVoice("Milo", "Milo（英文男声）"),
        TtsVoice("Dean", "Dean（英文男声）")
    )

    /** OpenAI 兼容接口通常接受的模型名，取不到列表时用它。 */
    const val OPENAI_SPEECH_MODEL = "tts-1"

    /** OpenAI 的标准音色名；自建服务大多也接受这些名字。 */
    val OPENAI_VOICES = listOf(
        TtsVoice("alloy", "alloy"),
        TtsVoice("ash", "ash"),
        TtsVoice("coral", "coral"),
        TtsVoice("echo", "echo"),
        TtsVoice("fable", "fable"),
        TtsVoice("nova", "nova"),
        TtsVoice("onyx", "onyx"),
        TtsVoice("sage", "sage"),
        TtsVoice("shimmer", "shimmer")
    )
}
