package com.aiyu.findex.core.repository

import com.aiyu.findex.core.db.FileTagEntity
import com.aiyu.findex.core.db.TagDao
import com.aiyu.findex.core.db.TagEntity
import com.aiyu.findex.core.db.likePrefix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** [colorIndex] indexes the UI's TagTones (0..7). */
data class Tag(val id: Long, val name: String, val colorIndex: Int)

@Singleton
class TagRepository @Inject constructor(private val dao: TagDao) {
    fun tags(): Flow<List<Tag>> = dao.observeTags().map { l -> l.map { Tag(it.id, it.name, it.colorIndex) } }

    /** Tags of the direct children of [dir], by child path. */
    fun tagsInFolder(dir: String): Flow<Map<String, List<Tag>>> {
        val like = likePrefix(dir)
        val deeper = like.dropLast(1) + "%/%"
        return dao.observeInFolder(like, deeper).map { rows ->
            rows.groupBy({ it.path }) { Tag(it.tagId, it.name, it.colorIndex) }
        }
    }

    fun filesWithTag(tagId: Long): Flow<List<String>> = dao.observePaths(tagId)

    suspend fun tagIdsFor(path: String): Set<Long> = dao.tagIdsFor(path).toSet()

    /** @return the new id, or -1 when [name] is blank or taken. */
    suspend fun create(name: String, colorIndex: Int): Long {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty()) return -1
        return runCatching { dao.upsertTag(TagEntity(name = clean, colorIndex = colorIndex.coerceIn(0, 7), position = Int.MAX_VALUE / 2)) }.getOrDefault(-1)
    }

    suspend fun update(tag: Tag, name: String, colorIndex: Int) {
        val clean = name.trim().take(MAX_NAME).ifEmpty { return }
        runCatching { dao.upsertTag(TagEntity(id = tag.id, name = clean, colorIndex = colorIndex.coerceIn(0, 7), position = 0)) }
    }

    suspend fun delete(id: Long) = dao.deleteTag(id)

    suspend fun apply(paths: List<String>, add: Set<Long>, remove: Set<Long>) {
        remove.forEach { dao.unassign(it, paths) }
        dao.assign(paths.flatMap { p -> add.map { FileTagEntity(p, it) } })
    }

    /** Drops rows for files that no longer exist. */
    suspend fun prune(paths: List<String>) {
        val gone = paths.filter { "://" !in it && !File(it).exists() }
        if (gone.isNotEmpty()) dao.forgetPaths(gone)
    }

    private companion object { const val MAX_NAME = 24 }
}
