package com.aiyu.findex.ui.explorer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.ui.LocalSnackbarHostState
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.CategoryShape
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.components.categoryOf
import com.aiyu.findex.ui.components.icon
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.theme.tone
import com.aiyu.findex.ui.util.formatDate
import com.aiyu.findex.ui.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Width of the list pane when the explorer shows list-detail (MD3 canonical layout for file browsers). */
val ExplorerListPaneWidth = 390.dp

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "svg", "avif")
private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts")
private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "opus", "amr")
private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "json", "xml", "html", "htm", "css", "js", "ts", "kt", "kts",
    "java", "py", "c", "cpp", "h", "hpp", "sh", "yml", "yaml", "log", "csv",
    "sql", "properties", "gradle", "toml", "conf", "ini", "rc"
)

/** Actions offered by the detail pane; each maps to what the item sheet already does on phones. */
class DetailActions(
    val onOpen: () -> Unit,
    val onShare: () -> Unit,
    val onCopy: () -> Unit,
    val onCut: () -> Unit,
    val onRename: () -> Unit,
    val onCompress: () -> Unit,
    val onExtract: () -> Unit,
    val onDelete: () -> Unit,
    val onClose: () -> Unit,
    val onNewFolder: (() -> Unit)? = null
)

/**
 * Expanded-width detail pane: large responsive preview, metadata, and expressive actions.
 * Empty state displays folder information until a file is focused.
 */
@Composable
fun ExplorerDetailPane(
    item: StorageItem?,
    folderName: String,
    folderItemCount: Int,
    load: suspend (StorageItem) -> FileDetails,
    actions: DetailActions,
    modifier: Modifier = Modifier
) {
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = item,
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            contentKey = { it?.path },
            label = "detail"
        ) { current ->
            if (current == null) {
                EmptyDetail(folderName, folderItemCount, actions.onNewFolder)
            } else {
                ItemDetail(current, load, actions)
            }
        }
    }
}

@Composable
private fun EmptyDetail(folderName: String, count: Int, onNewFolder: (() -> Unit)?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.xxl)
    ) {
        ShapeIcon(
            icon = Icons.Rounded.FolderOpen,
            shape = CategoryShape,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            size = 96.dp,
            iconSize = 44.dp
        )
        Spacer(Modifier.height(Dimens.xl))
        Text(
            text = folderName,
            style = MaterialTheme.typography.titleLargeEmphasized,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(Dimens.xs))
        Text(
            text = pluralStringResource(R.plurals.item_count, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Dimens.md))
        Text(
            text = stringResource(R.string.detail_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onNewFolder != null) {
            Spacer(Modifier.height(Dimens.xl))
            FilledTonalButton(onClick = onNewFolder) {
                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Dimens.sm))
                Text(stringResource(R.string.action_new_folder))
            }
        }
    }
}

