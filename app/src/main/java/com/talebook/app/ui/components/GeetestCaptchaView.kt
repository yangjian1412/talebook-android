package com.talebook.app.ui.components

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.talebook.app.data.repository.GeetestParams

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GeetestCaptchaView(
    captchaId: String,
    modifier: Modifier = Modifier,
    onSuccess: (GeetestParams) -> Unit,
    onError: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                allowFileAccess = true
                allowContentAccess = true
            }
            webViewClient = WebViewClient()
            addJavascriptInterface(
                GeetestJsBridge(
                    onSuccessJson = { json ->
                        val parsed = runCatching {
                            val obj = org.json.JSONObject(json)
                            GeetestParams(
                                lotNumber = obj.optString("lot_number"),
                                captchaOutput = obj.optString("captcha_output"),
                                passToken = obj.optString("pass_token"),
                                genTime = obj.optString("gen_time")
                            )
                        }.getOrElse { GeetestParams("", "", "", "") }
                        if (parsed.isComplete) {
                            onSuccess(parsed)
                        } else {
                            onError("极验返回参数不完整")
                        }
                    },
                    onErrorMsg = onError,
                    onClose = onClose
                ),
                "AndroidBridge"
            )
            loadUrl("file:///android_asset/geetest.html")
        }
    }

    DisposableEffect(captchaId) {
        if (captchaId.isNotBlank()) {
            webView.evaluateJavascript("window.startGeetest('$captchaId');", null)
        }
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
    )
}

private class GeetestJsBridge(
    private val onSuccessJson: (String) -> Unit,
    private val onErrorMsg: (String) -> Unit,
    private val onClose: () -> Unit
) {
    @JavascriptInterface
    fun onSuccess(json: String) {
        onSuccessJson(json)
    }

    @JavascriptInterface
    fun onError(msg: String) {
        onErrorMsg(msg)
    }

    @JavascriptInterface
    fun onClose() {
        onClose()
    }
}