package com.talebook.app.data.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.talebook.app.data.model.ReadState
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import android.webkit.CookieManager
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private var baseUrl = ""
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

    fun updateBaseUrl(url: String, clearCookies: Boolean = false) {
        val normalized = if (url.isBlank()) "" else if (url.endsWith("/")) url else "$url/"
        if (normalized != baseUrl) {
            baseUrl = normalized
            api = null
            client = null
            if (clearCookies) clearCookies()
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

    fun clearCurrentHostCookies() {
        val url = baseUrl.toHttpUrlOrNull() ?: return
        cookieJar.clearHost(url.host)
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
                .addConverterFactory(GsonConverterFactory.create(buildLenientGson()))
                .build()
            api = retrofit.create(TalebookApi::class.java)
        }
        return api!!
    }

    /**
     * Builds a Gson instance that tolerates server API inconsistencies.
     * - talebook/mybooks `ReadState` returns `favorite` / `wants` as either
     *   a JSON number (0/1, from the list endpoints) or a JSON boolean
     *   (true/false, from the single-book endpoint). Both must be accepted.
     * - JSON 数字 / 布尔兼容：服务端在两个端点返回不同类型，必须都接受。
     */
    private fun buildLenientGson(): Gson = GsonBuilder()
        .registerTypeAdapter(ReadState::class.java, ReadStateDeserializer)
        .setLenient()
        .create()

    private object ReadStateDeserializer : JsonDeserializer<ReadState> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): ReadState {
            val obj = json.asJsonObject
            return ReadState(
                page = obj.get("page")?.asInt ?: 0,
                percentage = obj.get("percentage")?.asDouble ?: 0.0,
                updated = obj.get("updated")?.asString.orEmpty(),
                favorite = readFlagLikeInt(obj.get("favorite")),
                wants = readFlagLikeInt(obj.get("wants")),
                readState = obj.get("read_state")?.asInt ?: 0
            )
        }

        private fun readFlagLikeInt(elem: JsonElement?): Int = when {
            elem == null || elem.isJsonNull -> 0
            elem.isJsonPrimitive -> when (val prim = elem.asJsonPrimitive) {
                is JsonPrimitive -> when {
                    prim.isBoolean -> if (prim.asBoolean) 1 else 0
                    prim.isNumber -> prim.asInt.coerceIn(0, 1)
                    prim.isString -> if (prim.asString.equals("true", ignoreCase = true) || prim.asString == "1") 1 else 0
                    else -> 0
                }
                else -> 0
            }
            else -> 0
        }
    }
}
