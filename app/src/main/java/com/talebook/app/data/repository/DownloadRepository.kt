package com.talebook.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.talebook.app.data.api.RetrofitClient
import com.talebook.app.data.model.BookDetail
import com.talebook.app.util.resolveUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

sealed interface DownloadEvent {
    data class Started(val displayName: String, val totalBytes: Long) : DownloadEvent
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : DownloadEvent
    data class Done(val displayName: String, val relativePath: String) : DownloadEvent
    data class Failed(val message: String) : DownloadEvent
}

private sealed interface SaveOutcome {
    data class Success(val displayName: String, val relativePath: String) : SaveOutcome
    data class Failure(val message: String) : SaveOutcome
}

class DownloadRepository {
    // 单独一个 OkHttpClient 给下载用：不带 CookieJar，每次手动塞 Cookie header，
    // 这样可以确保 cookie 的发送方式完全确定，避免自动 cookie 管理在某些边界场景下漏掉。
    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun downloadBookAsFlow(
        context: Context,
        bookId: Int,
        book: BookDetail
    ): Flow<DownloadEvent> = channelFlow {
        val chosen = pickFile(book)
        if (chosen == null) {
            send(
                DownloadEvent.Failed(
                    "该书无可下载格式 (files=${book.files.map { "${it.format}(${it.size}B, ${it.href})" }}, " +
                            "available_formats='${book.availableFormats}', " +
                            "fmt_epub='${book.fmtEpub}', fmt_pdf='${book.fmtPdf}', " +
                            "fmt_azw3='${book.fmtAzw3}', fmt_mobi='${book.fmtMobi}')"
                )
            )
            awaitClose { }
            return@channelFlow
        }

        val (ext, href) = chosen
        val url = resolveUrl(href)
        val displayName = sanitizeFileName(book.title) + "." + ext
        val mimeType = mimeFromExt(ext)

        val httpUrl = url.toHttpUrlOrNull() ?: run {
            send(DownloadEvent.Failed("下载 URL 解析失败: $url"))
            awaitClose { }
            return@channelFlow
        }

        val cookieHeaderStr = RetrofitClient.cookieHeader()
        val cookieNames = cookieHeaderStr
            .split("; ")
            .mapNotNull { it.substringBefore("=", "").takeIf { n -> n.isNotBlank() } }
        android.util.Log.d(
            "TaleDownload",
            "download url=$url host=${httpUrl.host} " +
                    "cookieHeaderLen=${cookieHeaderStr.length} cookieNames=$cookieNames"
        )

        val requestBuilder = Request.Builder().url(httpUrl)
        if (cookieHeaderStr.isNotBlank()) {
            requestBuilder.addHeader("Cookie", cookieHeaderStr)
        }
        val request = requestBuilder.build()

        val response = try {
            withContext(Dispatchers.IO) {
                downloadClient.newCall(request).execute()
            }
        } catch (e: Exception) {
            send(DownloadEvent.Failed("网络错误: ${e.message ?: "未知"}"))
            awaitClose { }
            return@channelFlow
        }

        try {
            if (!response.isSuccessful) {
                val msg = when (response.code) {
                    403 -> "下载失败: 服务器未启用下载权限 (HTTP 403)"
                    404 -> "下载失败: 服务器上没有该书的此格式 (HTTP 404)"
                    else -> "下载失败 (HTTP ${response.code})"
                }
                send(DownloadEvent.Failed(msg))
                return@channelFlow
            }
            val body = response.body
            if (body == null) {
                send(DownloadEvent.Failed("响应为空"))
                return@channelFlow
            }

            val actualMime = body.contentType()?.toString().orEmpty()
            if (actualMime.startsWith("text/html") ||
                actualMime.startsWith("application/xhtml") ||
                actualMime.startsWith("text/plain")
            ) {
                send(
                    DownloadEvent.Failed(
                        "服务器返回了 HTML 而不是书文件 (Content-Type=$actualMime)。" +
                                "通常是因为：(1) 当前未登录 / 登录已过期，请重新登录；" +
                                "(2) 服务器端 allow.download=false，需要管理员开启下载权限。"
                    )
                )
                return@channelFlow
            }

            val totalBytes = body.contentLength().takeIf { it > 0 } ?: -1L
            send(DownloadEvent.Started(displayName, totalBytes))

            val outcome: SaveOutcome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeViaMediaStore(context, displayName, mimeType, body.byteStream()) { d ->
                    trySend(DownloadEvent.Progress(d, totalBytes))
                }
            } else {
                writeViaLegacyFile(displayName, body.byteStream()) { d ->
                    trySend(DownloadEvent.Progress(d, totalBytes))
                }
            }

            when (outcome) {
                is SaveOutcome.Success -> send(DownloadEvent.Done(outcome.displayName, outcome.relativePath))
                is SaveOutcome.Failure -> send(DownloadEvent.Failed(outcome.message))
            }
        } finally {
            response.close()
        }

