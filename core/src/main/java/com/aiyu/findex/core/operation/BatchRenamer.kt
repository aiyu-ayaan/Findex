package com.aiyu.findex.core.operation

import com.aiyu.findex.core.db.PathIndexDao
import com.aiyu.findex.core.model.FailureReason
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.FileOperationException
import com.aiyu.findex.core.model.OperationProgress
import com.aiyu.findex.core.model.RenameEntry
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.network.SmbPath
import com.aiyu.findex.core.network.SmbRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

enum class CaseMode { NONE, LOWER, UPPER, TITLE }

/** Tokens: {name} {ext} {n} {n:3} {date} {date:yyyyMMdd} {parent}. Order: find/replace, pattern, case. */
data class RenameRule(
    val pattern: String = "{name}",
    val find: String = "",
    val replace: String = "",
    val regex: Boolean = false,
    val ignoreCase: Boolean = true,
    val case: CaseMode = CaseMode.NONE,
    val counterStart: Int = 1,
    val counterStep: Int = 1,
    val counterPad: Int = 0,
    /** false: rules apply to the base name and the extension is kept. */
    val touchExtension: Boolean = false
)

enum class RenameStatus { OK, UNCHANGED, DUPLICATE, EXISTS, INVALID, BAD_REGEX }

data class RenamePreview(val item: StorageItem, val newName: String, val status: RenameStatus)

/** Pure planning logic: no I/O, unit-tested. */
object RenamePlanner {
    private val tokenRx = Regex("""\{(name|ext|parent|n|date)(?::([^}]*))?}""")

    fun parentOf(path: String): String = path.trimEnd('/').substringBeforeLast('/', "")

    /** [siblingNames]: parent path to every name currently in that folder. */
    fun plan(items: List<StorageItem>, rule: RenameRule, siblingNames: Map<String, Set<String>>): List<RenamePreview> {
        val finder: Regex? = if (rule.find.isEmpty()) null else try {
            val opts = if (rule.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
            if (rule.regex) Regex(rule.find, opts) else Regex(Regex.escape(rule.find), opts)
        } catch (e: Exception) {
            return items.map { RenamePreview(it, it.name, RenameStatus.BAD_REGEX) }
        }
        val names = items.mapIndexed { i, item -> compute(item, i, rule, finder) }

        // A name held by a batch item that is itself moving away is free for others.
        val freed = HashMap<String, MutableSet<String>>()
        items.forEachIndexed { i, item ->
            if (names[i] != item.name) freed.getOrPut(parentOf(item.path)) { HashSet() }.add(item.name)
        }
        val groups = HashMap<Pair<String, String>, Int>()
        items.forEachIndexed { i, item -> groups.merge(parentOf(item.path) to names[i].lowercase(), 1, Int::plus) }

        return items.mapIndexed { i, item ->
            val parent = parentOf(item.path)
            val name = names[i]
            val status = when {
                !validName(name) -> RenameStatus.INVALID
                name == item.name -> RenameStatus.UNCHANGED
                groups.getValue(parent to name.lowercase()) > 1 -> RenameStatus.DUPLICATE
                clashes(name, siblingNames[parent].orEmpty(), freed[parent].orEmpty()) -> RenameStatus.EXISTS
                else -> RenameStatus.OK
            }
            RenamePreview(item, name, status)
        }
    }

    /**
     * True when [name] would land on an existing entry of [siblings] that is not moving away.
     * [freed]: exact current names of batch items in that folder that are being renamed. Any case
     * variant counts, since the volume may be case-insensitive; only the exact moving file is free.
     */
    fun clashes(name: String, siblings: Collection<String>, freed: Set<String>): Boolean =
        siblings.any { it.equals(name, ignoreCase = true) && it !in freed }

    /** True when one path lies inside another; renaming the outer one first breaks the inner path. */
    fun hasNested(paths: List<String>): Boolean {
        val dirs = paths.map { it.trimEnd('/') + "/" }
        return paths.any { p -> dirs.any { p.startsWith(it) } }
    }

    fun validName(name: String): Boolean =
        name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\u0000' !in name &&
            name.toByteArray(Charsets.UTF_8).size <= 255

    private fun compute(item: StorageItem, index: Int, rule: RenameRule, finder: Regex?): String {
        val dot = item.name.lastIndexOf('.')
        val hasExt = !item.isDirectory && dot > 0 && dot < item.name.length - 1
        val ext = if (hasExt) item.name.substring(dot + 1) else ""
        val stem = if (hasExt && !rule.touchExtension) item.name.substring(0, dot) else item.name
        var working = stem
        if (finder != null) working = finder.replace(working, if (rule.regex) rule.replace else Regex.escapeReplacement(rule.replace))
        val counter = rule.counterStart + index * rule.counterStep
        var out = tokenRx.replace(rule.pattern) { m ->
            val arg = m.groupValues[2]
            when (m.groupValues[1]) {
                "name" -> working
                "ext" -> ext
                "parent" -> parentOf(item.path).substringAfterLast('/')
                "n" -> counter.toString().padStart(arg.toIntOrNull() ?: rule.counterPad, '0')
                else -> try {
                    SimpleDateFormat(arg.ifEmpty { "yyyyMMdd" }, Locale.ROOT).format(Date(item.lastModified))
                } catch (e: IllegalArgumentException) {
                    m.value
                }
            }
        }
        out = when (rule.case) {
            CaseMode.NONE -> out
            CaseMode.LOWER -> out.lowercase()
            CaseMode.UPPER -> out.uppercase()
            CaseMode.TITLE -> Regex("""(^|[\s_\-.])(\p{L})""").replace(out.lowercase()) { it.groupValues[1] + it.groupValues[2].uppercase() }
        }
        return if (hasExt && !rule.touchExtension) "$out.$ext" else out
    }
}

/** One physical rename, paths absolute (or smb://). */
data class RenameMove(val from: String, val to: String)

/** Two-phase schedule so swaps and cycles never clobber. Pure, unit-tested. */
data class RenameSchedule(val phase1: List<RenameMove>, val phase2: List<RenameMove>) {
    /** Every physical move in run order; replaying these keeps path-keyed data on the right file. */
    val moves: List<RenameMove> get() = phase1 + phase2

