package com.spacebrowser.core.db

import com.spacebrowser.core.util.UrlUtil
import kotlinx.coroutines.flow.Flow

class BrowsingRepository(private val db: AppDatabase) {

    // History -----------------------------------------------------------------
    fun recentHistory(limit: Int = 400): Flow<List<HistoryEntry>> = db.historyDao().recent(limit)

    suspend fun recordVisit(url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        val host = UrlUtil.hostOf(url)?.removePrefix("www.") ?: ""
        db.historyDao().insert(
            HistoryEntry(url = url, title = title, visitedAt = System.currentTimeMillis(), host = host),
        )
    }

    /** Most-visited hosts for the start page. */
    fun topSites(limit: Int = 10): Flow<List<TopSite>> = db.historyDao().topSites(limit)

    suspend fun searchHistory(q: String, limit: Int = 4): List<HistoryEntry> =
        if (q.isBlank()) emptyList() else db.historyDao().search(q, limit)

    suspend fun clearHistory() = db.historyDao().clear()
    suspend fun deleteHistory(id: Long) = db.historyDao().delete(id)

    suspend fun trimHistory(retentionDays: Int) {
        if (retentionDays <= 0) return
        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60 * 60 * 1000
        db.historyDao().deleteOlderThan(cutoff)
    }

    // Bookmarks ---------------------------------------------------------------
    fun bookmarks(): Flow<List<Bookmark>> = db.bookmarkDao().all()
    fun isBookmarked(url: String): Flow<Int> = db.bookmarkDao().countForUrl(url)

    suspend fun addBookmark(url: String, title: String) =
        db.bookmarkDao().insert(Bookmark(url = url, title = title.ifBlank { url }, createdAt = System.currentTimeMillis()))

    suspend fun removeBookmark(url: String) = db.bookmarkDao().deleteByUrl(url)
    suspend fun removeBookmark(bookmark: Bookmark) = db.bookmarkDao().delete(bookmark)

    // Quick links -------------------------------------------------------------
    fun quickLinks(): Flow<List<QuickLink>> = db.quickLinkDao().all()

    suspend fun addQuickLink(url: String, title: String) =
        db.quickLinkDao().insert(QuickLink(url = url, title = title.ifBlank { url }, position = 99))

    suspend fun removeQuickLink(id: Long) = db.quickLinkDao().delete(id)
}
