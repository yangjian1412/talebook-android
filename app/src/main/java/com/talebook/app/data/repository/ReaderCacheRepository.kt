package com.talebook.app.data.repository

import android.content.Context
import com.talebook.app.data.api.RetrofitClient
import com.talebook.app.data.local.ReaderCacheEntity
import com.talebook.app.data.local.ReaderDatabase
import com.talebook.app.data.model.BookDetail
import com.talebook.app.util.resolveUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import java.util.concurrent.TimeUnit

data class ReaderCacheResult(
    val file: File,
    val format: String,
    val sizeBytes: Long
)

data class ReaderReadableSource(
    val uri: String,
    val format: String,
    val isCached: Boolean,
    val sizeBytes: Long = 0L
)

data class ReaderCacheInfo(
    val isCached: Boolean,
    val format: String = "",
    val sizeBytes: Long = 0L
)

data class ReaderCachedBook(
    val bookId: Int,
    val title: String,
    val format: String,
    val sizeBytes: Long,
    val lastAccessedAt: Long
)

sealed interface ReaderCacheEvent {
    data class Started(val totalBytes: Long) : ReaderCacheEvent
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : ReaderCacheEvent
    data class Done(val result: ReaderCacheResult) : ReaderCacheEvent
}

class ReaderCacheRepository {
    private val downloadRepository = DownloadRepository()
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun resolveReadableSource(context: Context, book: BookDetail): Result<ReaderReadableSource> = withContext(Dispatchers.IO) {
        val serverId = activeServerId(context)
        val dao = ReaderDatabase.get(context).readerDao()
        val existing = dao.getCache(serverId, book.id)
        if (existing != null) {
            val file = File(existing.filePath)
            if (file.exists() && file.length() > 0) {
                dao.touchCache(serverId, book.id, System.currentTimeMillis())
                return@withContext Result.success(
                    ReaderReadableSource(
                        uri = file.toURI().toString(),
                        format = existing.format,
                        isCached = true,
                        sizeBytes = file.length()
                    )
                )
            }
            dao.deleteCache(serverId, book.id)
        }

        val source = resolveReadableSource(book)
            ?: return@withContext Result.failure(Exception("没有可供本地阅读的格式"))
        Result.success(
            ReaderReadableSource(
                uri = source.second,
                format = source.first,
                isCached = false
            )
        )
    }

    suspend fun ensureCached(context: Context, book: BookDetail): Result<ReaderCacheResult> = cacheBook(context, book)

