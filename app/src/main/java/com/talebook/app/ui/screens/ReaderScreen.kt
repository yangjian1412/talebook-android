package com.talebook.app.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import java.net.URL
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.talebook.app.data.api.RetrofitClient
import com.talebook.app.data.repository.SettingsRepository
import com.talebook.app.viewmodel.ReaderUiState
import com.talebook.app.viewmodel.ReaderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderScreen(
    bookId: Int,
    isFullscreen: Boolean,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val currentOnBack by rememberUpdatedState(onBack)
    val settingsRepository = remember(context) { SettingsRepository(context) }
    val themeMode by settingsRepository.themeMode.collectAsState(initial = SettingsRepository.THEME_AUTO)
    val isDark = when (themeMode) {
        SettingsRepository.THEME_LIGHT -> false
        SettingsRepository.THEME_DARK -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loadedWebReadUrl by remember { mutableStateOf<String?>(null) }

    if (isFullscreen) {
        DisposableEffect(activity) {
            val act = activity ?: return@DisposableEffect onDispose {}
            val window = act.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            val prevBehavior = controller.systemBarsBehavior
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                runCatching {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = prevBehavior
                }
            }
        }
    }

    LaunchedEffect(bookId) {
        loadedWebReadUrl = null
        viewModel.load(bookId)
    }

    BackHandler { currentOnBack() }

    if (isFullscreen) {
        Box(modifier = Modifier.fillMaxSize()) {
            ReaderBody(
                uiState = uiState,
                loadedWebReadUrl = loadedWebReadUrl,
                isDark = isDark,
                onWebView = { webView = it },
                onUrlLoaded = { loadedWebReadUrl = it }
            )
            IconButton(
                onClick = { currentOnBack() },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.4f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 8.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回"
                )
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            uiState.bookTitle.ifBlank { "阅读" },
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { currentOnBack() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                ReaderBody(
                    uiState = uiState,
                    loadedWebReadUrl = loadedWebReadUrl,
                    isDark = isDark,
                    onWebView = { webView = it },
                    onUrlLoaded = { loadedWebReadUrl = it }
                )
            }
        }
    }
}

private fun loadUrlWithCookies(view: WebView, url: String) {
    RetrofitClient.syncCookiesToWebView()
    val cookie = RetrofitClient.cookieHeader()
    if (cookie.isNotBlank()) {
        view.loadUrl(url, mapOf("Cookie" to cookie))
    } else {
        view.loadUrl(url)
    }
}

