package com.talebook.app.data.api

import okhttp3.Cookie
import okhttp3.CookieJar

/**
 * 我们自己 cookie 存储需要的行为：在 OkHttp 的 CookieJar 基础上额外提供
 * 「按 host 拿全部 cookie」和「全部清空」。给 RetrofitClient 一个统一的类型。
 */
interface CookieStorage : CookieJar {
    fun cookiesForHost(host: String): List<Cookie>
    fun clear()
}
