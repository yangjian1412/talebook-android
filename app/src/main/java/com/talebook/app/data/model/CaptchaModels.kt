package com.talebook.app.data.model

import com.google.gson.annotations.SerializedName

data class CaptchaConfigResponse(
    @SerializedName("err") val err: String,
    @SerializedName("config") val config: CaptchaConfig?
)

data class CaptchaConfig(
    @SerializedName("provider") val provider: String,
    @SerializedName("captchaId") val captchaId: String = "",
    @SerializedName("sdkUrl") val sdkUrl: String = "",
    @SerializedName("enabled") val enabled: Boolean = false,
    @SerializedName("scenes") val scenes: CaptchaScenes = CaptchaScenes()
)

data class CaptchaScenes(
    @SerializedName("login") val login: Boolean = false,
    @SerializedName("welcome") val welcome: Boolean = false,
    @SerializedName("register") val register: Boolean = false,
    @SerializedName("reset") val reset: Boolean = false
)

data class CaptchaImageResponse(
    @SerializedName("err") val err: String,
    @SerializedName("captcha_id") val captchaId: String = "",
    @SerializedName("image") val image: String = ""
)