package com.talebook.app.data.opds

import okhttp3.Request
import java.io.IOException

data class OpdsBrowseResult(
    val feed: OpdsFeed,
    val books: List<com.talebook.app.data.model.Book>
)

class OpdsRepository(
    private val baseUrl: String,
    private val basicUser: String,
    private val basicPass: String
) {
    private val client get() = OpdsClient.client(baseUrl, basicUser, basicPass)
    private val rootUrl get() = OpdsClient.rootUrl(baseUrl)

    suspend fun discoverRoot(): String {
        val candidates = listOf("$baseUrl/opds", "$baseUrl/opds/", "$baseUrl/")
        for (c in candidates) {
            try {
                val req = Request.Builder().url(c).build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful && (resp.header("Content-Type") ?: "").contains("atom", ignoreCase = true)) {
                        return c.trimEnd('/')
                    }
                }
            } catch (_: IOException) {
            }
        }
        return rootUrl
    }

    suspend fun browse(url: String? = null): Result<OpdsBrowseResult> = runCatching {
        val target = url ?: discoverRoot()
        val req = Request.Builder().url(target).build()
        val xml = client.newCall(req).execute().use { resp ->
            @Suppress("DEPRECATION")
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
        val feed = OpdsXmlParser.parseFeed(xml)
        OpdsBrowseResult(feed = feed, books = OpdsXmlParser.entriesToBooks(feed.entries, baseUrl))
    }

    suspend fun fetchPage(url: String): Result<OpdsBrowseResult> = runCatching {
        val req = Request.Builder().url(url).build()
        val xml = client.newCall(req).execute().use { resp ->
            @Suppress("DEPRECATION")
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
        val feed = OpdsXmlParser.parseFeed(xml)
        OpdsBrowseResult(feed = feed, books = OpdsXmlParser.entriesToBooks(feed.entries, baseUrl))
    }

    suspend fun search(query: String): Result<OpdsBrowseResult> = runCatching {
        val req = Request.Builder().url("$rootUrl/search/${java.net.URLEncoder.encode(query, "UTF-8")}").build()
        val xml = client.newCall(req).execute().use { resp ->
            @Suppress("DEPRECATION")
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }
        val feed = OpdsXmlParser.parseFeed(xml)
        OpdsBrowseResult(feed = feed, books = OpdsXmlParser.entriesToBooks(feed.entries, baseUrl))
    }

    suspend fun testConnection(): Result<Unit> = runCatching {
        val req = Request.Builder().url(discoverRoot()).build()
        client.newCall(req).execute().use { resp ->
            @Suppress("DEPRECATION")
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
        }
    }
}