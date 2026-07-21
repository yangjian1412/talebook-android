package com.talebook.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val bookId: Int,
    val locatorJson: String,
    val progression: Double,
    val updatedAt: Long
)

@Entity(
    tableName = "reader_bookmarks",
    indices = [Index(value = ["bookId", "createdAt"])]
)
data class ReaderBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Int,
    val title: String,
    val locatorJson: String,
    val progression: Double,
    val createdAt: Long
)

@Entity(
    tableName = "reader_annotations",
    indices = [Index(value = ["bookId", "createdAt"])]
)
data class ReaderAnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Int,
    val locatorJson: String,
    val selectedText: String,
    val note: String,
    val color: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "reader_cache")
data class ReaderCacheEntity(
    @PrimaryKey val bookId: Int,
    val title: String,
    val format: String,
    val filePath: String,
    val sizeBytes: Long,
    val sourceUrl: String,
    val createdAt: Long,
    val lastAccessedAt: Long
)
