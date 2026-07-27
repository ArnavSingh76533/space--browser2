package com.spacebrowser.core.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.ColumnInfo
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long,
    @ColumnInfo(defaultValue = "") val host: String = "",
)

/** Aggregated most-visited host for the start page. */
data class TopSite(
    val host: String,
    val visits: Long,
    val lastVisit: Long,
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val createdAt: Long,
)

@Entity(tableName = "quick_links")
data class QuickLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val position: Int,
)

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entry: HistoryEntry)

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<HistoryEntry>>

    @Query(
        "SELECT * FROM history WHERE url LIKE '%' || :q || '%' OR title LIKE '%' || :q || '%' " +
            "ORDER BY visitedAt DESC LIMIT :limit"
    )
    suspend fun search(q: String, limit: Int): List<HistoryEntry>

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE visitedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        "SELECT host, COUNT(*) AS visits, MAX(visitedAt) AS lastVisit FROM history " +
            "WHERE host != '' GROUP BY host ORDER BY visits DESC, lastVisit DESC LIMIT :limit"
    )
    fun topSites(limit: Int): Flow<List<TopSite>>
}

@Dao
interface BookmarkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark)

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun all(): Flow<List<Bookmark>>

    @Query("SELECT COUNT(*) FROM bookmarks WHERE url = :url")
    fun countForUrl(url: String): Flow<Int>

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Delete
    suspend fun delete(bookmark: Bookmark)
}

@Dao
interface QuickLinkDao {
    @Insert
    suspend fun insert(link: QuickLink)

    @Query("SELECT * FROM quick_links ORDER BY position ASC, id ASC")
    fun all(): Flow<List<QuickLink>>

    @Query("DELETE FROM quick_links WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [HistoryEntry::class, Bookmark::class, QuickLink::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun quickLinkDao(): QuickLinkDao

    companion object {

        /** v2: per-visit host column (powers Top Sites) + drop the old seeded links. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN host TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "DELETE FROM quick_links WHERE url IN (" +
                        "'https://en.wikipedia.org','https://github.com'," +
                        "'https://www.youtube.com','https://www.reddit.com')"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "space.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
    }
}
