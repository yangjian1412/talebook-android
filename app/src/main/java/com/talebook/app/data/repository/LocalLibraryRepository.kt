package com.talebook.app.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.talebook.app.data.local.LocalBookEntity
import com.talebook.app.data.local.LocalFolderEntity
import com.talebook.app.data.local.ReaderDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalFolder(
    val id: Long,
    val displayName: String,
    val rootUri: Uri,
    val addedAt: Long,
    val bookCount: Int = 0
)

data class LocalBook(
    val id: Long,
    val folderId: Long?,
    val documentUri: Uri,
    val displayName: String,
    val relativePath: String,
    val format: String,
    val sizeBytes: Long,
    val available: Boolean,
    val lastReadAt: Long,
    val importedAt: Long
)

class LocalLibraryRepository(private val context: Context) {

    suspend fun listFolders(): List<LocalFolder> = withContext(Dispatchers.IO) {
        val dao = ReaderDatabase.get(context).readerDao()
        val counts = dao.getAvailableBookCountByFolder().associate { it.folderId to it.cnt }
        dao.getLocalFolders().map { folder ->
            LocalFolder(
                id = folder.id,
                displayName = folder.displayName,
                rootUri = Uri.parse(folder.rootUri),
                addedAt = folder.addedAt,
                bookCount = counts[folder.id] ?: 0
            )
        }
    }

    suspend fun listBooks(folderId: Long? = null): List<LocalBook> = withContext(Dispatchers.IO) {
        val dao = ReaderDatabase.get(context).readerDao()
        val raw = if (folderId == null) dao.getAvailableLocalBooks() else dao.getLocalBooksByFolder(folderId)
        raw.map { it.toModel() }
    }

    suspend fun getBook(id: Long): LocalBook? = withContext(Dispatchers.IO) {
        ReaderDatabase.get(context).readerDao().getLocalBook(id)?.toModel()
    }

    suspend fun addFolder(treeUri: Uri, displayName: String): Long = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // 忽略：调用方可能没有持久化权限（例如自定义 SAF 流程）
        }
        val dao = ReaderDatabase.get(context).readerDao()
        val folderId = dao.upsertLocalFolder(
            LocalFolderEntity(
                displayName = displayName,
                rootUri = treeUri.toString(),
                addedAt = System.currentTimeMillis()
            )
        )
        scanFolderInternal(folderId, treeUri)
        folderId
    }

    suspend fun refreshFolder(folderId: Long): Boolean = withContext(Dispatchers.IO) {
        val folder = ReaderDatabase.get(context).readerDao().getLocalFolder(folderId) ?: return@withContext false
        val uri = Uri.parse(folder.rootUri)
        scanFolderInternal(folderId, uri)
        true
    }

    suspend fun removeFolder(folderId: Long) = withContext(Dispatchers.IO) {
        val dao = ReaderDatabase.get(context).readerDao()
        dao.markLocalBooksUnavailableByFolder(folderId)
        dao.deleteLocalBooksByFolder(folderId)
        val folder = dao.getLocalFolder(folderId)
        if (folder != null) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(folder.rootUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
        }
        dao.deleteLocalFolder(folderId)
    }

    suspend fun removeBook(bookId: Long) = withContext(Dispatchers.IO) {
        ReaderDatabase.get(context).readerDao().deleteLocalBook(bookId)
    }

    suspend fun touchBook(bookId: Long) = withContext(Dispatchers.IO) {
        ReaderDatabase.get(context).readerDao().touchLocalBook(bookId, System.currentTimeMillis())
    }

    private suspend fun scanFolderInternal(folderId: Long, treeUri: Uri) {
        val dao = ReaderDatabase.get(context).readerDao()
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return
        val existing = dao.getLocalBooksByFolder(folderId).associateBy { it.documentUri }
        val now = System.currentTimeMillis()
        val scanned = mutableListOf<LocalBookEntity>()
        walk(root) { doc ->
            val name = doc.name ?: return@walk
            val mime = doc.type.orEmpty()
            val format = inferFormat(name, mime) ?: return@walk
            val uri = doc.uri.toString()
            val size = readSize(doc.uri)
            val previous = existing[uri]
            scanned += LocalBookEntity(
                id = previous?.id ?: 0,
                folderId = folderId,
                documentUri = uri,
                displayName = name,
                relativePath = doc.uri.lastPathSegment ?: name,
                format = format,
                sizeBytes = size,
                available = true,
                lastReadAt = previous?.lastReadAt ?: 0L,
                importedAt = previous?.importedAt ?: now
            )
        }
        // 先把当前文件夹里所有记录标记 unavailable，扫描到的再次置为 available
        dao.markLocalBooksUnavailableByFolder(folderId)
        if (scanned.isNotEmpty()) dao.upsertLocalBooks(scanned)
    }

    private fun walk(node: DocumentFile, block: (DocumentFile) -> Unit) {
        if (!node.canRead()) return
        if (node.isDirectory) {
            for (child in node.listFiles()) walk(child, block)
        } else {
            block(node)
        }
    }

    private fun readSize(uri: Uri): Long {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else 0L
                } else 0L
            } ?: 0L
        }.getOrDefault(0L)
    }

    private fun inferFormat(name: String, mime: String): String? {
        val lower = name.lowercase()
        val byExt = when {
            lower.endsWith(".epub") -> "epub"
            lower.endsWith(".pdf") -> "pdf"
            lower.endsWith(".txt") -> "txt"
            lower.endsWith(".mobi") -> "mobi"
            lower.endsWith(".azw3") -> "azw3"
            lower.endsWith(".fb2") -> "fb2"
            lower.endsWith(".rtf") -> "rtf"
            lower.endsWith(".doc") -> "doc"
            lower.endsWith(".docx") -> "docx"
            else -> null
        }
        if (byExt != null) return byExt
        return when {
            mime.contains("epub") -> "epub"
            mime.contains("pdf") -> "pdf"
            mime.startsWith("text/") -> "txt"
            else -> null
        }
    }

    private fun LocalBookEntity.toModel() = LocalBook(
        id = id,
        folderId = folderId,
        documentUri = Uri.parse(documentUri),
        displayName = displayName,
        relativePath = relativePath,
        format = format,
        sizeBytes = sizeBytes,
        available = available,
        lastReadAt = lastReadAt,
        importedAt = importedAt
    )
}