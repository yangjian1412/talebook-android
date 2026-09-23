package com.talebook.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.talebook.app.data.local.LocalBookEntity
import com.talebook.app.data.local.LocalFolderEntity
import com.talebook.app.data.local.ReaderAnnotationEntity
import com.talebook.app.data.local.ReaderBookmarkEntity
import com.talebook.app.data.local.ReaderDatabase
import com.talebook.app.data.local.ReadingProgressEntity
import com.talebook.app.data.local.RecentReadingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReaderBackup(
    val version: Int = 4,
    val exportedAt: Long = System.currentTimeMillis(),
    val baseUrl: String = "",
    val progress: List<ReadingProgressEntity>,
    val bookmarks: List<ReaderBookmarkEntity>,
    val annotations: List<ReaderAnnotationEntity>,
    val recentReading: List<RecentReadingEntity> = emptyList(),
    val localFolders: List<LocalFolderEntity> = emptyList(),
    val localBooks: List<LocalBookEntity> = emptyList()
)

data class ReaderNoteBookSummary(
    val bookId: Int,
    val title: String,
    val noteCount: Int,
    val updatedAt: Long
)

class ReaderBackupRepository {
    private val gson = Gson()
    private val bookRepository = BookRepository()

    suspend fun exportToDownloads(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dao = ReaderDatabase.get(context).readerDao()
            val settings = SettingsRepository(context.applicationContext)
            val serverId = settings.activeLibraryServerId.first()
            val activeServer = settings.activeLibraryServer.first()
            val backup = ReaderBackup(
                version = 4,
                baseUrl = activeServer.baseUrl,
                progress = dao.getAllProgress(serverId),
                bookmarks = dao.getAllBookmarks(serverId),
                annotations = dao.getAllAnnotations(serverId),
                recentReading = dao.getRecentReading(serverId),
                localFolders = dao.getAllLocalFolders(),
                localBooks = dao.getAllLocalBooks()
            )
            val json = gson.toJson(backup)
            val fileName = "talebook-reader-backup-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.json"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/talebook")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("无法创建导出文件")
                resolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                    ?: error("无法写入导出文件")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                "Download/talebook/$fileName"
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "talebook")
                if (!dir.exists()) dir.mkdirs()
                File(dir, fileName).writeText(json)
                "Download/talebook/$fileName"
            }
        }
    }

    suspend fun importFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("无法读取所选备份文件")
            importBackupJson(context, json)
        }
    }

    suspend fun importLatestFromDownloads(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val json = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val projection = arrayOf(
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.DATE_MODIFIED
                )
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
                val args = arrayOf("talebook-reader-backup-%.json")
                resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    args,
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) error("未找到备份文件")
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                    val uri = android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                    resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("无法读取备份文件")
                } ?: error("无法查询备份文件")
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "talebook")
                val file = dir.listFiles()
                    ?.filter { it.name.startsWith("talebook-reader-backup-") && it.name.endsWith(".json") }
                    ?.maxByOrNull { it.lastModified() }
                    ?: error("未找到备份文件")
                file.readText()
            }
            importBackupJson(context, json)
        }
    }

    private suspend fun importBackupJson(context: Context, json: String): String {
        val type = object : TypeToken<ReaderBackup>() {}.type
        val backup: ReaderBackup = gson.fromJson(json, type)
            ?: error("备份文件格式无效")
        val dao = ReaderDatabase.get(context).readerDao()
        val settings = SettingsRepository(context.applicationContext)

        val hasServerData = backup.progress.isNotEmpty() || backup.bookmarks.isNotEmpty() || backup.annotations.isNotEmpty() || backup.recentReading.isNotEmpty()
        val hasLocalData = backup.localFolders.isNotEmpty() || backup.localBooks.isNotEmpty()

        val matchedServerId = if (backup.version >= 4 && backup.baseUrl.isNotBlank()) {
            settings.libraryServers.first().firstOrNull { it.baseUrl == backup.baseUrl }?.id
        } else {
            null
        }

        val hasExistingLocalFolders = hasLocalData && dao.getAllLocalFolders().isNotEmpty()

        val serverSkipped = hasServerData && matchedServerId == null
        val localSkipped = hasLocalData && !hasExistingLocalFolders

        if (!serverSkipped && matchedServerId != null) {
            val sid = matchedServerId
            backup.progress.forEach { dao.saveProgress(it.copy(serverId = sid)) }
            val existingBookmarks = dao.getAllBookmarks(sid).map { it.bookId to it.locatorJson }.toSet()
            val bookmarks = backup.bookmarks
                .filter { (it.bookId to it.locatorJson) !in existingBookmarks }
                .map { it.copy(id = 0, serverId = sid) }
            if (bookmarks.isNotEmpty()) dao.addBookmarks(bookmarks)
            val existingAnnotations = dao.getAllAnnotations(sid)
                .map { Triple(it.bookId, it.locatorJson, it.selectedText) }
                .toSet()
            val annotations = backup.annotations
                .filter { Triple(it.bookId, it.locatorJson, it.selectedText) !in existingAnnotations }
                .map { it.copy(id = 0, serverId = sid) }
            if (annotations.isNotEmpty()) dao.saveAnnotations(annotations)
            backup.recentReading.forEach { entry ->
                val existingRecents = dao.getRecentReading(sid).map { it.bookId }.toSet()
                if (entry.bookId !in existingRecents) {
                    dao.upsertRecentEntry(entry.copy(serverId = sid))
                }
            }
        }

        if (!localSkipped) {
            val existingUris = dao.getAllLocalFolders().map { it.rootUri }.toSet()
            backup.localFolders.forEach { folder ->
                if (folder.rootUri !in existingUris) {
                    dao.upsertLocalFolder(folder.copy(id = 0))
                }
            }
            val existingBookUris = dao.getAllLocalBooks().map { it.documentUri }.toSet()
            backup.localBooks.forEach { book ->
                if (book.documentUri !in existingBookUris) {
                    dao.upsertLocalBook(book.copy(id = 0))
                }
            }
        }

        return buildString {
            append("导入完成：")
            val parts = mutableListOf<String>()
            if (!serverSkipped && matchedServerId != null) {
                parts.add("进度 ${backup.progress.size}")
            }
            parts.add("书签 ${backup.bookmarks.size}")
            parts.add("笔记 ${backup.annotations.size}")
            if (matchedServerId != null) {
                val recentCount = backup.recentReading.size
                if (recentCount > 0) parts.add("最近阅读 $recentCount")
            }
            if (!localSkipped) {
                if (backup.localFolders.isNotEmpty()) parts.add("本地文件夹 ${backup.localFolders.size}")
                if (backup.localBooks.isNotEmpty()) parts.add("本地图书 ${backup.localBooks.size}")
            }
            append(parts.joinToString("，"))
            if (serverSkipped) {
                append("。部分服务器数据未导入（进度/书签/笔记/最近阅读），请先配置对应服务器后再重新导入")
            }
            if (localSkipped) {
                append("。部分本地书架数据未导入，请先添加本地文件夹后再重新导入")
            }
        }
    }

    suspend fun exportNotesMarkdownToDownloads(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dao = ReaderDatabase.get(context).readerDao()
            val serverId = SettingsRepository(context.applicationContext).activeLibraryServerId.first()
            val annotationsByBook = dao.getAllAnnotations(serverId).groupBy { it.bookId }
            if (annotationsByBook.isEmpty()) error("暂无可导出的笔记")

            var exported = 0
            annotationsByBook.forEach { (bookId, annotations) ->
                val title = bookTitle(bookId)
                val markdown = buildNotesMarkdown(title, annotations)
                val fileName = "${sanitizeFileName(title)}-notes-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.md"
                writeTextToDownloads(context, fileName, markdown, "text/markdown", "talebook/notes")
                exported++
            }
            "已导出 $exported 本书的 Markdown 笔记到 Download/talebook/notes/"
        }
    }

    suspend fun noteBookSummaries(context: Context): List<ReaderNoteBookSummary> = withContext(Dispatchers.IO) {
        val serverId = SettingsRepository(context.applicationContext).activeLibraryServerId.first()
        val annotationsByBook = ReaderDatabase.get(context).readerDao().getAllAnnotations(serverId).groupBy { it.bookId }
        annotationsByBook.map { (bookId, annotations) ->
            ReaderNoteBookSummary(
                bookId = bookId,
                title = bookTitle(bookId),
                noteCount = annotations.size,
                updatedAt = annotations.maxOfOrNull { it.updatedAt } ?: 0L
            )
        }.sortedByDescending { it.updatedAt }
    }

    suspend fun exportNotesMarkdownForBook(context: Context, bookId: Int): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val serverId = SettingsRepository(context.applicationContext).activeLibraryServerId.first()
            val annotations = ReaderDatabase.get(context).readerDao().getAnnotations(serverId, bookId)
            if (annotations.isEmpty()) error("本书暂无笔记")
            val title = bookTitle(bookId)
            val fileName = "${sanitizeFileName(title)}-notes-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.md"
            writeTextToDownloads(context, fileName, buildNotesMarkdown(title, annotations), "text/markdown", "talebook/notes")
            "已导出到 Download/talebook/notes/$fileName"
        }
    }

    suspend fun deleteAnnotations(context: Context, ids: List<Long>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) ReaderDatabase.get(context).readerDao().deleteAnnotationsByIds(ids)
    }

    private suspend fun bookTitle(bookId: Int): String =
        bookRepository.getBookDetail(bookId).getOrNull()?.title?.ifBlank { null } ?: "book-$bookId"

    private fun buildNotesMarkdown(title: String, annotations: List<ReaderAnnotationEntity>): String = buildString {
        appendLine("# ${title.escapeMarkdown()}")
        appendLine()
        appendLine("导出时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine()
        annotations.sortedBy { it.createdAt }.forEachIndexed { index, annotation ->
            appendLine("## ${index + 1}. ${(annotation.selectedText.ifBlank { "当前位置" }).escapeMarkdown()}")
            appendLine()
            appendLine("> ${annotation.selectedText.ifBlank { "当前位置" }.replace("\n", " ").escapeMarkdown()}")
            appendLine()
            if (annotation.note.isNotBlank()) {
                appendLine(annotation.note.trim())
                appendLine()
            }
            appendLine("- 进度：${progressFromLocator(annotation.locatorJson)}")
            appendLine("- 创建：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(annotation.createdAt))}")
            appendLine()
        }
    }

    private fun writeTextToDownloads(
        context: Context,
        fileName: String,
        text: String,
        mimeType: String,
        relativeSubDir: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + relativeSubDir)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("无法创建导出文件")
            resolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
                ?: error("无法写入导出文件")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), relativeSubDir)
            if (!dir.exists()) dir.mkdirs()
            File(dir, fileName).writeText(text)
        }
    }

    private fun progressFromLocator(locatorJson: String): String {
        val marker = "\"totalProgression\":"
        val index = locatorJson.indexOf(marker)
        if (index < 0) return "未知"
        val start = index + marker.length
        val number = locatorJson.substring(start).takeWhile { it.isDigit() || it == '.' }
        val value = number.toDoubleOrNull() ?: return "未知"
        return "${(value * 100).toInt()}%"
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\r\n]"), "_").trim().take(80).ifBlank { "book" }

    private fun String.escapeMarkdown(): String = replace("#", "\\#")
}
