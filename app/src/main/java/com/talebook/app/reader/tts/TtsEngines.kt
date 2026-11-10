package com.talebook.app.reader.tts

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * 按设置挑朗读引擎，以及现在能不能用联网的那一套。
 */
object TtsEngines {

    /** 能不能用联网服务：这条服务本身能用，而且网络条件符合「只在 Wi-Fi 下用」。 */
    fun onlineAllowed(context: Context, service: TtsService, wifiOnly: Boolean): Boolean {
        if (service.format == TtsRequestFormat.BUILT_IN || !service.isUsable) return false
        if (!wifiOnly) return anyNetwork(context)
        return networkCapabilities(context)?.let { capabilities ->
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } == true
    }

    /** 按服务的格式造一个引擎。 */
    fun create(context: Context, service: TtsService, listener: TtsListener): TtsBackend = when (service.format) {
        TtsRequestFormat.BUILT_IN -> SystemTtsBackend(context, listener)
        TtsRequestFormat.EDGE -> EdgeTtsBackend(context, service, listener)
        TtsRequestFormat.OPENAI_SPEECH, TtsRequestFormat.OPENAI_CHAT -> OpenAiTtsBackend(context, service, listener)
    }

    private fun anyNetwork(context: Context): Boolean =
        networkCapabilities(context)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    private fun networkCapabilities(context: Context): NetworkCapabilities? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val network = manager.activeNetwork ?: return null
        return runCatching { manager.getNetworkCapabilities(network) }.getOrNull()
    }
}
