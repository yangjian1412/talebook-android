package com.talebook.app.util

import com.talebook.app.data.api.RetrofitClient

/**
 * 把服务器返回的相对路径（如 "/get/cover/1.jpg"）拼上 baseUrl
 * 如果已经是 http(s):// 开头则原样返回
 */
fun resolveUrl(path: String?): String {
    if (path.isNullOrBlank()) return ""
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    val base = RetrofitClient.currentBaseUrl().trimEnd('/')
    return if (path.startsWith("/")) "$base$path" else "$base/$path"
}
