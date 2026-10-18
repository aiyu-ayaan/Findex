package com.aiyu.findex.ui.network

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.ui.LocalSnackbarHostState
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.explorer.FileList
import com.aiyu.findex.ui.explorer.ItemAction
import com.aiyu.findex.ui.explorer.ItemActionSheet
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.util.formatDate
import com.aiyu.findex.ui.util.openFile
import com.aiyu.findex.ui.util.shareFiles

/** Read-only browser for one SMB share; files are downloaded to the cache, then opened or shared. */
@Composable
fun NetworkBrowseScreen(onBack: () -> Unit, viewModel: NetworkBrowseViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var actionsFor by remember { mutableStateOf<StorageItem?>(null) }
    val listStates = remember { mutableMapOf<String, LazyListState>() }

    LaunchedEffect(Unit) {
        viewModel.ready.collect { (file, intent) ->
            when (intent) {
                RemoteIntent.OPEN -> context.openFile(file.absolutePath)
                RemoteIntent.SHARE -> context.shareFiles(listOf(file.absolutePath))
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.errors.collect { snackbar.showSnackbar(context.getString(it.messageRes())) }
    }

    BackHandler(enabled = state.dir.isNotEmpty()) { viewModel.up() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val server = state.server
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        state.dir.substringAfterLast('/').ifEmpty { server?.label.orEmpty() },
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis
                    )
                },
                subtitle = {
                    if (server != null) {
                        Text(
                            "\\\\${server.host}\\${server.share}" + if (state.dir.isEmpty()) "" else "\\" + state.dir.replace('/', '\\'),
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.up()) onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }, enabled = !state.isLoading) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.network_retry))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            val error = state.error
            when {
                state.isLoading -> LoadingIndicator(Modifier.align(Alignment.Center))
                error != null -> BrowseMessage(
                    icon = Icons.Rounded.CloudOff,
                    text = stringResource(error.messageRes()),
                    onRetry = { viewModel.load() },
                    modifier = Modifier.padding(padding)
                )
                state.items.isEmpty() -> BrowseMessage(
                    icon = Icons.Rounded.FolderOff,
                    text = stringResource(R.string.empty_folder_title),
                    onRetry = null,
                    modifier = Modifier.padding(padding)
                )
                else -> FileList(
                    items = state.items,
                    selection = emptySet(),
                    state = listStates.getOrPut(state.dir) { LazyListState() },
                    contentPadding = PaddingValues(
                        start = readableGutter(), end = readableGutter(),
                        top = padding.calculateTopPadding() + Dimens.sm,
                        bottom = padding.calculateBottomPadding() + 120.dp
                    ),
                    onClick = { viewModel.open(it) },
                    onMore = { actionsFor = it },
                    onSelectionChange = {},
                    header = {},
                    // SMB listings don't count a folder's children; show its date instead of "0 items".
                    subtitle = { if (it.isDirectory) formatDate(it.lastModified) else null }
                )
            }

            AnimatedVisibility(
                visible = state.downloading != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = Dimens.xl, start = Dimens.lg, end = Dimens.lg)
            ) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.inverseSurface) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Dimens.lg, end = Dimens.xs, top = Dimens.xs, bottom = Dimens.xs)) {
                        LoadingIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.inversePrimary)
                        Spacer(Modifier.width(Dimens.sm))
                        Text(
                            stringResource(R.string.network_downloading, state.downloading.orEmpty()),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        IconButton(onClick = viewModel::cancelDownload) {
                            Icon(Icons.Rounded.Close, stringResource(R.string.cancel), tint = MaterialTheme.colorScheme.inverseOnSurface)
                        }
                    }
                }
            }
        }
    }

    actionsFor?.let { item ->
        ItemActionSheet(
            item = item,
            allowed = setOf(ItemAction.OPEN, ItemAction.SHARE),
            onDismiss = { actionsFor = null },
            onAction = { action ->
                actionsFor = null
                when (action) {
                    ItemAction.OPEN -> viewModel.open(item)
                    ItemAction.SHARE -> viewModel.open(item, RemoteIntent.SHARE)
                    else -> Unit
                }
            }
        )
    }
}

@Composable
private fun BrowseMessage(icon: ImageVector, text: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize().padding(Dimens.xxl)
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
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) {
            Spacer(Modifier.height(Dimens.lg))
            FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.network_retry)) }
        }
    }
}
