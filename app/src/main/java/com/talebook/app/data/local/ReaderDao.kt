package com.talebook.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReaderDao {
    @Query("SELECT * FROM reading_progress WHERE serverId = :serverId AND bookId = :bookId LIMIT 1")
    suspend fun getProgress(serverId: String, bookId: Int): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE serverId = :serverId ORDER BY updatedAt DESC")
    suspend fun getAllProgress(serverId: String): List<ReadingProgressEntity>

    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC")
    suspend fun getAllProgressAllServers(): List<ReadingProgressEntity>

    @Query("SELECT * FROM reader_bookmarks WHERE serverId = :serverId AND bookId = :bookId ORDER BY createdAt DESC")
    suspend fun getBookmarks(serverId: String, bookId: Int): List<ReaderBookmarkEntity>

    @Query("SELECT * FROM reader_bookmarks WHERE serverId = :serverId ORDER BY createdAt DESC")
    suspend fun getAllBookmarks(serverId: String): List<ReaderBookmarkEntity>

    @Query("SELECT * FROM reader_bookmarks ORDER BY createdAt DESC")
    suspend fun getAllBookmarksAllServers(): List<ReaderBookmarkEntity>

    @Insert
    suspend fun addBookmark(bookmark: ReaderBookmarkEntity): Long

    @Insert
    suspend fun addBookmarks(bookmarks: List<ReaderBookmarkEntity>)

    @Delete
    suspend fun deleteBookmark(bookmark: ReaderBookmarkEntity)

    @Query("UPDATE reader_bookmarks SET title = :title WHERE id = :id")
    suspend fun renameBookmark(id: Long, title: String)

    @Query("SELECT * FROM reader_annotations WHERE serverId = :serverId AND bookId = :bookId ORDER BY createdAt DESC")
    suspend fun getAnnotations(serverId: String, bookId: Int): List<ReaderAnnotationEntity>

    @Query("SELECT * FROM reader_annotations WHERE serverId = :serverId ORDER BY createdAt DESC")
    suspend fun getAllAnnotations(serverId: String): List<ReaderAnnotationEntity>

    @Query("SELECT * FROM reader_annotations ORDER BY createdAt DESC")
    suspend fun getAllAnnotationsAllServers(): List<ReaderAnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAnnotation(annotation: ReaderAnnotationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAnnotations(annotations: List<ReaderAnnotationEntity>)

    @Delete
    suspend fun deleteAnnotation(annotation: ReaderAnnotationEntity)

    @Query("DELETE FROM reader_annotations WHERE id IN (:ids)")
    suspend fun deleteAnnotationsByIds(ids: List<Long>)

    @Query("SELECT * FROM reader_cache WHERE serverId = :serverId AND bookId = :bookId LIMIT 1")
    suspend fun getCache(serverId: String, bookId: Int): ReaderCacheEntity?

    @Query("SELECT * FROM reader_cache WHERE serverId = :serverId ORDER BY lastAccessedAt ASC")
    suspend fun getAllCacheOldestFirst(serverId: String): List<ReaderCacheEntity>

    @Query("SELECT * FROM reader_cache ORDER BY lastAccessedAt ASC")
    suspend fun getAllCacheOldestFirstAllServers(): List<ReaderCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCache(cache: ReaderCacheEntity)

    @Query("UPDATE reader_cache SET lastAccessedAt = :now WHERE serverId = :serverId AND bookId = :bookId")
    suspend fun touchCache(serverId: String, bookId: Int, now: Long)

    @Query("DELETE FROM reader_cache WHERE serverId = :serverId AND bookId = :bookId")
    suspend fun deleteCache(serverId: String, bookId: Int)

    @Query("DELETE FROM reader_cache")
    suspend fun clearCacheTable()

    @Query("SELECT * FROM recent_reading WHERE serverId = :serverId ORDER BY pinned DESC, pinnedAt DESC, sortIndex DESC, updatedAt DESC")
    suspend fun getRecentReading(serverId: String): List<RecentReadingEntity>

    @Query("SELECT * FROM recent_reading WHERE serverId = :serverId AND bookId = :bookId LIMIT 1")
    suspend fun getRecentEntry(serverId: String, bookId: Int): RecentReadingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentEntry(entry: RecentReadingEntity)

    @Query("DELETE FROM recent_reading WHERE serverId = :serverId AND bookId = :bookId")
    suspend fun deleteRecentEntry(serverId: String, bookId: Int)

    @Query("DELETE FROM recent_reading WHERE serverId = :serverId")
    suspend fun clearRecentReading(serverId: String)

    @Query("UPDATE recent_reading SET sortIndex = :sortIndex WHERE serverId = :serverId AND bookId = :bookId")
    suspend fun updateRecentSortIndex(serverId: String, bookId: Int, sortIndex: Long)

    @Query("UPDATE recent_reading SET pinned = :pinned, pinnedAt = :pinnedAt WHERE serverId = :serverId AND bookId = :bookId")
    suspend fun updateRecentPinned(serverId: String, bookId: Int, pinned: Boolean, pinnedAt: Long)

    @Query("SELECT * FROM local_folder ORDER BY addedAt DESC")
    suspend fun getLocalFolders(): List<LocalFolderEntity>

    @Query("SELECT * FROM local_folder ORDER BY addedAt DESC")
    suspend fun getAllLocalFolders(): List<LocalFolderEntity>

    @Query("SELECT * FROM local_folder WHERE id = :id LIMIT 1")
    suspend fun getLocalFolder(id: Long): LocalFolderEntity?

    @Query("SELECT folderId, COUNT(*) AS cnt FROM local_book WHERE available = 1 GROUP BY folderId")
    suspend fun getAvailableBookCountByFolder(): List<FolderBookCount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalFolder(folder: LocalFolderEntity): Long

    @Query("DELETE FROM local_folder WHERE id = :id")
    suspend fun deleteLocalFolder(id: Long)

    @Query("SELECT * FROM local_book WHERE folderId = :folderId ORDER BY displayName COLLATE NOCASE ASC")
    suspend fun getLocalBooksByFolder(folderId: Long): List<LocalBookEntity>

    @Query("SELECT * FROM local_book WHERE available = 1 ORDER BY lastReadAt DESC, displayName COLLATE NOCASE ASC")
    suspend fun getAvailableLocalBooks(): List<LocalBookEntity>

    @Query("SELECT * FROM local_book ORDER BY displayName COLLATE NOCASE ASC")
    suspend fun getAllLocalBooks(): List<LocalBookEntity>

    @Query("SELECT * FROM local_book WHERE id = :id LIMIT 1")
    suspend fun getLocalBook(id: Long): LocalBookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalBook(book: LocalBookEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalBooks(books: List<LocalBookEntity>)

    @Query("UPDATE local_book SET folderId = :folderId, available = :available, sizeBytes = :sizeBytes WHERE id = :id")
    suspend fun updateLocalBookAvailability(id: Long, folderId: Long?, available: Boolean, sizeBytes: Long)

    @Query("UPDATE local_book SET lastReadAt = :now WHERE id = :id")
    suspend fun touchLocalBook(id: Long, now: Long)

    @Query("UPDATE local_book SET folderId = NULL, available = 0 WHERE folderId = :folderId")
    suspend fun markLocalBooksUnavailableByFolder(folderId: Long)

    @Query("DELETE FROM local_book WHERE folderId = :folderId")
    suspend fun deleteLocalBooksByFolder(folderId: Long)

    @Query("DELETE FROM local_book WHERE id = :id")
    suspend fun deleteLocalBook(id: Long)
}

data class FolderBookCount(
    val folderId: Long,
    val cnt: Int
)
