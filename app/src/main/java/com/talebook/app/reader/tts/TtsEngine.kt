package com.talebook.app.reader.tts

/**
 * 一种向朗读服务取音频的格式。
 *
 * 这里按请求格式分，不按品牌分：MiMo、OpenAI、自建的 Kokoro 都说自己「OpenAI 兼容」，
 * 但要音频的路径不一样，能不能自己填地址也就不一样。
 */
enum class TtsRequestFormat {

    /** 手机内置的 TTS（系统设置里选中的那个引擎），不联网，也没有次数限制。 */
    BUILT_IN,

    /** OpenAI 的音频接口：POST {地址}/audio/speech，自建服务大多也是这个。 */
    OPENAI_SPEECH,

    /** OpenAI 的对话接口带 audio 参数：POST {地址}/chat/completions，音频以 base64 放在返回里（小米 MiMo 用这种）。 */
    OPENAI_CHAT,

    /** Edge 的在线语音：微软的 WebSocket 接口，不要 Key，音色现成。 */
    EDGE;

    /** 界面上说明这种格式怎么填地址。 */
    val description: String
        get() = when (this) {
            BUILT_IN -> "用系统里的朗读引擎，不联网、没有次数限制"
            OPENAI_SPEECH -> "OpenAI 兼容：POST 地址/audio/speech（自建服务、Kokoro-FastAPI 等）"
            OPENAI_CHAT -> "OpenAI 兼容：POST 地址/chat/completions，音频在返回里（小米 MiMo 这类）"
            EDGE -> "微软 Edge 的在线语音，不用填地址和 Key"
        }

    /** 需不需要自己填服务地址。 */
    val needsUrl: Boolean
        get() = this == OPENAI_SPEECH || this == OPENAI_CHAT

    /** 能不能自己加一套这种格式的服务；内置和 Edge 是直接在列表里的，不用加。 */
    val isAddable: Boolean
        get() = this == OPENAI_SPEECH || this == OPENAI_CHAT

    companion object {

        /** 加号里能选的几种。 */
        val addable: List<TtsRequestFormat>
            get() = listOf(OPENAI_SPEECH, OPENAI_CHAT)

        /** 设置里存的是名字；认不出来的（旧版本存的 BUILT_IN 等）一律当内置。 */
        fun fromName(name: String?): TtsRequestFormat = values().firstOrNull { item -> item.name == name }
            ?: BUILT_IN
    }
}

/**
 * 一套已经配好的朗读服务：名字、格式、地址、口令、模型、音色。
 *
 * 手机内置和 Edge 是固定有的（Edge 免费、不用配），另外可以自己加几套
 * （自建服务、小米 MiMo 之类），读的时候选用哪一套。
 */
