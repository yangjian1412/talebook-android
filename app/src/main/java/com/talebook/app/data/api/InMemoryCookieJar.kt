package com.talebook.app.data.api

import okhttp3.Cookie
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * 简单的内存 CookieJar：按 host 缓存 Cookie。进程死了就丢。
 * 现在已经被 PersistentCookieJar 取代，但仍保留用作 fallback / 测试。
 */
class InMemoryCookieJar : CookieStorage {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val existing = store[host] ?: mutableListOf()
        for (cookie in cookies) {
            existing.removeAll { it.name == cookie.name }
            existing.add(cookie)
        }
        store[host] = existing
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val now = System.currentTimeMillis()
        return store[host]?.filter { it.expiresAt > now } ?: emptyList()
    }

    override fun clear() {
        store.clear()
    }

    override fun clearHost(host: String) {
        store.remove(host)
    }

    override fun cookiesForHost(host: String): List<Cookie> {
        val now = System.currentTimeMillis()
        return store[host]?.filter { it.expiresAt > now } ?: emptyList()
    }
}
