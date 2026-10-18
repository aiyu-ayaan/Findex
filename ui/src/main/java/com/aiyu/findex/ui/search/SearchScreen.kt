package com.aiyu.findex.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.components.isMediaExtension
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.explorer.DeleteDialog
import com.aiyu.findex.ui.explorer.FileList
import com.aiyu.findex.ui.explorer.ItemAction
import com.aiyu.findex.ui.explorer.ItemActionSheet
import com.aiyu.findex.ui.explorer.MediaViewerDialog
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.util.openFile
import com.aiyu.findex.ui.util.openFileWithChooser
import com.aiyu.findex.ui.util.shareFiles
import com.aiyu.findex.ui.viewer.InAppViewers
import java.io.File

/** Name search across every indexed volume. Folders open in the Explorer; images open in the built-in viewer; other files open externally. */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenArchive: (String) -> Unit,
    onOpenFile: (Any) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var actionsFor by remember { mutableStateOf<StorageItem?>(null) }
    var deletePaths by remember { mutableStateOf<List<String>?>(null) }
    var viewerInitialPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun open(item: StorageItem) {
        keyboard?.hide()
        when {
            item.isDirectory -> onOpenFolder(item.path)
            item.isArchive -> onOpenArchive(item.path)
            isMediaExtension(item.extension) -> {
                viewModel.onOpened(item.path)
                viewerInitialPath = item.path
            }
            else -> {
                viewModel.onOpened(item.path)
                InAppViewers.routeFor(item)?.let(onOpenFile) ?: context.openFile(item.path, item.size)
            }
        }
    }

    Scaffold(
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = Dimens.xs, end = readableGutter(), top = Dimens.sm, bottom = Dimens.sm)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                }
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f)
                ) {
                    SearchBarDefaults.InputField(
                        query = state.query,
                        onQueryChange = viewModel::onQueryChange,
                        onSearch = { keyboard?.hide() },
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChange("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear_search))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when {
                state.query.isBlank() -> SearchMessage(Icons.Rounded.Search, stringResource(R.string.search_prompt))
                state.isSearching && state.results.isEmpty() -> LoadingIndicator(Modifier.align(Alignment.Center))
                state.results.isEmpty() -> SearchMessage(Icons.Rounded.SearchOff, stringResource(R.string.search_no_results, state.query.trim()))
                else -> FileList(
                    items = state.results,
                    selection = emptySet(),
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(
                        start = readableGutter(), end = readableGutter(), top = Dimens.xs,
                        bottom = padding.calculateBottomPadding() + Dimens.xxl
                    ),
                    onClick = ::open,
                    onMore = { keyboard?.hide(); actionsFor = it },
                    onSelectionChange = {},
                    header = {},
                    subtitle = { File(it.path).parent.orEmpty() }
                )
            }
        }
    }

    actionsFor?.let { item ->
        ItemActionSheet(
            item = item,
            allowed = setOf(ItemAction.OPEN, ItemAction.OPEN_WITH, ItemAction.SHARE, ItemAction.SHOW_IN_FOLDER, ItemAction.DELETE),
            onDismiss = { actionsFor = null },
            onAction = { action ->
                actionsFor = null
                when (action) {
                    ItemAction.OPEN -> open(item)
                    ItemAction.OPEN_WITH -> {
                        viewModel.onOpened(item.path)
                        context.openFileWithChooser(item.path, item.size)
                    }
                    ItemAction.SHARE -> context.shareFiles(listOf(item.path))
                    ItemAction.SHOW_IN_FOLDER -> File(item.path).parent?.let(onOpenFolder)
                    ItemAction.DELETE -> deletePaths = listOf(item.path)
                    else -> Unit
                }
            }
        )
    }
    viewerInitialPath?.let { initialPath ->
        MediaViewerDialog(
            items = state.results,
            initialPath = initialPath,
            onDismiss = { viewerInitialPath = null },
            onDelete = { target -> deletePaths = listOf(target.path) }
        )
    }
    deletePaths?.let { paths ->
        DeleteDialog(paths.size, onConfirm = { permanent -> viewModel.delete(paths, permanent); deletePaths = null }, onDismiss = { deletePaths = null })
    }
}

@Composable
private fun SearchMessage(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(Dimens.xxl)
    ) {
        ShapeIcon(
            icon = icon,
            shape = MaterialShapes.Cookie9Sided,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
            size = 96.dp,
            iconSize = 40.dp
        )
        Spacer(Modifier.height(Dimens.lg))
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