data class TtsService(
    val id: String,
    val name: String = "",
    val format: TtsRequestFormat = TtsRequestFormat.OPENAI_SPEECH,
    /** 形如 https://…/v1 或 http://192.168.1.20:8880/v1；EDGE 不用填。 */
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val voice: String = ""
) {

    companion object {

        /** 内置引擎的 id；选用它等于「不用任何服务」。 */
        const val BUILT_IN_ID = ""

        /** 内置引擎在界面上的名字。 */
        const val BUILT_IN_LABEL = "手机内置"

        /** Edge 那一条的 id 固定；它不用配，一直就在列表里。 */
        const val EDGE_ID = "edge"

        /** Edge 在界面上的名字。 */
        const val EDGE_LABEL = "Edge 在线语音"

        /** 内置引擎的那一条服务记录；id 是空的，只为让后面的代码好写。 */
        fun builtIn(voice: String = ""): TtsService =
            TtsService(id = BUILT_IN_ID, name = BUILT_IN_LABEL, format = TtsRequestFormat.BUILT_IN, voice = voice)

        /** Edge 的那一条服务记录；只有音色要选，地址和 Key 都不用。 */
        fun edge(voice: String = ""): TtsService =
            TtsService(id = EDGE_ID, name = EDGE_LABEL, format = TtsRequestFormat.EDGE, voice = voice)
    }

    /** 是不是手机内置。 */
    val isBuiltIn: Boolean
        get() = format == TtsRequestFormat.BUILT_IN

    /** 是不是 Edge。 */
    val isEdge: Boolean
        get() = format == TtsRequestFormat.EDGE || id == EDGE_ID

    /** 内置和 Edge 是本来就有的，不让删。 */
    val isFixed: Boolean
        get() = isBuiltIn || isEdge

    /** 这套能不能调音调；不能的话界面上的音调滑杆要藏起来。 */
    val supportsPitch: Boolean
        get() = format == TtsRequestFormat.BUILT_IN || format == TtsRequestFormat.EDGE

    /** 能不能真去朗读一句。 */
    val isUsable: Boolean
        get() = when (format) {
            TtsRequestFormat.BUILT_IN -> true
            TtsRequestFormat.EDGE -> true
            else -> baseUrl.isNotBlank()
        }

    /** 拼一个接口路径，例如 apiPath("/audio/speech")。 */
    fun apiPath(path: String): String = baseUrl.trim().trimEnd('/') + path

    /** 界面上和朗读面板里显示的名字。 */
    val displayName: String
        get() = when (format) {
            TtsRequestFormat.BUILT_IN -> BUILT_IN_LABEL
            TtsRequestFormat.EDGE -> EDGE_LABEL
            else -> name.ifBlank { baseUrl }
        }

    /** 一行说明，用在服务列表里。 */
    val summary: String
        get() = when (format) {
            TtsRequestFormat.BUILT_IN -> "不联网"
            TtsRequestFormat.EDGE -> listOf("微软，免费", voiceDisplay).filter { part -> part.isNotBlank() }.joinToString(" · ")
            else -> buildString {
                append(baseUrl.trim())
                if (model.isNotBlank()) {
                    append(" · ")
                    append(model)
                }
            }
        }

    /** 选中的音色；没选就是服务自己的默认。 */
    val voiceDisplay: String
        get() = when (format) {
            TtsRequestFormat.EDGE -> {
                val chosen = voice.ifBlank { EdgeSpeech.DEFAULT_VOICE }
                TtsPresets.EDGE_VOICES.firstOrNull { preset -> preset.id == chosen }?.label ?: chosen
            }

            else -> voice.trim()
        }
}

/** 一次朗读要用的参数，由 [TtsService] 加上界面上的语速、音调凑成。 */
data class TtsSpeechSetting(
    val service: TtsService,
    val rate: Float = 1.0f,
    val pitch: Float = 1.0f,
    /** 实际要用的音色；空字符串表示用服务默认的。 */
    val voice: String = ""
) {

    /** 朗读面板上要显示「现在用的是哪一套」。 */
    val label: String
        get() = service.displayName
}

/**
 * 各家服务的预置：官方地址、模型名、常见音色。
 *
 * 只是给界面一个起点；能取到服务真实列表时以服务的为准。
 */
object TtsPresets {

    /** 小米 MiMo 的官方地址和朗读模型。 */
    const val MIMO_BASE_URL = "https://api.xiaomimimo.com/v1"
    const val MIMO_MODEL = "mimo-v2.5-tts"

    /** MiMo 的朗读模型（官方列出的三个）。 */
    val MIMO_MODELS = listOf(
        TtsVoice(MIMO_MODEL, "mimo-v2.5-tts（预置音色）"),
        TtsVoice("mimo-v2.5-tts-voicedesign", "mimo-v2.5-tts-voicedesign（文字描述音色）"),
        TtsVoice("mimo-v2.5-tts-voiceclone", "mimo-v2.5-tts-voiceclone（样例音频复刻）")
    )

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

    /** Edge 常用音色：中文的在前，够用了；完整列表能从服务取。 */
    val EDGE_VOICES = listOf(
        TtsVoice("zh-CN-XiaoxiaoNeural", "晓晓 · 中文女声"),
        TtsVoice("zh-CN-XiaoyiNeural", "晓伊 · 中文女声"),
        TtsVoice("zh-CN-YunxiNeural", "云希 · 中文男声"),
        TtsVoice("zh-CN-YunjianNeural", "云健 · 中文男声"),
        TtsVoice("zh-CN-XiaoxiaoMultilingualNeural", "晓晓多语言 · 中英混读"),
        TtsVoice("zh-CN-liaoning-XiaobeiNeural", "晓北 · 东北话"),
        TtsVoice("zh-TW-HsiaoChenNeural", "晓辰 · 台湾女声"),
        TtsVoice("zh-HK-HiuGaaiNeural", "晓敏 · 粤语女声"),
        TtsVoice("en-US-AriaNeural", "Aria · 英文女声"),
        TtsVoice("en-US-GuyNeural", "Guy · 英文男声")
    )
}
