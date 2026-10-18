package com.aiyu.findex.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.repository.StorageRepository
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.CategoryShape
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.explorer.ExplorerTab
import com.aiyu.findex.ui.explorer.TabSession
import com.aiyu.findex.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class TabsViewModel @Inject constructor(
    private val tabSession: TabSession,
    private val storageRepository: StorageRepository
) : ViewModel() {
    val state = tabSession.state

    fun select(index: Int) = tabSession.select(index)
    fun close(index: Int) = tabSession.close(index)
    fun closeAll() = tabSession.closeAll()

    fun newTab(path: String, then: () -> Unit) {
        viewModelScope.launch {
            tabSession.newTab(path)
            then()
        }
    }
}

/** Overview of every open explorer tab, like a browser's tab switcher. Reached from the drawer. */
@Composable
fun TabsScreen(
    onBack: () -> Unit,
    onOpenExplorer: () -> Unit,
    viewModel: TabsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var newTabSheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.nav_tabs)) },
                subtitle = { Text(pluralStringResource(R.plurals.tabs_open_count, state.tabs.size, state.tabs.size)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } },
                actions = {
                    if (state.tabs.isNotEmpty()) {
                        IconButton(onClick = viewModel::closeAll) { Icon(Icons.Rounded.ClearAll, stringResource(R.string.tabs_close_all)) }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { newTabSheet = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_new_tab)) }
            )
        }
    ) { padding ->
        if (state.tabs.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(padding).padding(Dimens.xxl)
            ) {
                ShapeIcon(
                    icon = Icons.Rounded.Tab,
                    shape = CategoryShape,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    size = 120.dp,
                    iconSize = 48.dp
                )
                Spacer(Modifier.height(Dimens.xl))
                Text(stringResource(R.string.tabs_empty_title), style = MaterialTheme.typography.titleLargeEmphasized)
                Spacer(Modifier.height(Dimens.sm))
                Text(
                    stringResource(R.string.tabs_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }
        // Adaptive cells: two columns on phones, more on tablets, without a separate layout.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            contentPadding = PaddingValues(
                start = Dimens.gutter, end = Dimens.gutter,
                top = padding.calculateTopPadding() + Dimens.sm,
                // Clear the extended FAB.
                bottom = padding.calculateBottomPadding() + 96.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(state.tabs, key = { _, tab -> tab.id }) { index, tab ->
                TabCard(
                    tab = tab,
                    active = index == state.active,
                    onOpen = { viewModel.select(index); onOpenExplorer() },
                    onClose = { viewModel.close(index) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
    if (newTabSheet) {
        NewTabSheet(onPick = { newTabSheet = false; viewModel.newTab(it, onOpenExplorer) }, onDismiss = { newTabSheet = false })
    }
}

@Composable
private fun TabCard(tab: ExplorerTab, active: Boolean, onOpen: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val name = if (tab.path == tab.root) tab.rootName else File(tab.path).name
    // "Internal storage › DCIM › Camera": where the tab is, without the raw /storage/emulated/0 prefix.
    val trail = listOf(tab.rootName) + tab.path.removePrefix(tab.root).trim('/').split('/').filter { it.isNotEmpty() }
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (active) colors.primaryContainer else colors.surfaceContainer,
            contentColor = if (active) colors.onPrimaryContainer else colors.onSurface
        ),
        border = if (active) BorderStroke(2.dp, colors.primary) else null,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 148.dp)
            .semantics { selected = active }
    ) {
        Column(Modifier.padding(start = Dimens.lg, bottom = Dimens.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeIcon(
                    icon = Icons.Rounded.Folder,
                    shape = MaterialShapes.Cookie4Sided,
                    container = if (active) colors.primary else colors.secondaryContainer,
                    content = if (active) colors.onPrimary else colors.onSecondaryContainer,
                    size = 40.dp,
                    iconSize = 20.dp,
                    modifier = Modifier.padding(top = Dimens.lg)
                )
                Spacer(Modifier.weight(1f))
                // 48dp target in the corner; the card itself stays the primary tap target.
                IconButton(onClick = onClose, modifier = Modifier.padding(top = Dimens.xs, end = Dimens.xs)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close_tab, name))
                }
            }
            Spacer(Modifier.height(Dimens.md))
            Text(
                name,
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = Dimens.lg)
            )
            Text(
                trail.joinToString(" › "),
                style = MaterialTheme.typography.bodySmall,
                color = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = Dimens.lg)
            )
            if (active) {
                Spacer(Modifier.height(Dimens.sm))
                Text(stringResource(R.string.tabs_current), style = MaterialTheme.typography.labelMedium, color = colors.primary)
            }
        }
    }
}
