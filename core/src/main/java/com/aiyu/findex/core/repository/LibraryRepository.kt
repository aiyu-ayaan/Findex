package com.aiyu.findex.core.repository

import com.aiyu.findex.core.db.BookmarkDao
import com.aiyu.findex.core.db.BookmarkEntity
import com.aiyu.findex.core.db.RecentFileDao
import com.aiyu.findex.core.db.RecentFileEntity
import com.aiyu.findex.core.model.Bookmark
import com.aiyu.findex.core.model.RecentFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Bookmarked folders and recently opened files, stored in SQLite. */
@Singleton
class LibraryRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val recentFileDao: RecentFileDao
) {
    val bookmarks: Flow<List<Bookmark>> = bookmarkDao.observeAll().map { list -> list.map { Bookmark(it.path, it.name) } }

    fun recentFiles(limit: Int = 10): Flow<List<RecentFile>> = recentFileDao.observeRecent(limit).map { list ->
        list.map { RecentFile(it.path, it.name, it.size, it.openedAt) }
    }

    fun isBookmarked(path: String): Flow<Boolean> = bookmarkDao.observeIsBookmarked(path)

    suspend fun toggleBookmark(path: String, name: String, bookmarked: Boolean) {
        if (bookmarked) bookmarkDao.delete(path)
        else bookmarkDao.insert(BookmarkEntity(path = path, name = name))
    }

    suspend fun removeBookmark(path: String) = bookmarkDao.delete(path)

    suspend fun recordOpened(file: File) {
        recentFileDao.upsert(RecentFileEntity(path = file.absolutePath, name = file.name, size = file.length()))
        recentFileDao.trim(keep = 50)
    }

    suspend fun forget(paths: List<String>) = recentFileDao.delete(paths)
}
