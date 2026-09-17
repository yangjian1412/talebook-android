package com.talebook.app.data.repository

import com.talebook.app.data.api.RetrofitClient
import com.talebook.app.data.model.CaptchaConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class CaptchaStatus {
    object Disabled : CaptchaStatus()
    data class Image(val config: CaptchaConfig) : CaptchaStatus()
    data class Geetest(val config: CaptchaConfig) : CaptchaStatus()
    data class Unknown(val config: CaptchaConfig) : CaptchaStatus()
}

data class CaptchaImage(
    val captchaId: String,
    val imageBase64: String
)

class CaptchaRepository {
    private val api get() = RetrofitClient.getApi()

    suspend fun probe(scene: String = "login"): CaptchaStatus = withContext(Dispatchers.IO) {
        try {
            val resp = api.getCaptchaConfig()
            val body = resp.body()
            val config = body?.config
            when {
                !resp.isSuccessful || body == null -> CaptchaStatus.Disabled
                body.err != "ok" || config == null -> CaptchaStatus.Disabled
                !config.enabled -> CaptchaStatus.Disabled
                !sceneEnabled(config, scene) -> CaptchaStatus.Disabled
                config.provider == "image" -> CaptchaStatus.Image(config)
                config.provider == "geetest" -> CaptchaStatus.Geetest(config)
                else -> CaptchaStatus.Unknown(config)
            }
        } catch (e: Exception) {
            CaptchaStatus.Disabled
        }
    }

    private fun sceneEnabled(config: CaptchaConfig, scene: String): Boolean {
        return when (scene) {
            "login" -> config.scenes.login
            "welcome" -> config.scenes.welcome
            "register" -> config.scenes.register
            "reset" -> config.scenes.reset
            else -> false
        }
    }

    suspend fun fetchImage(): CaptchaImage? = withContext(Dispatchers.IO) {
        try {
            val resp = api.getCaptchaImage()
            val body = resp.body()
            if (!resp.isSuccessful || body == null || body.err != "ok" || body.image.isBlank()) null
            else CaptchaImage(captchaId = body.captchaId, imageBase64 = body.image)
        } catch (e: Exception) {
            null
        }
    }
}