    companion object {
        fun of(entries: List<RenameEntry>, tag: String): RenameSchedule {
            val changing = entries.filter { it.path.substringAfterLast('/') != it.newName }
            val oldNames = changing.map { RenamePlanner.parentOf(it.path) to it.path.substringAfterLast('/').lowercase() }.toSet()
            val newNames = changing.map { RenamePlanner.parentOf(it.path) to it.newName.lowercase() }.toSet()
            val p1 = ArrayList<RenameMove>()
            val p2 = ArrayList<RenameMove>()
            changing.forEachIndexed { i, e ->
                val parent = RenamePlanner.parentOf(e.path)
                val old = parent to e.path.substringAfterLast('/').lowercase()
                val final = "$parent/${e.newName}"
                // Target held by a moving item, or my old name wanted by someone: go through a temp name.
                if ((parent to e.newName.lowercase()) in oldNames || old in newNames) {
                    val temp = "$parent/.fdxrn-$tag-$i"
                    p1 += RenameMove(e.path, temp)
                    p2 += RenameMove(temp, final)
                } else {
                    p2 += RenameMove(e.path, final)
                }
            }
            return RenameSchedule(p1, p2)
        }
    }
}

/** Executor handler for [FileOperation.BatchRename]. */
@Singleton
class BatchRenamer @Inject constructor(
    private val smb: SmbRepository,
    private val pathIndex: PathIndexDao
) {
    suspend fun run(op: FileOperation.BatchRename, touched: MutableList<String>, report: (OperationProgress) -> Unit) {
        val entries = op.renames
        if (entries.any { !RenamePlanner.validName(it.newName) }) throw FileOperationException(FailureReason.INVALID_NAME)
        val changing = entries.filter { it.path.substringAfterLast('/') != it.newName }
        // Re-validate: the folder may have changed since the preview.
        val keys = changing.map { RenamePlanner.parentOf(it.path) to it.newName.lowercase() }
        if (keys.toSet().size != keys.size) throw FileOperationException(FailureReason.NAME_CONFLICT)
        if (RenamePlanner.hasNested(changing.map { it.path })) throw FileOperationException(FailureReason.NESTED_SELECTION)
        for ((parent, group) in changing.groupBy { RenamePlanner.parentOf(it.path) }) {
            val siblings = siblingNames(parent)
            val freed = group.map { it.path.substringAfterLast('/') }.toSet()
            if (group.any { RenamePlanner.clashes(it.newName, siblings, freed) }) throw FileOperationException(FailureReason.NAME_CONFLICT)
        }

        val schedule = RenameSchedule.of(changing, op.id.take(8))
        val total = schedule.phase2.size
        val done = ArrayList<RenameMove>()
        try {
            for (m in schedule.phase1) {
                currentCoroutineContext().ensureActive()
                rename(m); done += m
            }
            schedule.phase2.forEachIndexed { i, m ->
                currentCoroutineContext().ensureActive()
                report(OperationProgress(op.id, m.to.substringAfterLast('/'), processedItems = i, totalItems = total))
                rename(m); done += m
            }
        } catch (e: Throwable) {
            withContext(NonCancellable) { done.asReversed().forEach { runCatching { rename(RenameMove(it.to, it.from)) } } }
            throw e
        }
        changing.forEach {
            touched += it.path; touched += "${RenamePlanner.parentOf(it.path)}/${it.newName}"
        }
        // Same order as on disk, so swaps and chains carry each file's tags through the temp names.
        pathIndex.onMovedAll(schedule.moves.map { it.from to it.to })
        report(OperationProgress(op.id, processedItems = total, totalItems = total, isComplete = true))
    }

    /** Fresh listing of [dir]: the folder may have changed since the preview. */
    private suspend fun siblingNames(dir: String): List<String> {
        val remote = SmbPath.parse(dir)
        return if (remote != null) smb.list(remote.first, remote.second).map { it.name }
        else File(dir).list()?.toList() ?: throw FileOperationException(FailureReason.ACCESS_DENIED)
    }

    private suspend fun rename(m: RenameMove) {
        val from = SmbPath.parse(m.from)
        val to = SmbPath.parse(m.to)
        if (from != null && to != null) smb.rename(from.first, from.second, to.second)
        else if (!File(m.from).renameTo(File(m.to))) throw FileOperationException(FailureReason.ACCESS_DENIED)
    }
}
