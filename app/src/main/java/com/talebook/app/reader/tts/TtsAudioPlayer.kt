package com.talebook.app.reader.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer

/**
 * 播放取回来的音频字节。
 *
 * 联网引擎返回的是一句的 MP3/WAV，也就几十到几百 KB，
 * 所以只在内存里交给MediaPlayer，不落盘、也不留缓存文件。
 */
class TtsAudioPlayer(context: Context, private val listener: TtsListener) {

    private val focus = TtsAudioFocus(
        context,
        onLost = { listener.onAudioFocusLost() },
        onRegained = { listener.onAudioFocusRegained() }
    )
    private var player: MediaPlayer? = null
    private var playingId: String? = null

    /** 开始播放这一句；返回 false 表示没能开始。 */
    fun play(utteranceId: String, bytes: ByteArray): Boolean {
        releasePlayer()
        if (bytes.isEmpty()) return false
        focus.request()
        val result = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(BytesDataSource(bytes))
                setOnPreparedListener { started ->
                    if (playingId == utteranceId) {
                        started.start()
                        listener.onSpeakStarted(utteranceId)
                    }
                }
                setOnCompletionListener {
                    if (playingId == utteranceId) {
                        playingId = null
                        listener.onSpeakFinished(utteranceId)
                    }
                }
                setOnErrorListener { _, _, _ ->
                    if (playingId == utteranceId) {
                        playingId = null
                        listener.onSpeakFailed("音频播放失败")
                    }
                    true
                }
                player = this
                playingId = utteranceId
                prepareAsync()
            }
        }
        if (result.isFailure) releasePlayer()
        return result.isSuccess
    }

    /** 停下正在播的这句；不算读完，所以不会触发完成回调。 */
    fun stop() {
        releasePlayer()
    }

    /** 不再播了，连音频焦点一起放掉。 */
    fun release() {
        releasePlayer()
        focus.abandon()
    }

    private fun releasePlayer() {
        playingId = null
        player?.let { current ->
            runCatching { if (current.isPlaying) current.stop() }
            runCatching { current.release() }
        }
        player = null
    }
}

/** 让MediaPlayer直接读内存里的字节，不用先写成文件。 */
private class BytesDataSource(private val bytes: ByteArray) : MediaDataSource() {

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        val start = position.toInt()
        if (start >= bytes.size) return -1
        val length = minOf(size, bytes.size - start)
        System.arraycopy(bytes, start, buffer, offset, length)
        return length
    }

    override fun getSize(): Long = bytes.size.toLong()

    override fun close() = Unit
}