    fun cacheBookAsFlow(context: Context, book: BookDetail): Flow<ReaderCacheEvent> = flow {
        val appCtx = context.applicationContext
        val serverId = activeServerId(appCtx)
        val dao = ReaderDatabase.get(appCtx).readerDao()
        val existing = dao.getCache(serverId, book.id)
        if (existing != null) {
            val file = File(existing.filePath)
            if (file.exists() && file.length() > 0) {
                dao.touchCache(serverId, book.id, System.currentTimeMillis())
                emit(ReaderCacheEvent.Done(ReaderCacheResult(file = file, format = existing.format, sizeBytes = file.length())))
                return@flow
            }
            dao.deleteCache(serverId, book.id)
        }

        val source = resolveReadableSource(book) ?: error("没有可供缓存的格式")
        val (format, url) = source
        val httpUrl = url.toHttpUrlOrNull() ?: error("本地阅读下载地址无效: $url")
        val bookDir = bookDir(appCtx, book.id)
        if (!bookDir.exists()) bookDir.mkdirs()
        val file = File(bookDir, "book.$format.part")
        val finalFile = File(bookDir, "book.$format")
        finalFile.delete()
        val requestBuilder = Request.Builder().url(httpUrl)
        RetrofitClient.cookieHeader().takeIf { it.isNotBlank() }?.let { requestBuilder.addHeader("Cookie", it) }
        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) error("缓存失败 HTTP ${response.code}")
                val body = response.body ?: error("缓存失败: 响应为空")
                val actualMime = body.contentType()?.toString().orEmpty()
                if (actualMime.startsWith("text/html") || actualMime.startsWith("text/plain")) {
                    error("服务器返回 HTML，不是书籍文件，请检查登录状态或服务器转换状态")
                }
                val total = body.contentLength().takeIf { it > 0 } ?: -1L
                emit(ReaderCacheEvent.Started(total))
                var copied = 0L
                var lastReport = 0L
                file.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            copied += read
                            val now = System.currentTimeMillis()
                            if (now - lastReport > 200) {
                                lastReport = now
                                emit(ReaderCacheEvent.Progress(copied, total))
                            }
                        }
                    }
                }
                emit(ReaderCacheEvent.Progress(copied, total))
            }
            if (!file.renameTo(finalFile)) error("缓存文件保存失败")
            val now = System.currentTimeMillis()
            val size = finalFile.length()
            dao.saveCache(ReaderCacheEntity(serverId, book.id, book.title, format, finalFile.absolutePath, size, url, now, now))
            emit(ReaderCacheEvent.Done(ReaderCacheResult(finalFile, format, size)))
        } catch (e: kotlinx.coroutines.CancellationException) {
            file.delete()
            finalFile.delete()
            dao.deleteCache(serverId, book.id)
            throw e
        } catch (e: Exception) {
            file.delete()
            finalFile.delete()
            dao.deleteCache(serverId, book.id)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    suspend fun cacheBook(context: Context, book: BookDetail): Result<ReaderCacheResult> = withContext(Dispatchers.IO) {
        val serverId = activeServerId(context)
        val dao = ReaderDatabase.get(context).readerDao()
        val existing = dao.getCache(serverId, book.id)
        if (existing != null) {
            val file = File(existing.filePath)
            if (file.exists() && file.length() > 0) {
                dao.touchCache(serverId, book.id, System.currentTimeMillis())
                return@withContext Result.success(
                    ReaderCacheResult(file = file, format = existing.format, sizeBytes = file.length())
                )
            }
            dao.deleteCache(serverId, book.id)
        }

        val source = resolveReadableSource(book)
            ?: return@withContext Result.failure(Exception("没有可供缓存的格式"))
        val (format, url) = source
        val httpUrl = url.toHttpUrlOrNull()
            ?: return@withContext Result.failure(Exception("本地阅读下载地址无效: $url"))

        val bookDir = bookDir(context, book.id)
        if (!bookDir.exists()) bookDir.mkdirs()
        val file = File(bookDir, "book.$format")
        val requestBuilder = Request.Builder().url(httpUrl)
        RetrofitClient.cookieHeader().takeIf { it.isNotBlank() }?.let { cookie ->
            requestBuilder.addHeader("Cookie", cookie)
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("缓存失败 HTTP ${response.code}"))
                }
                val body = response.body
                    ?: return@withContext Result.failure(Exception("缓存失败: 响应为空"))
                val actualMime = body.contentType()?.toString().orEmpty()
                if (actualMime.startsWith("text/html") || actualMime.startsWith("text/plain")) {
                    return@withContext Result.failure(Exception("服务器返回 HTML，不是书籍文件，请检查登录状态或服务器转换状态"))
                }
                file.outputStream().use { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                }
            }
            val now = System.currentTimeMillis()
            val size = file.length()
            dao.saveCache(
                ReaderCacheEntity(
                    serverId = serverId,
                    bookId = book.id,
                    title = book.title,
                    format = format,
                    filePath = file.absolutePath,
                    sizeBytes = size,
                    sourceUrl = url,
                    createdAt = now,
                    lastAccessedAt = now
                )
            )
            Result.success(ReaderCacheResult(file = file, format = format, sizeBytes = size))
        } catch (e: Exception) {
            file.delete()
            Result.failure(e)
        }
    }

    suspend fun clearAll(context: Context): Long = withContext(Dispatchers.IO) {
        val dir = rootDir(context)
        val bytes = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        dir.deleteRecursively()
        ReaderDatabase.get(context).readerDao().clearCacheTable()
        bytes
    }

    suspend fun cacheInfo(context: Context, bookId: Int): ReaderCacheInfo = withContext(Dispatchers.IO) {
        val serverId = activeServerId(context)
        val dao = ReaderDatabase.get(context).readerDao()
        val existing = dao.getCache(serverId, bookId) ?: return@withContext ReaderCacheInfo(isCached = false)
        val file = File(existing.filePath)
        if (!file.exists() || file.length() <= 0) {
            dao.deleteCache(serverId, bookId)
            return@withContext ReaderCacheInfo(isCached = false)
        }
        ReaderCacheInfo(isCached = true, format = existing.format, sizeBytes = file.length())
    }

    suspend fun cachedBooks(context: Context): List<ReaderCachedBook> = withContext(Dispatchers.IO) {
        val serverId = activeServerId(context)
        val dao = ReaderDatabase.get(context).readerDao()
        dao.getAllCacheOldestFirst(serverId).mapNotNull { entry ->
            val file = File(entry.filePath)
            if (!file.exists() || file.length() <= 0) {
                dao.deleteCache(serverId, entry.bookId)
                null
            } else {
                ReaderCachedBook(
                    bookId = entry.bookId,
                    title = entry.title,
                    format = entry.format,
                    sizeBytes = file.length(),
                    lastAccessedAt = entry.lastAccessedAt
                )
            }
        }.sortedByDescending { it.lastAccessedAt }
    }

    suspend fun deleteBookCache(context: Context, bookId: Int): Long = withContext(Dispatchers.IO) {
        val serverId = activeServerId(context)
        val dao = ReaderDatabase.get(context).readerDao()
        val existing = dao.getCache(serverId, bookId)
        val file = existing?.let { File(it.filePath) }
        val bookRoot = file?.parentFile
        val size = if (bookRoot?.exists() == true) {
            bookRoot.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        } else {
            0L
        }
        bookRoot?.deleteRecursively()
        dao.deleteCache(serverId, bookId)
        size
    }

    suspend fun totalSizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        val dir = rootDir(context)
        if (!dir.exists()) 0L else dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    suspend fun trimToSize(context: Context, maxBytes: Long): Long = withContext(Dispatchers.IO) {
        val serverId = activeServerId(context)
        val dao = ReaderDatabase.get(context).readerDao()
        var total = dao.getAllCacheOldestFirst(serverId).sumOf { File(it.filePath).takeIf { f -> f.exists() }?.length() ?: 0L }
        var removed = 0L
        for (entry in dao.getAllCacheOldestFirst(serverId)) {
            if (total <= maxBytes) break
            val file = File(entry.filePath)
            val bookRoot = file.parentFile
            val size = if (bookRoot?.exists() == true) {
                bookRoot.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            } else {
                0L
            }
            bookRoot?.deleteRecursively()
            dao.deleteCache(serverId, entry.bookId)
            total -= size
            removed += size
        }
        removed
    }

    private fun resolveReadableSource(book: BookDetail): Pair<String, String>? {
        val picked = downloadRepository.pickFile(book)
        if (picked != null) {
            val ext = picked.first.lowercase()
            if (ext == "epub" || ext == "pdf") return ext to resolveUrl(picked.second)
        }

        val canExtract = book.fmtAzw3.isNotBlank() ||
            book.fmtMobi.isNotBlank() ||
            book.availableFormats.contains("azw3", ignoreCase = true) ||
            book.availableFormats.contains("mobi", ignoreCase = true) ||
            book.files.any { it.format.equals("azw3", true) || it.format.equals("mobi", true) }

        return if (canExtract) {
            "epub" to "${RetrofitClient.currentBaseUrl().trimEnd('/')}/get/extract/${book.id}/"
        } else {
            null
        }
    }

    private fun rootDir(context: Context): File = File(context.getExternalFilesDir(null), "reader_cache")

    private fun bookDir(context: Context, bookId: Int): File = File(rootDir(context), "books/$bookId")

    private suspend fun activeServerId(context: Context): String = SettingsRepository(context.applicationContext).activeLibraryServerId.first()
}

