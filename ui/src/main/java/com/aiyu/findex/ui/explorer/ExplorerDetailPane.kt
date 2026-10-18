package com.aiyu.findex.ui.explorer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/** Width of the supporting detail pane; the file list takes the rest (MD3 supporting-pane layout). */
val ExplorerDetailPaneWidth = 360.dp

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
    val onNewFolder: (() -> Unit)? = null,
    val onOpenFolder: ((String) -> Unit)? = null
)

/**
 * Expanded-width detail pane: modern responsive inspector with rich hero previews,
 * metadata property cards, on-demand checksums, and expressive action bar.
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
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
            size = 88.dp,
            iconSize = 40.dp
        )
        Spacer(Modifier.height(Dimens.lg))
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
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
        ) {
            Text(
                text = stringResource(R.string.detail_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)
            )
        }
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
    val file = remember(item.path) { File(item.path) }

    // Media metadata extraction
    val imageMeta by produceState<ImageMeta?>(null, item.path) {
        if (isImg) value = FileInspector.inspectImage(file)
    }
    val videoMeta by produceState<VideoMeta?>(null, item.path) {
        if (isVid) value = FileInspector.inspectVideo(file)
    }
    val audioMeta by produceState<AudioMeta?>(null, item.path) {
        if (isAud) value = FileInspector.inspectAudio(file)
    }

    val copy: (String, String, Int) -> Unit = { label, text, message ->
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        cm?.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        scope.launch { snackbar.showSnackbar(context.getString(message)) }
    }
    val openPrimary = if (item.isDirectory) {
        { actions.onOpenFolder?.invoke(item.path) ?: actions.onOpen() }
    } else actions.onOpen

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.lg, vertical = Dimens.md)
    ) {
        // 1. Header: category chip + close
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = categoryTone?.container ?: MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = Dimens.sm, vertical = Dimens.xs)
                ) {
                    Icon(
                        category?.icon ?: Icons.AutoMirrored.Rounded.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = categoryTone?.onContainer ?: MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.width(Dimens.xs))
                    val categoryLabel = if (item.isDirectory) {
                        stringResource(R.string.details_type).uppercase()
                    } else {
                        category?.name ?: ext.uppercase().ifEmpty { stringResource(R.string.preview_file) }
                    }
                    Text(
                        text = categoryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryTone?.onContainer ?: MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = actions.onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close_details))
            }
        }

        Spacer(Modifier.height(Dimens.sm))

        // 2. Hero preview, capped so metadata stays above the fold
        when {
            isImg -> ImagePreviewCard(item = item, onOpenExternal = actions.onOpen)
            isVid -> VideoPreviewCard(item = item, onPlay = actions.onOpen)
            isAud -> AudioPreviewCard(item = item, onOpenExternal = actions.onOpen)
            isTxt -> TextPreviewCard(item = item, onOpenExternal = actions.onOpen)
            item.isArchive -> ArchivePreviewCard(item = item)
            item.isDirectory -> FolderPreviewCard(item = item, details = details)
            ext == "apk" -> ApkPreviewCard(item = item)
            else -> GenericPreviewCard(item = item, onOpen = actions.onOpen)
        }

        Spacer(Modifier.height(Dimens.md))

        // 3. Identity
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleMediumEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(Dimens.xxs))
        Text(
            text = "${formatSize(details?.totalBytes ?: item.size)} · ${formatDate(item.lastModified)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(Dimens.md))

        // 4. One primary action, the rest as a compact icon toolbar
        Button(
            onClick = if (item.isArchive) actions.onExtract else openPrimary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                when {
                    item.isArchive -> Icons.Rounded.Unarchive
                    item.isDirectory -> Icons.Rounded.FolderOpen
                    else -> Icons.AutoMirrored.Rounded.OpenInNew
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(Dimens.sm))
            Text(
                stringResource(
                    when {
                        item.isArchive -> R.string.action_extract
                        item.isDirectory -> R.string.action_open_folder
                        else -> R.string.action_open
                    }
                )
            )
        }

        Spacer(Modifier.height(Dimens.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionIcon(Icons.Rounded.Share, R.string.action_share, actions.onShare)
            ActionIcon(Icons.Rounded.ContentCopy, R.string.action_copy, actions.onCopy)
            ActionIcon(Icons.Rounded.ContentCut, R.string.action_cut, actions.onCut)
            ActionIcon(Icons.Rounded.DriveFileRenameOutline, R.string.action_rename, actions.onRename)
            if (item.isArchive) {
                ActionIcon(Icons.AutoMirrored.Rounded.OpenInNew, R.string.action_open, actions.onOpen)
            } else {
                ActionIcon(Icons.Rounded.FolderZip, R.string.action_compress, actions.onCompress)
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

        Spacer(Modifier.height(Dimens.lg))

        // 5. Properties: one label/value row each, fits the narrow supporting pane
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.md)) {
                Text(
                    text = stringResource(R.string.details_overview),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(Dimens.xs))
                val total = details?.totalBytes ?: item.size
                DetailProp(
                    stringResource(R.string.details_size),
                    "${formatSize(total)} (${String.format(Locale.ROOT, "%,d", total)} B)"
                )
                DetailProp(stringResource(R.string.details_type), item.mimeType ?: ext.uppercase().ifEmpty { "-" })
                DetailProp(stringResource(R.string.details_modified), formatDate(item.lastModified))
                imageMeta?.let { im ->
                    DetailProp(
                        stringResource(R.string.details_dimensions),
                        "${im.width} × ${im.height} (${String.format(Locale.ROOT, "%.1f MP", im.megapixels)})"
                    )
                }
                videoMeta?.let { vm ->
                    val res = if (vm.width > 0 && vm.height > 0) " · ${vm.width}×${vm.height}" else ""
                    DetailProp(stringResource(R.string.details_duration), formatDuration(vm.durationMs) + res)
                }
                audioMeta?.let { am ->
                    DetailProp(stringResource(R.string.details_duration), formatDuration(am.durationMs))
                }
                val d = details
                if (item.isDirectory && d != null) {
                    DetailProp(
                        stringResource(R.string.details_contains),
                        pluralStringResource(R.plurals.file_count, d.fileCount, d.fileCount) + ", " +
                            pluralStringResource(R.plurals.folder_count, d.folderCount, d.folderCount)
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = Dimens.xs),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.details_path),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            item.path,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { copy("path", item.path, R.string.path_copied) }) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.action_copy_path),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (!item.isDirectory) {
            Spacer(Modifier.height(Dimens.sm))

            // 6. On-demand checksums (MD5 & SHA-256)
            var checksums by remember(item.path) { mutableStateOf<FileChecksums?>(null) }
            var isCalculating by remember(item.path) { mutableStateOf(false) }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.sm)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.minTouch)
                    ) {
                        Icon(
                            Icons.Rounded.Numbers,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(Dimens.sm))
                        Text(
                            text = stringResource(R.string.details_checksums),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        if (checksums == null) {
                            if (isCalculating) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                TextButton(onClick = {
                                    isCalculating = true
                                    scope.launch {
                                        checksums = FileInspector.calculateChecksums(file)
                                        isCalculating = false
                                    }
                                }) {
                                    Text(stringResource(R.string.details_calculate_hash))
                                }
                            }
                        }
                    }
                    checksums?.let { chk ->
                        HashRow("MD5", chk.md5) { copy("MD5", chk.md5, R.string.details_copied_hash) }
                        HashRow("SHA-256", chk.sha256) { copy("SHA-256", chk.sha256, R.string.details_copied_hash) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HashRow(algorithm: String, hash: String, onCopy: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.minTouch)
            .clickable(onClickLabel = stringResource(R.string.action_copy), onClick = onCopy)
            .padding(vertical = Dimens.xs)
    ) {
        Column(Modifier.weight(1f)) {
            Text(algorithm, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(hash, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            Icons.Rounded.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetailProp(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.xs)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, labelRes: Int, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick) {
        Icon(icon, contentDescription = stringResource(labelRes))
    }
}
