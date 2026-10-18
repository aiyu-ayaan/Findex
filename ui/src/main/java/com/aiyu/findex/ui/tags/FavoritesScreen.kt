package com.aiyu.findex.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.repository.Tag
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.FileThumbnail
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.theme.TagTones
import com.aiyu.findex.ui.util.openFile
import com.aiyu.findex.ui.viewer.InAppViewers
import java.io.File

/** [onOpenFile] navigates to a viewer route. */
@Composable
fun FavoritesScreen(onBack: () -> Unit, onOpenFolder: (String) -> Unit, onOpenTag: (Long) -> Unit, onOpenFile: (Any) -> Unit, viewModel: FavoritesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Tag?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Tag?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.favorites_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } }
            )
        }
    ) { padding ->
        if (state.loaded && state.folders.isEmpty() && state.files.isEmpty() && state.tags.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(stringResource(R.string.favorites_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = readableGutter(), end = readableGutter(), top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Dimens.xxl)) {
            if (state.folders.isNotEmpty()) item("h-folders") { Header(R.string.favorites_folders) }
            items(state.folders, key = { "d" + it.path }) { b ->
                ListItem(
                    headlineContent = { Text(b.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(b.path, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                    leadingContent = { FileThumbnail(b.path, true, "") },
                    trailingContent = { IconButton(onClick = { viewModel.removeBookmark(b.path) }) { Icon(Icons.Rounded.Bookmark, stringResource(R.string.action_remove_bookmark)) } },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.clip(MaterialTheme.shapes.large).clickable { onOpenFolder(b.path) }
                )
            }
            if (state.files.isNotEmpty()) item("h-files") { Header(R.string.favorites_files) }
            items(state.files, key = { "f" + it.path }) { b ->
                val file = File(b.path)
                ListItem(
                    headlineContent = { Text(b.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                    supportingContent = { Text(file.parent.orEmpty(), maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                    leadingContent = { FileThumbnail(b.path, false, file.extension, lastModified = file.lastModified()) },
                    trailingContent = { IconButton(onClick = { viewModel.removeBookmark(b.path) }) { Icon(Icons.Rounded.Bookmark, stringResource(R.string.action_remove_bookmark)) } },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.clip(MaterialTheme.shapes.large).clickable {
                        val item = StorageItem(b.path, b.name, extension = file.extension.lowercase())
                        InAppViewers.routeFor(item)?.let(onOpenFile) ?: context.openFile(b.path)
                    }
                )
            }
            item("h-tags") { Header(R.string.favorites_tags, action = { IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.tags_new)) } }) }
            if (state.tags.isEmpty()) item("tags-empty") { Text(stringResource(R.string.tags_empty), Modifier.padding(Dimens.lg), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(state.tags, key = { "t" + it.id }) { tag ->
                ListItem(
                    headlineContent = { Text(tag.name) },
                    leadingContent = { Box(Modifier.size(Dimens.xl).clip(CircleShape).background(TagTones[tag.colorIndex].accent)) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editing = tag }) { Icon(Icons.Rounded.Edit, stringResource(R.string.tags_rename)) }
                            IconButton(onClick = { deleting = tag }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.clip(MaterialTheme.shapes.large).clickable { onOpenTag(tag.id) }
                )
            }
        }
    }

    if (creating) TagEditDialog(null, { creating = false }) { n, c -> creating = false; viewModel.createTag(n, c) }
    editing?.let { t -> TagEditDialog(t, { editing = null }) { n, c -> editing = null; viewModel.updateTag(t, n, c) } }
    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.tags_delete_title, t.name)) },
            text = { Text(stringResource(R.string.tags_delete_body)) },
            confirmButton = { TextButton(onClick = { deleting = null; viewModel.deleteTag(t) }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun Header(titleRes: Int, action: @Composable () -> Unit = {}) {
    Row(Modifier.padding(start = Dimens.lg, top = Dimens.lg), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(titleRes), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        action()
    }
}
