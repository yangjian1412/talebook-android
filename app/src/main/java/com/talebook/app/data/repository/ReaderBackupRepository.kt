package com.talebook.app.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.talebook.app.data.local.ReaderAnnotationEntity
import com.talebook.app.data.local.ReaderBookmarkEntity
import com.talebook.app.data.local.ReaderDatabase
import com.talebook.app.data.local.ReadingProgressEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReaderBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val progress: List<ReadingProgressEntity>,
    val bookmarks: List<ReaderBookmarkEntity>,
    val annotations: List<ReaderAnnotationEntity>
)

class ReaderBackupRepository {
    private val gson = Gson()

    suspend fun exportToDownloads(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dao = ReaderDatabase.get(context).readerDao()
            val backup = ReaderBackup(
                progress = dao.getAllProgress(),
                bookmarks = dao.getAllBookmarks(),
                annotations = dao.getAllAnnotations()
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

            val type = object : TypeToken<ReaderBackup>() {}.type
            val backup: ReaderBackup = gson.fromJson(json, type)
            val dao = ReaderDatabase.get(context).readerDao()
            backup.progress.forEach { dao.saveProgress(it) }
            val existingBookmarks = dao.getAllBookmarks().map { it.bookId to it.locatorJson }.toSet()
            val bookmarks = backup.bookmarks
                .filter { (it.bookId to it.locatorJson) !in existingBookmarks }
                .map { it.copy(id = 0) }
            if (bookmarks.isNotEmpty()) dao.addBookmarks(bookmarks)

            val existingAnnotations = dao.getAllAnnotations()
                .map { Triple(it.bookId, it.locatorJson, it.selectedText) }
                .toSet()
            val annotations = backup.annotations
                .filter { Triple(it.bookId, it.locatorJson, it.selectedText) !in existingAnnotations }
                .map { it.copy(id = 0) }
            if (annotations.isNotEmpty()) dao.saveAnnotations(annotations)
            "已导入：进度 ${backup.progress.size}，书签 ${bookmarks.size}/${backup.bookmarks.size}，笔记 ${annotations.size}/${backup.annotations.size}"
        }
    }
}