@Composable
private fun ItemDetail(
    item: StorageItem,
    load: suspend (StorageItem) -> FileDetails,
    actions: DetailActions
) {
    val details by produceState<FileDetails?>(null, item) { value = load(item) }
    val ext = item.extension.lowercase()
    val isImg = ext in IMAGE_EXTENSIONS
    val isVid = ext in VIDEO_EXTENSIONS
    val isAud = ext in AUDIO_EXTENSIONS
    val isTxt = ext in TEXT_EXTENSIONS
    val category = categoryOf(item.extension)
    val categoryTone = category?.tone

    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Dimens.xl)
    ) {
        // Top header row: Category badge + dismiss
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = categoryTone?.container ?: MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = Dimens.sm, vertical = Dimens.xs)
                ) {
                    Icon(
                        category?.icon ?: Icons.AutoMirrored.Rounded.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = categoryTone?.onContainer ?: MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(Dimens.xs))
                    Text(
                        text = if (item.isDirectory) stringResource(R.string.details_type).uppercase() else ext.uppercase().ifEmpty { stringResource(R.string.preview_file) },
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryTone?.onContainer ?: MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onShare) {
                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.action_share))
                }
                IconButton(onClick = actions.onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close_details))
                }
            }
        }

        Spacer(Modifier.height(Dimens.md))

        // Hero Preview section
        when {
            isImg -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 360.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable(onClick = actions.onOpen)
                ) {
                    AsyncImage(
                        model = File(item.path),
                        contentDescription = item.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Dimens.xs)
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(Dimens.sm)
                    ) {
                        Text(
                            text = formatSize(item.size),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = Dimens.sm, vertical = Dimens.xs)
                        )
                    }
                }
            }
            isVid -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 320.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable(onClick = actions.onOpen)
                ) {
                    AsyncImage(
                        model = File(item.path),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f))
                    )
                    FilledIconButton(
                        onClick = actions.onOpen,
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.preview_play_video),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            isAud -> {
                val tone = categoryTone ?: FileCategory.AUDIO.tone
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 220.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(tone.container)
                        .clickable(onClick = actions.onOpen)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ShapeIcon(
                            icon = Icons.Rounded.MusicNote,
                            shape = MaterialShapes.Cookie9Sided,
                            container = tone.accent,
                            content = tone.onContainer,
                            size = 64.dp,
                            iconSize = 32.dp
                        )
                        Spacer(Modifier.height(Dimens.md))
                        FilledTonalButton(
                            onClick = actions.onOpen,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = tone.accent,
                                contentColor = tone.onContainer
                            )
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Dimens.xs))
                            Text(stringResource(R.string.preview_play_audio))
                        }
                    }
                }
            }
            isTxt -> {
                val textSnippet by produceState<String?>(null, item.path) {
                    value = withContext(Dispatchers.IO) {
                        try {
                            val f = File(item.path)
                            if (f.exists() && f.length() in 1..(10 * 1024 * 1024)) {
                                f.bufferedReader().use { reader ->
                                    val buf = CharArray(2048)
                                    val n = reader.read(buf)
                                    if (n > 0) String(buf, 0, n) else ""
                                }
                            } else null
                        } catch (_: Exception) {
                            null
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 280.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(Dimens.md)
                ) {
                    val snippet = textSnippet
                    if (snippet == null) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ShapeIcon(
                                icon = Icons.Rounded.Description,
                                shape = MaterialShapes.Cookie6Sided,
                                container = MaterialTheme.colorScheme.secondaryContainer,
                                content = MaterialTheme.colorScheme.onSecondaryContainer,
                                size = 56.dp,
                                iconSize = 28.dp
                            )
                        }
                    } else if (snippet.isEmpty()) {
                        Text(
                            text = stringResource(R.string.preview_empty_file),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        Text(
                            text = snippet,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            maxLines = 14,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            item.isArchive -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 220.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ShapeIcon(
                            icon = Icons.Rounded.FolderZip,
                            shape = MaterialShapes.Cookie9Sided,
                            container = MaterialTheme.colorScheme.tertiaryContainer,
                            content = MaterialTheme.colorScheme.onTertiaryContainer,
                            size = 72.dp,
                            iconSize = 36.dp
                        )
                    }
                }
            }
            item.isDirectory -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 200.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ShapeIcon(
                            icon = Icons.Rounded.Folder,
                            shape = MaterialShapes.Square,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            content = MaterialTheme.colorScheme.onPrimaryContainer,
                            size = 64.dp,
                            iconSize = 32.dp
                        )
                    }
                }
            }
            ext == "apk" -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 200.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    ShapeIcon(
                        icon = Icons.Rounded.Android,
                        shape = MaterialShapes.SoftBurst,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                        size = 64.dp,
                        iconSize = 32.dp
                    )
                }
            }
            else -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 200.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    ShapeIcon(
                        icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                        shape = MaterialShapes.Square,
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 64.dp,
                        iconSize = 32.dp
                    )
                }
            }
        }

        Spacer(Modifier.height(Dimens.lg))

        // Title & metadata chips
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleLargeEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(Dimens.xs))
        Text(
            text = formatSize(item.size) + " · " + formatDate(item.lastModified),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(Dimens.lg))

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            if (item.isArchive) {
                Button(
                    onClick = actions.onExtract,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Dimens.sm))
                    Text(stringResource(R.string.action_extract))
                }
                FilledTonalButton(onClick = actions.onOpen) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Dimens.sm))
                    Text(stringResource(R.string.action_open))
                }
            } else {
                Button(
                    onClick = actions.onOpen,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Dimens.sm))
                    Text(stringResource(R.string.action_open))
                }
                FilledTonalButton(onClick = actions.onShare) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Dimens.sm))
                    Text(stringResource(R.string.action_share))
                }
            }
        }

        Spacer(Modifier.height(Dimens.sm))

        // Secondary Icon Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                ActionIcon(Icons.Rounded.ContentCopy, R.string.action_copy, actions.onCopy)
                ActionIcon(Icons.Rounded.ContentCut, R.string.action_cut, actions.onCut)
                ActionIcon(Icons.Rounded.DriveFileRenameOutline, R.string.action_rename, actions.onRename)
                if (item.isArchive) {
                    ActionIcon(Icons.Rounded.Unarchive, R.string.action_extract, actions.onExtract)
                } else {
                    ActionIcon(Icons.Rounded.FolderZip, R.string.action_compress, actions.onCompress)
                }
            }

            FilledTonalIconButton(
                onClick = actions.onDelete,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }

        Spacer(Modifier.height(Dimens.xl))

        // Properties Card
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(Dimens.lg)) {
                // Location row with copy button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.details_path),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(Dimens.xxs))
                        Text(
                            item.path,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            cm?.setPrimaryClip(android.content.ClipData.newPlainText("path", item.path))
                            scope.launch {
                                snackbar.showSnackbar(context.getString(R.string.path_copied))
                            }
                        }
                    ) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.action_copy_path),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = Dimens.md)
                )

                // Property grid
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DetailProp(
                        label = stringResource(R.string.details_size),
                        value = formatSize(details?.totalBytes ?: item.size),
                        modifier = Modifier.weight(1f)
                    )
                    DetailProp(
                        label = stringResource(R.string.details_type),
                        value = item.mimeType ?: item.extension.uppercase().ifEmpty { "-" },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(Dimens.md))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DetailProp(
                        label = stringResource(R.string.details_modified),
                        value = formatDate(item.lastModified),
                        modifier = Modifier.weight(1f)
                    )
                    if (item.isDirectory) {
                        val d = details
                        if (d != null) {
                            DetailProp(
                                label = stringResource(R.string.details_contains),
                                value = pluralStringResource(R.plurals.file_count, d.fileCount, d.fileCount) + ", " +
                                    pluralStringResource(R.plurals.folder_count, d.folderCount, d.folderCount),
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailProp(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(Dimens.xxs))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, labelRes: Int, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick) {
        Icon(icon, contentDescription = stringResource(labelRes))
    }
}
