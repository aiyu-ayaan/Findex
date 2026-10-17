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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.xl, vertical = Dimens.lg)
    ) {
        // 1. Top Header Row: Category Badge + Dismiss/Share Actions
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
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

        // 2. Hero Preview Section
        when {
            isImg -> ImagePreviewCard(
                item = item,
                onOpenExternal = actions.onOpen
            )
            isVid -> VideoPreviewCard(
                item = item,
                onPlay = actions.onOpen
            )
            isAud -> AudioPreviewCard(
                item = item,
                onOpenExternal = actions.onOpen
            )
            isTxt -> TextPreviewCard(
                item = item,
                onOpenExternal = actions.onOpen
            )
            item.isArchive -> ArchivePreviewCard(
                item = item,
                onExtract = actions.onExtract
            )
            item.isDirectory -> FolderPreviewCard(
                item = item,
                details = details,
                onOpenFolder = { actions.onOpenFolder?.invoke(item.path) ?: actions.onOpen() }
            )
            ext == "apk" -> ApkPreviewCard(
                item = item,
                onInstall = actions.onOpen
            )
            else -> GenericPreviewCard(
                item = item,
                onOpen = actions.onOpen
            )
        }

        Spacer(Modifier.height(Dimens.lg))

        // 3. File Identity Section
        Text(
            text = item.name,
            style = MaterialTheme.typography.titleLargeEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(Dimens.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatSize(item.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = " · ${formatDate(item.lastModified)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(Dimens.lg))

        // 4. Action Buttons Section
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

        // 5. Secondary Quick Actions Toolbar
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

        // 6. Sectioned Information Cards
        // Card A: Properties & Technical Specs
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(Dimens.lg)) {
                Text(
                    text = stringResource(R.string.details_overview),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(Dimens.md))

                // Row 1: Size & Exact Bytes
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val total = details?.totalBytes ?: item.size
                    DetailProp(
                        label = stringResource(R.string.details_size),
                        value = "${formatSize(total)} (${String.format(Locale.ROOT, "%,d", total)} B)",
                        modifier = Modifier.weight(1f)
                    )
                    DetailProp(
                        label = stringResource(R.string.details_type),
                        value = item.mimeType ?: ext.uppercase().ifEmpty { "-" },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2: Modified date & Media Specs (Dimensions / Duration / Counts)
                Spacer(Modifier.height(Dimens.md))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DetailProp(
                        label = stringResource(R.string.details_modified),
                        value = formatDate(item.lastModified),
                        modifier = Modifier.weight(1f)
                    )

                    when {
                        imageMeta != null -> {
                            val im = imageMeta!!
                            DetailProp(
                                label = stringResource(R.string.details_dimensions),
                                value = "${im.width} × ${im.height} (${String.format(Locale.ROOT, "%.1f MP", im.megapixels)})",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        videoMeta != null -> {
                            val vm = videoMeta!!
                            val dur = formatDuration(vm.durationMs)
                            val res = if (vm.width > 0 && vm.height > 0) " · ${vm.width}×${vm.height}" else ""
                            DetailProp(
                                label = stringResource(R.string.details_duration),
                                value = "$dur$res",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        audioMeta != null -> {
                            val am = audioMeta!!
                            DetailProp(
                                label = stringResource(R.string.details_duration),
                                value = formatDuration(am.durationMs),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        item.isDirectory -> {
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
                        }
                        else -> Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(Dimens.md))

        // Card B: Location Card with Copy Button
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.lg)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.details_path),
                        style = MaterialTheme.typography.labelSmall,
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
        }

        if (!item.isDirectory) {
            Spacer(Modifier.height(Dimens.md))

            // Card C: On-Demand Checksums (MD5 & SHA-256)
            var checksums by remember(item.path) { mutableStateOf<FileChecksums?>(null) }
            var isCalculating by remember(item.path) { mutableStateOf(false) }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(Dimens.lg)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Numbers,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(Dimens.xs))
                            Text(
                                text = stringResource(R.string.details_checksums),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (checksums == null) {
                            if (isCalculating) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                FilledTonalButton(
                                    onClick = {
                                        isCalculating = true
                                        scope.launch {
                                            checksums = FileInspector.calculateChecksums(file)
                                            isCalculating = false
                                        }
                                    },
                                    contentPadding = ButtonDefaults.TextButtonContentPadding
                                ) {
                                    Text(stringResource(R.string.details_calculate_hash), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    checksums?.let { chk ->
                        Spacer(Modifier.height(Dimens.md))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(Modifier.height(Dimens.sm))

                        HashRow("MD5", chk.md5) {
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            cm?.setPrimaryClip(android.content.ClipData.newPlainText("MD5", chk.md5))
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.details_copied_hash)) }
                        }

                        Spacer(Modifier.height(Dimens.xs))

                        HashRow("SHA-256", chk.sha256) {
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            cm?.setPrimaryClip(android.content.ClipData.newPlainText("SHA-256", chk.sha256))
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.details_copied_hash)) }
                        }
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
            .clickable(onClick = onCopy)
            .padding(vertical = Dimens.xs)
    ) {
        Column(Modifier.weight(1f)) {
            Text(algorithm, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Text(hash, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy $algorithm", modifier = Modifier.size(16.dp))
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
