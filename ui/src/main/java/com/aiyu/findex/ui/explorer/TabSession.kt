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

    /** Focuses a tab already showing [path], otherwise opens a new one there. */
    suspend fun open(path: String) {
        val existing = _state.value.tabs.indexOfFirst { it.path == path }
        if (existing >= 0) select(existing) else newTab(path)
    }

    /** Always opens a new tab, even if another one shows [path]. */
    suspend fun newTab(path: String) {
        val tab = createTab(path)
        _state.update { s -> TabsState(s.tabs + tab, s.tabs.size) }
    }

    /** Opens a copy of the active tab next to it (the Explorer's "New tab"). */
    fun duplicateActive() = _state.update { s ->
        val current = s.tab ?: return@update s
        val tabs = s.tabs.toMutableList().apply { add(s.active + 1, current.copy(id = nextId.getAndIncrement())) }
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

    fun setActivePath(path: String) = _state.update { s ->
        val current = s.tab ?: return@update s
        s.copy(tabs = s.tabs.toMutableList().apply { set(s.active, current.copy(path = path)) })
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
