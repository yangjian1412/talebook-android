package com.talebook.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReaderDao {
    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId LIMIT 1")
    suspend fun getProgress(bookId: Int): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC")
    suspend fun getAllProgress(): List<ReadingProgressEntity>

    @Query("SELECT * FROM reader_bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    suspend fun getBookmarks(bookId: Int): List<ReaderBookmarkEntity>

    @Query("SELECT * FROM reader_bookmarks ORDER BY createdAt DESC")
    suspend fun getAllBookmarks(): List<ReaderBookmarkEntity>

    @Insert
    suspend fun addBookmark(bookmark: ReaderBookmarkEntity): Long

    @Insert
    suspend fun addBookmarks(bookmarks: List<ReaderBookmarkEntity>)

    @Delete
    suspend fun deleteBookmark(bookmark: ReaderBookmarkEntity)

    @Query("UPDATE reader_bookmarks SET title = :title WHERE id = :id")
    suspend fun renameBookmark(id: Long, title: String)

    @Query("SELECT * FROM reader_annotations WHERE bookId = :bookId ORDER BY createdAt DESC")
    suspend fun getAnnotations(bookId: Int): List<ReaderAnnotationEntity>

    @Query("SELECT * FROM reader_annotations ORDER BY createdAt DESC")
    suspend fun getAllAnnotations(): List<ReaderAnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAnnotation(annotation: ReaderAnnotationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAnnotations(annotations: List<ReaderAnnotationEntity>)

    @Delete
    suspend fun deleteAnnotation(annotation: ReaderAnnotationEntity)

    @Query("SELECT * FROM reader_cache WHERE bookId = :bookId LIMIT 1")
    suspend fun getCache(bookId: Int): ReaderCacheEntity?

    @Query("SELECT * FROM reader_cache ORDER BY lastAccessedAt ASC")
    suspend fun getAllCacheOldestFirst(): List<ReaderCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCache(cache: ReaderCacheEntity)

    @Query("UPDATE reader_cache SET lastAccessedAt = :now WHERE bookId = :bookId")
    suspend fun touchCache(bookId: Int, now: Long)

    @Query("DELETE FROM reader_cache WHERE bookId = :bookId")
    suspend fun deleteCache(bookId: Int)

    @Query("DELETE FROM reader_cache")
    suspend fun clearCacheTable()
}
