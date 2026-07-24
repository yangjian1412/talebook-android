package com.talebook.app.data.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Cookie
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 持久化 CookieJar：用 SharedPreferences 把 cookie 按 host 存下来。
 * - 构造时一次性读取所有 cookie 到内存 map（之后都在内存里走）
 * - 每次 saveFromResponse 同步刷 SharedPreferences（合并去重、按 expiresAt 过滤）
 * - App 重启后构造时会把过期的丢掉，仍有效的带回内存
 *
 * 解决「冷启动后 SettingsRepository 说登录了但 jar 是空的」导致的请求不带 cookie 问题。
 */
class PersistentCookieJar(context: Context) : CookieStorage {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        load()
    }

    private fun load() {
        val json = prefs.getString(KEY_COOKIES, "") ?: ""
        if (json.isBlank()) return
        runCatching {
            val type = object : TypeToken<Map<String, List<Cookie>>>() {}.type
            val saved: Map<String, List<Cookie>> = gson.fromJson(json, type) ?: return
            val now = System.currentTimeMillis()
            saved.forEach { (host, cookies) ->
                val valid = cookies.filter { it.expiresAt > now }
                if (valid.isNotEmpty()) store[host] = valid.toMutableList()
            }
        }
    }

    private fun persist() {
        val now = System.currentTimeMillis()
        val sanitized = store.mapValues { (_, v) -> v.filter { it.expiresAt > now } }
        val json = gson.toJson(sanitized)
        prefs.edit().putString(KEY_COOKIES, json).apply()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val existing = store[host] ?: mutableListOf()
        for (c in cookies) {
            existing.removeAll { it.name == c.name }
            existing.add(c)
        }
        store[host] = existing
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val now = System.currentTimeMillis()
        return store[host]?.filter { it.expiresAt > now } ?: emptyList()
    }

    override fun cookiesForHost(host: String): List<Cookie> {
        val now = System.currentTimeMillis()
        return store[host]?.filter { it.expiresAt > now } ?: emptyList()
    }

    override fun clear() {
        store.clear()
        persist()
    }

    override fun clearHost(host: String) {
        store.remove(host)
        persist()
    }

    companion object {
        private const val PREFS_NAME = "talebook_cookies"
        private const val KEY_COOKIES = "cookies_json"
    }
}
