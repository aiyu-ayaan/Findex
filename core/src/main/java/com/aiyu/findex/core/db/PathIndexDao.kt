package com.aiyu.findex.core.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * Keeps path-keyed rows (tags, bookmarks, recents) in step with moves and renames.
 * `from` and `to` are absolute paths; `fromLike` is the escaped "$from/%".
 */
@Dao
interface PathIndexDao {
    @Query("UPDATE OR REPLACE file_tags SET path = :to || substr(path, length(:from) + 1) WHERE path = :from OR path LIKE :fromLike ESCAPE '\\'")
    suspend fun moveTags(from: String, to: String, fromLike: String)

    @Query("UPDATE OR REPLACE bookmarks SET path = :to || substr(path, length(:from) + 1), name = CASE WHEN path = :from THEN :toName ELSE name END WHERE path = :from OR path LIKE :fromLike ESCAPE '\\'")
    suspend fun moveBookmarks(from: String, to: String, toName: String, fromLike: String)

    @Query("UPDATE OR REPLACE recent_files SET path = :to || substr(path, length(:from) + 1) WHERE path = :from OR path LIKE :fromLike ESCAPE '\\'")
    suspend fun moveRecents(from: String, to: String, fromLike: String)

    @Transaction
    suspend fun onMoved(from: String, to: String) {
        if (from == to) return
        val fromLike = likePrefix(from)
        moveTags(from, to, fromLike)
        moveBookmarks(from, to, to.trimEnd('/').substringAfterLast('/'), fromLike)
        moveRecents(from, to, fromLike)
    }

    /** Applies [moves] in order in one transaction. */
    @Transaction
    suspend fun onMovedAll(moves: List<Pair<String, String>>) {
        moves.forEach { (from, to) -> onMoved(from, to) }
    }
}

/** Escaped `"$dir/%"` for `LIKE … ESCAPE '\'`: matches everything below [dir]. */
fun likePrefix(dir: String): String =
    dir.trimEnd('/').replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "/%"
