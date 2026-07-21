package com.talebook.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ReadingProgressEntity::class,
        ReaderBookmarkEntity::class,
        ReaderAnnotationEntity::class,
        ReaderCacheEntity::class,
        RecentReadingEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun readerDao(): ReaderDao

    companion object {
        @Volatile private var INSTANCE: ReaderDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                rebuildReaderTables(database)
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                rebuildReaderTables(database)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recent_reading (
                        serverId TEXT NOT NULL,
                        bookId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        author TEXT NOT NULL,
                        cover TEXT NOT NULL,
                        img TEXT NOT NULL,
                        thumb TEXT NOT NULL,
                        progression REAL NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        sortIndex INTEGER NOT NULL,
                        PRIMARY KEY(serverId, bookId)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recent_reading_serverId_sortIndex ON recent_reading(serverId, sortIndex)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                if (!hasColumn(database, "recent_reading", "pinned")) {
                    database.execSQL("ALTER TABLE recent_reading ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(database, "recent_reading", "pinnedAt")) {
                    database.execSQL("ALTER TABLE recent_reading ADD COLUMN pinnedAt INTEGER NOT NULL DEFAULT 0")
                }
                database.execSQL("DROP INDEX IF EXISTS index_recent_reading_serverId_sortIndex")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_recent_reading_serverId_pinned_pinnedAt_sortIndex ON recent_reading(serverId, pinned, pinnedAt, sortIndex)")
            }
        }

        private fun rebuildReaderTables(database: SupportSQLiteDatabase) {
            val progressServerExpr = if (hasColumn(database, "reading_progress", "serverId")) "serverId" else "'default'"
            database.execSQL("""
                CREATE TABLE reading_progress_new (
                    serverId TEXT NOT NULL,
                    bookId INTEGER NOT NULL,
                    locatorJson TEXT NOT NULL,
                    progression REAL NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(serverId, bookId)
                )
            """.trimIndent())
            database.execSQL("""
                INSERT OR REPLACE INTO reading_progress_new (serverId, bookId, locatorJson, progression, updatedAt)
                SELECT $progressServerExpr, bookId, locatorJson, progression, updatedAt FROM reading_progress
            """.trimIndent())
            database.execSQL("DROP TABLE reading_progress")
            database.execSQL("ALTER TABLE reading_progress_new RENAME TO reading_progress")

            val bookmarkServerExpr = if (hasColumn(database, "reader_bookmarks", "serverId")) "serverId" else "'default'"
            database.execSQL("DROP INDEX IF EXISTS index_reader_bookmarks_bookId_createdAt")
            database.execSQL("DROP INDEX IF EXISTS index_reader_bookmarks_serverId_bookId_createdAt")
            database.execSQL("""
                CREATE TABLE reader_bookmarks_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    serverId TEXT NOT NULL,
                    bookId INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    locatorJson TEXT NOT NULL,
                    progression REAL NOT NULL,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())
            database.execSQL("""
                INSERT INTO reader_bookmarks_new (id, serverId, bookId, title, locatorJson, progression, createdAt)
                SELECT id, $bookmarkServerExpr, bookId, title, locatorJson, progression, createdAt FROM reader_bookmarks
            """.trimIndent())
            database.execSQL("DROP TABLE reader_bookmarks")
            database.execSQL("ALTER TABLE reader_bookmarks_new RENAME TO reader_bookmarks")
            database.execSQL("CREATE INDEX index_reader_bookmarks_serverId_bookId_createdAt ON reader_bookmarks(serverId, bookId, createdAt)")

            val annotationServerExpr = if (hasColumn(database, "reader_annotations", "serverId")) "serverId" else "'default'"
            database.execSQL("DROP INDEX IF EXISTS index_reader_annotations_bookId_createdAt")
            database.execSQL("DROP INDEX IF EXISTS index_reader_annotations_serverId_bookId_createdAt")
            database.execSQL("""
                CREATE TABLE reader_annotations_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    serverId TEXT NOT NULL,
                    bookId INTEGER NOT NULL,
                    locatorJson TEXT NOT NULL,
                    selectedText TEXT NOT NULL,
                    note TEXT NOT NULL,
                    color TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())
            database.execSQL("""
                INSERT INTO reader_annotations_new (id, serverId, bookId, locatorJson, selectedText, note, color, createdAt, updatedAt)
                SELECT id, $annotationServerExpr, bookId, locatorJson, selectedText, note, color, createdAt, updatedAt FROM reader_annotations
            """.trimIndent())
            database.execSQL("DROP TABLE reader_annotations")
            database.execSQL("ALTER TABLE reader_annotations_new RENAME TO reader_annotations")
            database.execSQL("CREATE INDEX index_reader_annotations_serverId_bookId_createdAt ON reader_annotations(serverId, bookId, createdAt)")

            val cacheServerExpr = if (hasColumn(database, "reader_cache", "serverId")) "serverId" else "'default'"
            database.execSQL("""
                CREATE TABLE reader_cache_new (
                    serverId TEXT NOT NULL,
                    bookId INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    format TEXT NOT NULL,
                    filePath TEXT NOT NULL,
                    sizeBytes INTEGER NOT NULL,
                    sourceUrl TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    lastAccessedAt INTEGER NOT NULL,
                    PRIMARY KEY(serverId, bookId)
                )
            """.trimIndent())
            database.execSQL("""
                INSERT OR REPLACE INTO reader_cache_new (serverId, bookId, title, format, filePath, sizeBytes, sourceUrl, createdAt, lastAccessedAt)
                SELECT $cacheServerExpr, bookId, title, format, filePath, sizeBytes, sourceUrl, createdAt, lastAccessedAt FROM reader_cache
            """.trimIndent())
            database.execSQL("DROP TABLE reader_cache")
            database.execSQL("ALTER TABLE reader_cache_new RENAME TO reader_cache")
        }

        private fun hasColumn(database: SupportSQLiteDatabase, table: String, column: String): Boolean {
            database.query("PRAGMA table_info($table)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) return true
                }
            }
            return false
        }

        fun get(context: Context): ReaderDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                ReaderDatabase::class.java,
                "reader.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { INSTANCE = it }
        }
    }
}
