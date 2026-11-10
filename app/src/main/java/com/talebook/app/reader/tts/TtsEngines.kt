package com.talebook.app.reader.tts

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * 按设置决定用哪个朗读引擎，以及现在能不能用联网的那个。
 */
object TtsEngines {

    /** 能不能用联网引擎：填了地址，而且网络条件符合「只在 Wi-Fi 下用」的要求。 */
    fun onlineAllowed(context: Context, config: TtsEngineConfig): Boolean {
        if (!config.isUsable) return false
        if (!config.wifiOnly) return anyNetwork(context)
        return networkCapabilities(context)?.let { capabilities ->
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } == true
    }

    /** 造一个引擎；用不上联网引擎时由调用方自己选内置的。 */
    fun create(context: Context, config: TtsEngineConfig, listener: TtsListener): TtsBackend = when (config.engine) {
        TtsEngine.OPENAI_SPEECH, TtsEngine.OPENAI_CHAT -> HttpTtsBackend(context, config, listener)
        TtsEngine.BUILT_IN -> SystemTtsBackend(context, listener)
    }

    private fun anyNetwork(context: Context): Boolean =
        networkCapabilities(context)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    private fun networkCapabilities(context: Context): NetworkCapabilities? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        val network = manager.activeNetwork ?: return null
        return runCatching { manager.getNetworkCapabilities(network) }.getOrNull()
    }
}
