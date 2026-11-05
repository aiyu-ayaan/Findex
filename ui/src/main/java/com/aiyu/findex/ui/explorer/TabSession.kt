package com.aiyu.findex.ui.explorer

import com.aiyu.findex.core.db.OpenTabDao
import com.aiyu.findex.core.db.OpenTabEntity
import com.aiyu.findex.core.network.SmbPath
import com.aiyu.findex.core.network.SmbRepository
import com.aiyu.findex.core.repository.StorageRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** A folder on a tab's back history; it keeps its volume so Back can cross volumes. */
data class TabPlace(val root: String, val rootName: String, val path: String)

data class TabsState(val tabs: List<ExplorerTab> = emptyList(), val active: Int = 0) {
    val tab: ExplorerTab? get() = tabs.getOrNull(active)
}

/**
 * Open explorer tabs, shared by every Explorer screen, the Tabs overview and the drawer badge.
 * Saved to Room (`open_tabs`) so tabs survive process death and app restarts.
 */
@OptIn(FlowPreview::class)
@Singleton
class TabSession @Inject constructor(
    private val storageRepository: StorageRepository,
    private val openTabDao: OpenTabDao,
    private val smbRepository: SmbRepository
) {
    private val _state = MutableStateFlow(TabsState())
    val state: StateFlow<TabsState> = _state.asStateFlow()

    private val nextId = AtomicLong()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val restored = CompletableDeferred<Unit>()

    init {
        scope.launch { restoreAndPersist() }
    }

    private suspend fun restoreAndPersist() {
        // A removed SD card drops its tabs; a deleted folder falls back to its volume root.
        // Network tabs are kept only while their share is still saved; checking the folder would need the network.
        val servers = runCatching { smbRepository.servers.first().map { it.id }.toSet() }.getOrDefault(emptySet())
        val saved = runCatching { openTabDao.all() }.getOrDefault(emptyList()).filter { tab ->
            SmbPath.parse(tab.root)?.let { it.first in servers } ?: File(tab.root).isDirectory
        }
        val tabs = saved.map {
            val path = if (SmbPath.parse(it.path) != null || File(it.path).isDirectory) it.path else it.root
            ExplorerTab(nextId.getAndIncrement(), it.root, it.rootName, path)
        }
        val active = saved.indexOfFirst { it.isActive }.coerceAtLeast(0)
        // Tabs opened before the restore finished stay after the saved ones.
        _state.update { s -> if (tabs.isEmpty()) s else TabsState(tabs + s.tabs, if (s.tabs.isEmpty()) active else tabs.size + s.active) }
        restored.complete(Unit)
        _state.drop(1).distinctUntilChanged().debounce(300).collect { s ->
            openTabDao.replaceAll(s.tabs.mapIndexed { i, t -> OpenTabEntity(i, t.root, t.rootName, t.path, i == s.active) })
        }
    }

    /** Suspends until saved tabs are loaded, so callers don't open a fresh tab over a restored session. */
    suspend fun awaitRestored() = restored.await()

    /**
     * A folder picked outside the Explorer (Home, search, a category). Like ES File Explorer's windows: a tab
     * already showing [path] gets focus, otherwise the active tab goes there and keeps its back history.
     * New tabs only come from an explicit "New tab" / "Open in new tab".
     */
    suspend fun open(path: String) {
        val existing = _state.value.tabs.indexOfFirst { it.path == path }
        when {
            existing >= 0 -> select(existing)
            _state.value.tab == null -> newTab(path)
            else -> {
                val place = createTab(path)
                _state.update { s -> s.withActive { it.visit(place.root, place.rootName, place.path) } }
            }
        }
    }

    /** Always opens a new tab, even if another one shows [path]. */
    suspend fun newTab(path: String) {
        val tab = createTab(path)
        _state.update { s -> TabsState(s.tabs + tab, s.tabs.size) }
    }

    /** Opens a copy of the active tab next to it (the Explorer's "New tab"). */
    fun duplicateActive() = _state.update { s ->
        val current = s.tab ?: return@update s
        val tabs = s.tabs.toMutableList().apply { add(s.active + 1, current.copy(id = nextId.getAndIncrement(), history = emptyList())) }
        TabsState(tabs, s.active + 1)
    }

    fun select(index: Int) = _state.update { s -> if (index in s.tabs.indices) s.copy(active = index) else s }

    fun close(index: Int) = _state.update { s ->
        if (index !in s.tabs.indices) return@update s
        val tabs = s.tabs.toMutableList().apply { removeAt(index) }
        val active = when {
            index < s.active -> s.active - 1
            else -> s.active.coerceAtMost((tabs.size - 1).coerceAtLeast(0))
        }
        TabsState(tabs, active)
    }

    fun closeAll() = _state.update { TabsState() }

    /** Moves the active tab to [path] on its volume; [record] puts the folder it leaves on its back history. */
    fun setActivePath(path: String, record: Boolean = true) = _state.update { s ->
        s.withActive { if (record) it.visit(it.root, it.rootName, path) else it.copy(path = path) }
    }

    /** Steps the active tab back through its own history. @return false when it has none. */
    fun back(): Boolean {
        var moved = false
        _state.update { s ->
            moved = false
            s.withActive { tab -> tab.stepBack()?.also { moved = true } ?: tab }
        }
        return moved
    }

    private inline fun TabsState.withActive(change: (ExplorerTab) -> ExplorerTab): TabsState {
        val current = tab ?: return this
        return copy(tabs = tabs.toMutableList().apply { set(active, change(current)) })
    }


    /** The storage volume [path] lives on becomes the tab's root; up-navigation stops there. */
    private suspend fun createTab(path: String): ExplorerTab {
        SmbPath.parse(path)?.let { (id, _) ->
            val server = smbRepository.server(id)
            return ExplorerTab(nextId.getAndIncrement(), SmbPath.of(id, ""), server?.label ?: path, path)
        }
        val drive = storageRepository.getStorageDrives().first().filter { path.startsWith(it.path) }.maxByOrNull { it.path.length }
        return ExplorerTab(
            id = nextId.getAndIncrement(),
            root = drive?.path ?: path,
            rootName = drive?.name ?: File(path).name,
            path = path
        )
    }

}

// shortcut: history lives in memory only (not in open_tabs); persist it if users want Back to survive restarts.
private const val MAX_HISTORY = 50

/** The tab moved to [path]; the folder it leaves goes on its back history. */
internal fun ExplorerTab.visit(root: String, rootName: String, path: String): ExplorerTab {
    if (path == this.path && root == this.root) return this
    val history = (history + TabPlace(this.root, this.rootName, this.path)).takeLast(MAX_HISTORY)
    return copy(root = root, rootName = rootName, path = path, history = history)
}

/** The tab one step back in its history, or null when there is nowhere to go back to. */
internal fun ExplorerTab.stepBack(): ExplorerTab? {
    // An up-navigation can leave the previous entry equal to the current folder; skip it.
    val history = history.dropLastWhile { it.path == path }
    val previous = history.lastOrNull() ?: return null
    return copy(root = previous.root, rootName = previous.rootName, path = previous.path, history = history.dropLast(1))
}
