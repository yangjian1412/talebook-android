package com.talebook.app.data.api

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.webkit.CookieManager
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private var baseUrl = "https://book.liufenyi.xyz:9973/"
    private var api: TalebookApi? = null
    private var client: OkHttpClient? = null

    /**
     * 默认是内存 jar；TalebookApp.onCreate 调 [initialize] 后换成 PersistentCookieJar。
     * 类型用我们自己的 [CookieStorage]，多出来的 clear()/cookiesForHost() 都暴露。
     */
    private var cookieJar: CookieStorage = InMemoryCookieJar()

    val okHttpClient: OkHttpClient
        get() {
            if (client == null) client = buildClient()
            return client!!
        }

    /**
     * 必须在 [TalebookApp.onCreate] 第一时间调。把 cookieJar 换成持久化版本，
     * 否则冷启动后 SettingsRepository 看起来登录了但 jar 是空的，会发不出 cookie。
     * 调完后会强制 [okHttpClient] / [getApi] 重新构建，下一次访问就用持久化 jar。
     */
    @Synchronized
    fun initialize(context: Context) {
        val ctx = context.applicationContext
        if (cookieJar is PersistentCookieJar) {
            // 重复调用（比如配置变更或重组活）不重复创建
            return
        }
        cookieJar = PersistentCookieJar(ctx)
        client = null
        api = null
        android.util.Log.d("TaleInit", "RetrofitClient initialized with PersistentCookieJar")
    }

    fun updateBaseUrl(url: String) {
        val normalized = if (url.endsWith("/")) url else "$url/"
        if (normalized != baseUrl) {
            baseUrl = normalized
            api = null
            client = null
            clearCookies()
        }
    }

    fun currentBaseUrl(): String = baseUrl

    fun isCurrentHost(host: String?): Boolean {
        val url = baseUrl.toHttpUrlOrNull() ?: return false
        return host.equals(url.host, ignoreCase = true)
    }

    fun readUrl(bookId: Int): String = "${baseUrl.trimEnd('/')}/read/$bookId"

    fun cookieHeader(): String {
        val url = baseUrl.toHttpUrlOrNull() ?: return ""
        return cookieJar.cookiesForHost(url.host).joinToString("; ") { "${it.name}=${it.value}" }
    }

    fun syncCookiesToWebView() {
        val url = baseUrl.toHttpUrlOrNull() ?: return
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        val cookies = cookieJar.cookiesForHost(url.host)
        android.util.Log.d("TaleWeb", "syncCookiesToWebView host=${url.host} count=${cookies.size}")
        cookies.forEach { cookie ->
            val raw = cookie.toString()
            val hostOnly = "${cookie.name}=${cookie.value}; Path=/"
            val secureHostOnly = "${cookie.name}=${cookie.value}; Path=/; Secure"
            val domain = "${cookie.name}=${cookie.value}; Domain=${url.host}; Path=/"
            listOf(baseUrl, "${url.scheme}://${url.host}", "${url.scheme}://${url.host}:${url.port}").forEach { target ->
                cm.setCookie(target, raw)
                cm.setCookie(target, hostOnly)
                cm.setCookie(target, secureHostOnly)
                cm.setCookie(target, domain)
            }
        }
        cm.flush()
    }

    fun clearCookies() {
        cookieJar.clear()
    }

    private fun buildClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .cookieJar(cookieJar)
            .build()
    }

    fun getApi(): TalebookApi {
        if (api == null) {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            api = retrofit.create(TalebookApi::class.java)
        }
        return api!!
    }
}