        awaitClose { }
    }

    fun pickFormat(book: BookDetail): String? = pickFile(book)?.first

    /**
     * 返回选中的 (lowercase ext, href) 二元组，按 EPUB > PDF > AZW3 > MOBI 优先级；
     * 找不到任何可用格式则 null。
     * 优先看 talebook 服务端 `files` 数组里的 `format` + `href`，其次才退回 `available_formats` 和 `fmt_*`。
     */
    fun pickFile(book: BookDetail): Pair<String, String>? {
        val formatsByPriority = mutableListOf<Pair<String, String>>()

        book.files.forEach { file ->
            val fmt = file.format.trim().lowercase()
            val href = file.href
            if (fmt.isNotBlank() && href.isNotBlank()) {
                formatsByPriority.add(fmt to href)
            }
        }

        if (formatsByPriority.isEmpty()) {
            if (book.availableFormats.isNotBlank()) {
                book.availableFormats
                    .split(',', '，', ' ', '/', '\n', '\t', ';', '；')
                    .map { it.trim().lowercase() }
                    .filter { it.isNotBlank() }
                    .forEach { formatsByPriority.add(it to "") }
            }
            if (book.fmtEpub.isNotBlank()) formatsByPriority.add("epub" to "")
            if (book.fmtPdf.isNotBlank()) formatsByPriority.add("pdf" to "")
            if (book.fmtAzw3.isNotBlank()) formatsByPriority.add("azw3" to "")
            if (book.fmtMobi.isNotBlank()) formatsByPriority.add("mobi" to "")
        }

        android.util.Log.d(
            "TaleDownload",
            "pickFile: id=${book.id} files=${book.files.map { "${it.format}:${it.href}" }} " +
                    "→ resolved=$formatsByPriority"
        )

        val priority = listOf("epub", "pdf", "azw3", "mobi")
        for (fmt in priority) {
            val match = formatsByPriority.firstOrNull { it.first == fmt }
            if (match != null) {
                val (ext, serverHref) = match
                val href = serverHref.ifBlank {
                    "${RetrofitClient.currentBaseUrl().trimEnd('/')}/api/book/${book.id}.${ext}"
                }
                return ext to href
            }
        }
        return null
    }

    private fun mimeFromExt(ext: String): String = when (ext.lowercase()) {
        "epub" -> "application/epub+zip"
        "pdf" -> "application/pdf"
        "azw3" -> "application/octet-stream"
        "mobi" -> "application/x-mobipocket-ebook"
        else -> "application/octet-stream"
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_").trim().take(120).ifBlank { "book" }

    private fun writeViaMediaStore(
        context: Context,
        displayName: String,
        mimeType: String,
        input: InputStream,
        onBytes: (Long) -> Unit
    ): SaveOutcome {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/talebook"
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val itemUri = try {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } catch (e: Exception) {
            return SaveOutcome.Failure("无法创建下载文件: ${e.message ?: "未知"}")
        } ?: return SaveOutcome.Failure("无法创建下载文件 (insert 返回 null)")

        var success = false
        var failureMsg: String? = null
        try {
            resolver.openOutputStream(itemUri)?.use { output ->
                val buffer = ByteArray(8 * 1024)
                var copied = 0L
                var lastReport = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    copied += read
                    val now = System.currentTimeMillis()
                    if (now - lastReport > 200) {
                        lastReport = now
                        onBytes(copied)
                    }
                }
                onBytes(copied)
                output.flush()
                success = true
            } ?: run {
                failureMsg = "无法打开输出流"
            }
        } catch (e: Exception) {
            failureMsg = "写入失败: ${e.message ?: "未知"}"
        }

        return if (success) {
            try {
                val update = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                resolver.update(itemUri, update, null, null)
            } catch (_: Throwable) {
            }
            SaveOutcome.Success(displayName, "Download/talebook")
        } else {
            try {
                resolver.delete(itemUri, null, null)
            } catch (_: Throwable) {
            }
            SaveOutcome.Failure(failureMsg ?: "未知失败")
        }
    }

    @Suppress("DEPRECATION")
    private fun writeViaLegacyFile(
        displayName: String,
        input: InputStream,
        onBytes: (Long) -> Unit
    ): SaveOutcome {
        return try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val talebookDir = File(downloads, "talebook")
            if (!talebookDir.exists()) talebookDir.mkdirs()
            val outFile = File(talebookDir, displayName)
            FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(8 * 1024)
                var copied = 0L
                var lastReport = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    copied += read
                    val now = System.currentTimeMillis()
                    if (now - lastReport > 200) {
                        lastReport = now
                        onBytes(copied)
                    }
                }
                onBytes(copied)
                output.flush()
            }
            SaveOutcome.Success(displayName, "Download/talebook")
        } catch (e: Exception) {
            SaveOutcome.Failure("写入失败: ${e.message ?: "未知"}")
        }
    }
}
