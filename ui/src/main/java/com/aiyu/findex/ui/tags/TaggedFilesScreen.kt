package com.aiyu.findex.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.FileThumbnail
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.util.formatSize
import com.aiyu.findex.ui.util.openFile
import com.aiyu.findex.ui.viewer.InAppViewers
import java.io.File

@Composable
fun TaggedFilesScreen(
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (Any) -> Unit = {},
    viewModel: TaggedFilesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.tag?.name ?: stringResource(R.string.tags_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } }
            )
        }
    ) { padding ->
        if (state.loaded && state.items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(stringResource(R.string.tags_files_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = readableGutter(), end = readableGutter(), top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Dimens.xxl)) {
            items(state.items, key = { it.path }) { item ->
                ListItem(
                    headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                    supportingContent = { Text(if (item.isDirectory) File(item.path).parent.orEmpty() else formatSize(item.size), maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                    leadingContent = { FileThumbnail(item.path, item.isDirectory, item.extension, lastModified = item.lastModified) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clip(MaterialTheme.shapes.large).clickable {
                        if (item.isDirectory) onOpenFolder(item.path)
                        else InAppViewers.routeFor(item)?.let(onOpenFile) ?: context.openFile(item.path, item.size)
                    }
                )
            }
        }
    }
}
