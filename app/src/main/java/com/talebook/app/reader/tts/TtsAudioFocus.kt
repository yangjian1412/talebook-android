package com.talebook.app.reader.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * 音频焦点。朗读时要占住声道，电话或音乐进来时要让出来，
 * 内置引擎和联网引擎都用这一份。
 */
class TtsAudioFocus(
    context: Context,
    private val onLost: () -> Unit,
    private val onRegained: () -> Unit
) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var request: AudioFocusRequest? = null
    private var listener: AudioManager.OnAudioFocusChangeListener? = null
    private var held = false

    /** 占住声道；已经占着就不再重复申请。 */
    fun request() {
        if (held) return
        val changeListener = listener ?: AudioManager.OnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    held = false
                    onLost()
                }

                AudioManager.AUDIOFOCUS_GAIN -> {
                    held = true
                    onRegained()
                }
            }
        }.also { listener = it }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(changeListener)
                .setWillPauseWhenDucked(false)
                .build()
            request = focusRequest
            held = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            held = audioManager.requestAudioFocus(
                changeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    /** 让出声道。 */
    fun abandon() {
        listener?.let { focusListener ->
            request?.let { audioManager.abandonAudioFocusRequest(it) } ?: run {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusListener)
            }
        }
        listener = null
        request = null
        held = false
    }
}
