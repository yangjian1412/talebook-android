package com.talebook.app.data.opds

import okhttp3.OkHttpClient
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

object OpdsClient {
    private val cache = mutableMapOf<String, OkHttpClient>()

    fun client(baseUrl: String, basicUser: String, basicPass: String): OkHttpClient {
        val key = "${baseUrl}|${basicUser}|${basicPass.hashCode()}"
        return cache.getOrPut(key) {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(BasicAuthInterceptor(basicUser, basicPass))
                .build()
        }
    }

    fun rootUrl(baseUrl: String): String {
        val trimmed = baseUrl.trimEnd('/')
        return "$trimmed/opds"
    }

    private class BasicAuthInterceptor(private val user: String, private val pass: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val req: Request = if (user.isNotBlank()) {
                chain.request().newBuilder()
                    .header("Authorization", Credentials.basic(user, pass))
                    .build()
            } else chain.request()
            return chain.proceed(req)
        }
    }
}