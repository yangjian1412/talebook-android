package com.talebook.app.reader.tts

/**
 * 朗读引擎的接口。
 *
 * 手机内置的 TTS 和联网的语音模型都实现这个接口，阅读器只按这里调用，
 * 换引擎不影响逐句队列、高亮、翻页这些已有逻辑。
 */
interface TtsBackend {

    /** 界面上要显示「现在用的是哪个引擎」，例如「手机内置」「MiMo · 冰糖」。 */
    val engineLabel: String

    /** 能不能调音调。不支持的引擎要把界面上的音调滑杆藏起来。 */
    val supportsPitch: Boolean
        get() = true

    /** 准备引擎：成功就给出可用的音色，失败给出原因（都是主线程回调）。 */
    fun start(onReady: (List<TtsVoice>) -> Unit, onFailure: (String) -> Unit)

    /**
     * 把下一句要读的文本先交给引擎。能提前取音频的会提前取好，
     * 等真的轮到它时就不必再等网络，听起来才不会每句都顿一下。
     */
    fun preload(text: String, utteranceId: String) = Unit

    /**
     * 朗读一句。返回 false 只表示这句没能开始（参数不对、引擎没准备好）；
     * 开始之后的失败都通过 [TtsListener.onSpeakFailed] 说出去。
     */
    fun speak(text: String, utteranceId: String, options: TtsSpeechOptions): Boolean

    /** 停下正在读的这句（不算读完，不会触发完成回调）。 */
    fun stop()

    /** 释放引擎，之后不再有任何回调。 */
    fun release()
}

/** 一个可选项：音色或模型。id 存进设置，label 显示在界面上。 */
data class TtsVoice(val id: String, val label: String)

/** 一句的朗读参数。引擎不支持的那项自己忽略。 */
data class TtsSpeechOptions(
    val rate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val voice: String = ""
)

/** 朗读过程中的事件，由引擎回调给阅读器。 */
interface TtsListener {

    fun onSpeakStarted(utteranceId: String)

    fun onSpeakFinished(utteranceId: String)

    /** 这一句失败了，带原因；阅读器可以据此换回内置引擎。 */
    fun onSpeakFailed(message: String)

    /** 别的应用要放声音（电话、音乐），朗读该暂停。 */
    fun onAudioFocusLost() = Unit

    /** 音频焦点回来了。 */
    fun onAudioFocusRegained() = Unit
}
