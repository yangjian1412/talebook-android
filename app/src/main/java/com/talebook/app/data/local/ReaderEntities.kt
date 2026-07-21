package com.talebook.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reading_progress",
    primaryKeys = ["serverId", "bookId"]
)
data class ReadingProgressEntity(
    val serverId: String = "default",
    val bookId: Int,
    val locatorJson: String,
    val progression: Double,
    val updatedAt: Long
)

@Entity(
    tableName = "reader_bookmarks",
    indices = [Index(value = ["serverId", "bookId", "createdAt"])]
)
data class ReaderBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String = "default",
    val bookId: Int,
    val title: String,
    val locatorJson: String,
    val progression: Double,
    val createdAt: Long
)

@Entity(
    tableName = "reader_annotations",
    indices = [Index(value = ["serverId", "bookId", "createdAt"])]
)
data class ReaderAnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String = "default",
    val bookId: Int,
    val locatorJson: String,
    val selectedText: String,
    val note: String,
    val color: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "reader_cache",
    primaryKeys = ["serverId", "bookId"]
)
data class ReaderCacheEntity(
    val serverId: String = "default",
    val bookId: Int,
    val title: String,
    val format: String,
    val filePath: String,
    val sizeBytes: Long,
    val sourceUrl: String,
    val createdAt: Long,
    val lastAccessedAt: Long
)

@Entity(
    tableName = "recent_reading",
    primaryKeys = ["serverId", "bookId"],
    indices = [Index(value = ["serverId", "pinned", "pinnedAt", "sortIndex"])]
)
data class RecentReadingEntity(
    val serverId: String = "default",
    val bookId: Int,
    val title: String,
    val author: String,
    val cover: String,
    val img: String,
    val thumb: String,
    val progression: Double,
    val updatedAt: Long,
    val sortIndex: Long,
    val pinned: Boolean = false,
    val pinnedAt: Long = 0L
)