object CacheJobTracker {
    private const val MAX_RUNNING = 2
    private val semaphore = Semaphore(MAX_RUNNING)
    private val _jobs = MutableStateFlow<Map<Int, CacheJobInfo>>(emptyMap())
    val jobs: StateFlow<Map<Int, CacheJobInfo>> = _jobs.asStateFlow()

    data class CacheJobInfo(
        val bookId: Int,
        val title: String,
        val downloadedBytes: Long = 0L,
        val totalBytes: Long = -1L,
        val isRunning: Boolean = true
    )

    private val jobHandles = mutableMapOf<Int, Job>()
    private val runningBookIds = mutableSetOf<Int>()

    fun enqueue(bookId: Int, title: String, job: Job) {
        synchronized(this) {
            jobHandles[bookId] = job
            _jobs.update { it + (bookId to CacheJobInfo(bookId, title, isRunning = false)) }
        }
    }

    suspend fun acquireSlot(bookId: Int) {
        semaphore.acquire()
        synchronized(this) {
            val job = jobHandles[bookId]
            if (job == null || !job.isActive) {
                semaphore.release()
                throw CancellationException("缓存已取消")
            }
            runningBookIds += bookId
            _jobs.update {
                val current = it[bookId] ?: return@update it
                it + (bookId to current.copy(isRunning = true))
            }
        }
    }

    fun updateProgress(bookId: Int, downloaded: Long, total: Long) {
        _jobs.update {
            val current = it[bookId] ?: return@update it
            it + (bookId to current.copy(downloadedBytes = downloaded, totalBytes = total, isRunning = true))
        }
    }

    fun finish(bookId: Int) {
        var shouldRelease = false
        synchronized(this) {
            shouldRelease = runningBookIds.remove(bookId)
            jobHandles.remove(bookId)
            _jobs.update { it - bookId }
        }
        if (shouldRelease) semaphore.release()
    }

    fun cancel(bookId: Int) {
        jobHandles[bookId]?.cancel()
    }
}