@Composable
private fun ReaderBody(
    uiState: ReaderUiState,
    loadedWebReadUrl: String?,
    isDark: Boolean,
    onWebView: (WebView?) -> Unit,
    onUrlLoaded: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.webReadUrl != null -> {
                AndroidView(
                    factory = { ctx ->
                        RetrofitClient.syncCookiesToWebView()
                        // talebook 的 CandleReader 用这几个 cookie：
                        //   theme_mode  = "day" / "night"  —— 工具栏那个"夜晚/白天"按钮的状态
                        //   theme       = "white"/"black"/"sepia"/"grey"  —— 实际渲染的 CSS 主题名
                        //   theme_day   = 日间主题 CSS 名（默认 white）
                        //   theme_night  = 夜间主题 CSS 名（默认 grey）
                        //   app_theme   = "light"/"dark"  —— SPA 整体（不是阅读器专用）
                        // app 切夜间 → 我们写 theme_mode=dark + theme=<对应夜间 CSS> + app_theme=dark。
                        // 多 URL × 多格式（跟 RetrofitClient.syncCookiesToWebView 一致），避免 Android 怪行为。
                        val modeValue = if (isDark) "dark" else "light"
                        // 夜间对应的 CSS 名 talebook 默认是 "grey"（前面加载设置日志显示 theme_night:"grey"）
                        // 先用 grey 作为兜底；用户如果在自己设置里换了别的，可以照样工作
                        val nightCss = "grey"
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        val cookieUrls = listOf(
                            RetrofitClient.currentBaseUrl(),
                            "https://book.liufenyi.xyz",
                            "https://book.liufenyi.xyz:9973"
                        )
                        fun setCookieMulti(line: String) {
                            cookieUrls.forEach { url ->
                                cm.setCookie(url, line)
                            }
                        }
                        // 写三组字段：theme_mode / theme / app_theme，每组 3 种格式
                        val cookies = listOf(
                            "theme_mode=$modeValue; Path=/; Max-Age=31536000",
                            "theme_mode=$modeValue; Path=/; Secure; Max-Age=31536000",
                            "theme_mode=$modeValue; Domain=book.liufenyi.xyz; Path=/; Max-Age=31536000"
                        )
                        setCookieMulti("theme=$nightCss; Path=/; Max-Age=31536000")
                        setCookieMulti("theme=$nightCss; Path=/; Secure; Max-Age=31536000")
                        setCookieMulti("theme=$nightCss; Domain=book.liufenyi.xyz; Path=/; Max-Age=31536000")
                        setCookieMulti("app_theme=$modeValue; Path=/; Max-Age=31536000")
                        setCookieMulti("app_theme=$modeValue; Path=/; Secure; Max-Age=31536000")
                        setCookieMulti("app_theme=$modeValue; Domain=book.liufenyi.xyz; Path=/; Max-Age=31536000")
                        cookies.forEach { setCookieMulti(it) }
                        cm.flush()
                        val verify = cm.getCookie("https://book.liufenyi.xyz:9973")
                        android.util.Log.d(
                            "TaleReader",
                            "reader cookies set: theme_mode=$modeValue theme=$nightCss app_theme=$modeValue (isDark=$isDark); getCookie=$verify"
                        )
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                builtInZoomControls = false
                                displayZoomControls = false
                            }
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = trustedTalebookWebViewClient(uiState.webReadUrl!!, isDark)
                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(msg: android.webkit.ConsoleMessage?): Boolean {
                                    android.util.Log.d("TaleWeb", "[${msg?.messageLevel()}] ${msg?.message()}")
                                    return true
                                }
                            }
                            onWebView(this)
                            onUrlLoaded(uiState.webReadUrl)
                            loadUrlWithCookies(this, uiState.webReadUrl!!)
                        }
                    },
                    update = { view ->
                        onWebView(view)
                        if (loadedWebReadUrl != uiState.webReadUrl) {
                            onUrlLoaded(uiState.webReadUrl)
                            loadUrlWithCookies(view, uiState.webReadUrl!!)
                        } else {
                            RetrofitClient.syncCookiesToWebView()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            uiState.error != null -> {
                Text(
                    text = "加载失败: ${uiState.error}",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@SuppressLint("WebViewClientOnReceivedSslError")
private fun trustedTalebookWebViewClient(
    targetReadUrl: String? = null,
    isDark: Boolean = false
): WebViewClient {
    val READ_PATH_REGEX = Regex("^/read/\\d+$")
    return object : WebViewClient() {
        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            val host = error?.url?.let { Uri.parse(it).host }
            if (RetrofitClient.isCurrentHost(host)) {
                android.util.Log.w("TaleWeb", "Trusted personal server SSL warning ignored: ${error?.url}")
                handler?.proceed()
            } else {
                handler?.cancel()
            }
        }

        override fun onSafeBrowsingHit(
            view: WebView?,
            request: WebResourceRequest?,
            threatType: Int,
            callback: SafeBrowsingResponse?
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && RetrofitClient.isCurrentHost(request?.url?.host)) {
                android.util.Log.w("TaleWeb", "Trusted personal server safe-browsing warning ignored: ${request?.url}")
                callback?.proceed(false)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                callback?.backToSafety(false)
            }
        }

        override fun shouldInterceptRequest(
            view: WebView?,
            request: WebResourceRequest?
        ): WebResourceResponse? {
            val reqUrl = request?.url ?: return null
            if (!"GET".equals(request.method, ignoreCase = true)) return null
            val path = reqUrl.path ?: return null

            val fileName = path.substringAfterLast('/')
            if (path.startsWith("/static/candle-reader/") && fileName.startsWith("candle-reader") && fileName.endsWith(".js")) {
                val urlStr = reqUrl.toString()
                return try {
                    val conn = URL(urlStr).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    val js = conn.inputStream.bufferedReader().use { it.readText() }
                    val patchedJs = js.replace("show_navbar: !0", "show_navbar: !1")
                    android.util.Log.d("TaleWeb", "CandleReader navbar default patched")
                    WebResourceResponse("application/javascript", "UTF-8", patchedJs.byteInputStream())
                } catch (e: Exception) {
                    android.util.Log.w("TaleWeb", "CandleReader patch failed: ${e.message}")
                    null
                }
            }

            if (path.startsWith("/static/candle-reader/js/") && fileName.startsWith("epub") && fileName.endsWith(".js")) {
                val urlStr = reqUrl.toString()
                return try {
                    val conn = URL(urlStr).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 15000
                    conn.readTimeout = 15000
                    val js = conn.inputStream.bufferedReader().use { it.readText() }
                    val lineOverlap = """
                      (function(manager){
                        try {
                          var view = manager.views && manager.views.length ? manager.views.first() : null;
                          var doc = view && view.document;
                          var win = doc && doc.defaultView;
                          var body = doc && doc.body;
                          if (!win || !body) return 0;
                          var style = win.getComputedStyle(body);
                          var lineHeight = parseFloat(style.lineHeight);
                          if (!isFinite(lineHeight)) {
                            var fontSize = parseFloat(style.fontSize) || 18;
                            lineHeight = fontSize * 1.5;
                          }
                          return lineHeight * 2;
                        } catch (e) {
                          return 0;
                        }
                      })(this)
                    """.trimIndent().replace("\n", "")
                    val continuousMarker = "class continuous_ContinuousViewManager extends"
                    val continuousStart = js.indexOf(continuousMarker)
                    val patchedJs = if (continuousStart >= 0) {
                        val before = js.substring(0, continuousStart)
                        val continuous = js.substring(continuousStart)
                            .replace(
                                "this.scrollBy(0, this.layout.height, true);",
                                "this.scrollBy(0, Math.max(0, this.layout.height - $lineOverlap), true);"
                            )
                            .replace(
                                "this.scrollBy(0, -this.layout.height, true);",
                                "this.scrollBy(0, -Math.max(0, this.layout.height - $lineOverlap), true);"
                            )
                        before + continuous
                    } else {
                        js
                    }
                    android.util.Log.d("TaleWeb", "EPUB.js scrolled page overlap patched")
                    WebResourceResponse("application/javascript", "UTF-8", patchedJs.byteInputStream())
                } catch (e: Exception) {
                    android.util.Log.w("TaleWeb", "EPUB.js patch failed: ${e.message}")
                    null
                }
            }

            if (!request.isForMainFrame) return null
            if (!READ_PATH_REGEX.matches(path)) return null
            val urlStr = reqUrl.toString()

            android.util.Log.d("TaleWeb", "Intercepting reader page: $urlStr")
            try {
                val cookie = CookieManager.getInstance().getCookie(urlStr)
                if (cookie.isNullOrBlank()) {
                    android.util.Log.w("TaleWeb", "No cookies available for reader page, falling back")
                    return null
                }
                val conn = URL(urlStr).openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("Cookie", cookie)
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.instanceFollowRedirects = false

                val responseCode = conn.responseCode
                if (responseCode == java.net.HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == java.net.HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == java.net.HttpURLConnection.HTTP_SEE_OTHER) {
                    android.util.Log.d("TaleWeb", "Redirect ($responseCode) — letting WebView follow natively")
                    conn.disconnect()
                    return null
                }
                if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
                    android.util.Log.w("TaleWeb", "Unexpected response code $responseCode for $urlStr, falling back")
                    conn.disconnect()
                    return null
                }

                val html = conn.inputStream.bufferedReader().use { it.readText() }

                val (themeVal, modeVal, appVal) = if (isDark) 
                    Triple("\"grey\"", "\"night\"", "\"dark\"") 
                else 
                    Triple("\"white\"", "\"day\"", "\"light\"")

                val injectScript = """
                  <script>
                  (function(){
                    var s = JSON.parse(localStorage.getItem("readerSettings") || "{}");
                    s.theme = $themeVal;
                    s.theme_mode = $modeVal;
                    s.app_theme = $appVal;
                    s.theme_day = "white";
                    s.theme_night = "grey";
                    if (!s.flow) s.flow = "paginated";
                    localStorage.setItem("readerSettings", JSON.stringify(s));
                    console.log('TaleThemeInjected:${if(isDark)"dark" else "light"}');
                  })();
                  </script>
                """.trimIndent()
                val moduleScriptRegex = Regex("""<script\s+type=["']module["']>""")
                val modifiedHtml = moduleScriptRegex.replaceFirst(html, injectScript + """<script type="module">""")
                android.util.Log.d("TaleWeb", "Theme injected into reader page (dark=$isDark)")
                return WebResourceResponse("text/html", "UTF-8", modifiedHtml.byteInputStream())
            } catch (e: Exception) {
                android.util.Log.w("TaleWeb", "Intercept failed: ${e.message}")
                return null
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            val current = url?.let { Uri.parse(it) } ?: return
            if (!RetrofitClient.isCurrentHost(current.host)) return

            if (current.toString().contains("viewer.html?file=") && isDark) {
                val darkCss = """
                    (function(){
                        var s = document.createElement('style');
                        s.textContent = `:root {
                            --toolbar-bg-color: #2a2a2e !important;
                            --toolbar-border-color: #3a3a3e !important;
                            --sidebar-bg-color: #2a2a2e !important;
                            --body-bg-color: #1a1a1e !important;
                            --page-bg-color: #3a3a3e !important;
                            --field-bg-color: #3a3a3e !important;
                            --field-color: #e0e0e0 !important;
                            --doorhanger-bg-color: #2a2a2e !important;
                            --doorhanger-border-color: #3a3a3e !important;
                            --doorhanger-fg-color: #e0e0e0 !important;
                            --doorhanger-hover-color: #3a3a3e !important;
                            --dialog-bg-color: #2a2a2e !important;
                            --dialog-border-color: #3a3a3e !important;
                        }
                        .pdfViewer .page { background: #1a1a1e !important; }
                        .pdfViewer canvas { filter: invert(0.92) hue-rotate(180deg) !important; }`;
                        document.head.appendChild(s);
                        console.log('TalePDFDarkMode:injected');
                    })();
                """.trimIndent()
                view?.evaluateJavascript(darkCss, null)
                android.util.Log.d("TaleWeb", "PDF.js dark mode injected")
            }

            // 如果错跳到了首页（典型情况：用户未登录被服务器 302 到 / 然后又被带去 /，但读者没产生 cookie）
            // 重新定向到目标阅读页
            val path = current.path.orEmpty().trimEnd('/')
            val isHome = path.isBlank() || path == "/index" || path == "/book" || path == "/library"
            val isLogin = path.contains("login") || path.contains("sign_in") || path.contains("user")
            if (isHome && !isLogin) {
                val target = targetReadUrl ?: return
                android.util.Log.d("TaleWeb", "Web login landed on home, redirecting back to reader: $targetReadUrl")
                RetrofitClient.syncCookiesToWebView()
                view?.post { view.loadUrl(targetReadUrl) }
                return
            }
        }
    }
}
