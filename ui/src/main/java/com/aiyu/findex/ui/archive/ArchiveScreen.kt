package com.aiyu.findex.ui.archive

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.ui.LocalSnackbarHostState
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.di.failureRes
import com.aiyu.findex.ui.explorer.ArchivePasswordDialog
import com.aiyu.findex.ui.explorer.ExtractDialog
import com.aiyu.findex.ui.explorer.FileList
import com.aiyu.findex.ui.explorer.ItemAction
import com.aiyu.findex.ui.explorer.ItemActionSheet
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.theme.tone
import com.aiyu.findex.ui.util.openFile
import com.aiyu.findex.ui.util.shareFiles

/** In-archive preview: browse folders inside an archive and open single files without a full extract. */
@Composable
fun ArchiveScreen(onBack: () -> Unit, viewModel: ArchiveViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var actionsFor by remember { mutableStateOf<StorageItem?>(null) }
    var extractAll by remember { mutableStateOf(false) }
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    LaunchedEffect(Unit) {
        viewModel.ready.collect { (file, intent) ->
            when (intent) {
                EntryIntent.OPEN -> context.openFile(file.absolutePath)
                EntryIntent.SHARE -> context.shareFiles(listOf(file.absolutePath))
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.errors.collect { snackbar.showSnackbar(context.getString(failureRes(it))) }
    }

    BackHandler(enabled = state.dir.isNotEmpty()) { viewModel.up() }

    val items = remember(state.entries, state.dir) { state.items }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(state.dir.substringAfterLast('/').ifEmpty { state.name }, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                subtitle = {
                    Text(
                        if (state.dir.isEmpty()) pluralStringResource(R.plurals.item_count, items.size, items.size)
                        else state.name + " › " + state.dir.replace("/", " › "),
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.up()) onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            if (!state.isLoading) {
                ExtendedFloatingActionButton(
                    onClick = { extractAll = true },
                    icon = { Icon(Icons.Rounded.Unarchive, null) },
                    text = { Text(stringResource(R.string.archive_extract_all)) }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when {
                state.isLoading -> LoadingIndicator(Modifier.align(Alignment.Center))
                state.unreadable || items.isEmpty() -> ArchiveMessage(
                    stringResource(if (state.unreadable) R.string.archive_unreadable else R.string.archive_empty),
                    Modifier.padding(padding)
                )
                else -> AnimatedContent(
                    targetState = state.dir,
                    transitionSpec = { fadeIn(effects) togetherWith fadeOut(effects) },
                    label = "archiveDir"
                ) { dir ->
                    // The outgoing folder keeps its own rows while it fades out.
                    val dirItems = remember(state.entries, dir) { state.copy(dir = dir).items }
                    FileList(
                        items = dirItems,
                        selection = emptySet(),
                        state = listStates.getOrPut(dir) { LazyListState() },
                        contentPadding = PaddingValues(
                            start = readableGutter(), end = readableGutter(),
                            top = padding.calculateTopPadding() + Dimens.sm,
                            bottom = padding.calculateBottomPadding() + 120.dp
                        ),
                        onClick = { viewModel.open(it) },
                        onMore = { actionsFor = it },
                        onSelectionChange = {},
                        header = {}
                    )
                }
            }

            AnimatedVisibility(
                visible = state.opening != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 96.dp)
            ) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.inverseSurface) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)) {
                        LoadingIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.inversePrimary)
                        Spacer(Modifier.width(Dimens.sm))
                        Text(
                            stringResource(R.string.archive_opening, state.opening.orEmpty()),
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis
                        )
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
                    ItemAction.SHARE -> viewModel.open(item, EntryIntent.SHARE)
                    else -> Unit
                }
            }
        )
    }
    state.passwordFor?.let { (entry, _) ->
        ArchivePasswordDialog(
            entryName = entry.substringAfterLast('/'),
            onConfirm = viewModel::submitPassword,
            onDismiss = viewModel::dismissPassword
        )
    }
    if (extractAll) {
        ExtractDialog(
            archiveName = state.name,
            onConfirm = { password -> viewModel.extractAll(password); extractAll = false; onBack() },
            onDismiss = { extractAll = false }
        )
    }
}

@Composable
private fun ArchiveMessage(text: String, modifier: Modifier = Modifier) {
    val tone = FileCategory.ARCHIVES.tone
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize().padding(Dimens.xxl)
    ) {
        ShapeIcon(
            icon = Icons.Rounded.FolderZip,
            shape = MaterialShapes.Cookie9Sided,
            container = tone.container,
            content = tone.onContainer,
            size = 96.dp,
            iconSize = 40.dp
        )
        Spacer(Modifier.height(Dimens.lg))
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
