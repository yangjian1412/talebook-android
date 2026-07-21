package com.talebook.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ReadingProgressEntity::class,
        ReaderBookmarkEntity::class,
        ReaderAnnotationEntity::class,
        ReaderCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun readerDao(): ReaderDao

    companion object {
        @Volatile private var INSTANCE: ReaderDatabase? = null

        fun get(context: Context): ReaderDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                ReaderDatabase::class.java,
                "reader.db"
            ).build().also { INSTANCE = it }
        }
    }
}
