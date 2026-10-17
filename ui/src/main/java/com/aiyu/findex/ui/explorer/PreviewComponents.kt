package com.aiyu.findex.ui.explorer

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaPlayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.ui.LocalSnackbarHostState
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.CategoryShape
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.theme.tone
import com.aiyu.findex.ui.util.formatSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

// region Image Preview & Fullscreen Lightbox

@Composable
fun ImagePreviewCard(
    item: StorageItem,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val file = remember(item.path) { File(item.path) }
    val meta by produceState<ImageMeta?>(null, item.path) {
        value = FileInspector.inspectImage(file)
    }
    var showLightbox by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.extraLarge
            )
    ) {
        AsyncImage(
            model = file,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 380.dp)
                .clickable { showLightbox = true }
                .padding(Dimens.xs)
        )

        // Overlay top-end: Fullscreen action button
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Dimens.sm)
        ) {
            IconButton(
                onClick = { showLightbox = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Rounded.Fullscreen,
                    contentDescription = stringResource(R.string.preview_fullscreen),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Overlay bottom-start: Resolution badge
        meta?.let { m ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Dimens.sm)
            ) {
                Text(
                    text = "${m.width} × ${m.height} (${String.format(Locale.ROOT, "%.1f MP", m.megapixels)})",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = Dimens.sm, vertical = Dimens.xs)
                )
            }
        }

        // Overlay bottom-end: File size
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

    if (showLightbox) {
        ImageLightboxDialog(
            item = item,
            meta = meta,
            onDismiss = { showLightbox = false },
            onOpenExternal = onOpenExternal
        )
    }
}

@Composable
fun ImageLightboxDialog(
    item: StorageItem,
    meta: ImageMeta?,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val file = remember(item.path) { File(item.path) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            // Interactive Zoomable Image
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.8f, 6f)
                            offset = if (scale > 1f) offset + pan else Offset.Zero
                        }
                    }
            ) {
                AsyncImage(
                    model = file,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
            }

            // Top Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = Dimens.lg, vertical = Dimens.md)
                    .align(Alignment.TopCenter)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    meta?.let {
                        Text(
                            text = "${it.width} × ${it.height} · ${formatSize(item.size)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (scale != 1f) {
                        IconButton(onClick = { scale = 1f; offset = Offset.Zero }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Reset Zoom", tint = Color.White)
                        }
                    }
                    IconButton(onClick = onOpenExternal) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.action_open), tint = Color.White)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close_details), tint = Color.White)
                    }
                }
            }

            // Bottom Hint
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Dimens.xl)
            ) {
                Text(
                    text = stringResource(R.string.preview_zoom_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(horizontal = Dimens.md, vertical = Dimens.xs)
                )
            }
        }
    }
}

// endregion

// region Video Hero Preview

@Composable
fun VideoPreviewCard(
    item: StorageItem,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val file = remember(item.path) { File(item.path) }
    val meta by produceState<VideoMeta?>(null, item.path) {
        value = FileInspector.inspectVideo(file)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.extraLarge
            )
            .clickable(onClick = onPlay)
    ) {
        AsyncImage(
            model = file,
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.65f))
                    )
                )
        )

        // Play Button
        FilledIconButton(
            onClick = onPlay,
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

        // Bottom Info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(Dimens.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            meta?.let { m ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    val formatted = formatDuration(m.durationMs)
                    val res = if (m.width > 0 && m.height > 0) " · ${m.width}×${m.height}" else ""
                    Text(
                        text = "$formatted$res",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = Dimens.sm, vertical = Dimens.xs)
                    )
                }
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Text(
                    text = formatSize(item.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = Dimens.sm, vertical = Dimens.xs)
                )
            }
        }
    }
}

// endregion

// region Audio Hero & In-Line Player

@Composable
fun AudioPreviewCard(
    item: StorageItem,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val file = remember(item.path) { File(item.path) }
    val meta by produceState<AudioMeta?>(null, item.path) {
        value = FileInspector.inspectAudio(file)
    }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    val player = remember { MediaPlayer() }

    DisposableEffect(item.path) {
        try {
            player.reset()
            player.setDataSource(file.absolutePath)
            player.prepare()
            duration = player.duration
            player.setOnCompletionListener {
                isPlaying = false
                currentPos = 0
            }
        } catch (_: Exception) {
            duration = 0
        }

        onDispose {
            try {
                if (player.isPlaying) player.stop()
                player.release()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                if (player.isPlaying) currentPos = player.currentPosition
            } catch (_: Exception) {}
            delay(250)
        }
    }

    val tone = FileCategory.AUDIO.tone

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = tone.accent.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.extraLarge
            )
            .padding(Dimens.lg)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                ShapeIcon(
                    icon = Icons.Rounded.MusicNote,
                    shape = MaterialShapes.Cookie9Sided,
                    container = tone.container,
                    content = tone.onContainer,
                    size = 56.dp,
                    iconSize = 28.dp
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Dimens.md)
                ) {
                    Text(
                        text = meta?.title ?: item.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    meta?.artist?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                FilledIconButton(
                    onClick = {
                        try {
                            if (isPlaying) {
                                player.pause()
                                isPlaying = false
                            } else {
                                player.start()
                                isPlaying = true
                            }
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = tone.container,
                        contentColor = tone.onContainer
                    )
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            if (duration > 0) {
                Spacer(Modifier.height(Dimens.md))
                val progress = if (duration > 0) currentPos.toFloat() / duration.toFloat() else 0f
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = tone.accent,
                    trackColor = tone.container.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(Dimens.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDuration(currentPos.toLong()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDuration(duration.toLong()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// endregion

// region Text / Code Hero Preview

@Composable
fun TextPreviewCard(
    item: StorageItem,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val file = remember(item.path) { File(item.path) }
    val snippet by produceState<TextSnippet?>(null, item.path) {
        value = FileInspector.inspectText(file)
    }
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.extraLarge
            )
    ) {
        Column {
            // Code Card Header Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = Dimens.md, vertical = Dimens.sm)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = item.extension.uppercase().ifEmpty { "TXT" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = Dimens.sm, vertical = Dimens.xxs)
                        )
                    }
                    snippet?.let { s ->
                        Spacer(Modifier.width(Dimens.sm))
                        Text(
                            text = stringResource(R.string.preview_lines_count, s.lineCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            snippet?.let { s ->
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                cm?.setPrimaryClip(android.content.ClipData.newPlainText("code", s.content))
                                scope.launch {
                                    snackbar.showSnackbar(context.getString(R.string.preview_text_copied))
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.preview_copy_text), modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onOpenExternal, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.action_open), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Code Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 280.dp)
                    .padding(Dimens.md)
                    .verticalScroll(rememberScrollState())
            ) {
                val current = snippet
                if (current == null) {
                    Text(
                        text = stringResource(R.string.preview_empty_file),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val lines = current.content.lines()
                    Column {
                        lines.forEachIndexed { i, line ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "%3d ".format(i + 1),
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    ),
                                    modifier = Modifier.width(36.dp)
                                )
                                Text(
                                    text = line,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// endregion

// region Archive Hero Preview

@Composable
fun ArchivePreviewCard(
    item: StorageItem,
    onExtract: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tone = FileCategory.ARCHIVES.tone

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = tone.accent.copy(alpha = 0.4f),
                shape = MaterialTheme.shapes.extraLarge
            )
            .padding(Dimens.xl)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            ShapeIcon(
                icon = Icons.Rounded.FolderZip,
                shape = MaterialShapes.Cookie9Sided,
                container = tone.container,
                content = tone.onContainer,
                size = 72.dp,
                iconSize = 36.dp
            )
            Spacer(Modifier.height(Dimens.md))
            Text(
                text = "${item.extension.uppercase()} Archive",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(Dimens.xxs))
            Text(
                text = formatSize(item.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Dimens.lg))
            Button(
                onClick = onExtract,
                colors = ButtonDefaults.buttonColors(
                    containerColor = tone.accent,
                    contentColor = tone.onContainer
                )
            ) {
                Icon(Icons.Rounded.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Dimens.sm))
                Text(stringResource(R.string.action_extract))
            }
        }
    }
}

// endregion

// region APK Hero Preview

@Composable
fun ApkPreviewCard(
    item: StorageItem,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val file = remember(item.path) { File(item.path) }
    val apkMeta by produceState<ApkMeta?>(null, item.path) {
        value = FileInspector.inspectApk(context, file)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.extraLarge
            )
            .padding(Dimens.xl)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            val drawable = apkMeta?.icon
            if (drawable is BitmapDrawable) {
                androidx.compose.foundation.Image(
                    bitmap = drawable.bitmap.asImageBitmap(),
                    contentDescription = apkMeta?.appName ?: item.name,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(MaterialTheme.shapes.large)
                )
            } else {
                ShapeIcon(
                    icon = Icons.Rounded.Android,
                    shape = MaterialShapes.SoftBurst,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 64.dp,
                    iconSize = 32.dp
                )
            }

            Spacer(Modifier.height(Dimens.md))
            Text(
                text = apkMeta?.appName ?: item.name,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            apkMeta?.packageName?.let { pkg ->
                Spacer(Modifier.height(Dimens.xxs))
                Text(
                    text = pkg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            apkMeta?.versionName?.let { ver ->
                Spacer(Modifier.height(Dimens.xxs))
                Text(
                    text = "v$ver · ${formatSize(item.size)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(Dimens.lg))
            FilledTonalButton(onClick = onInstall) {
                Icon(Icons.Rounded.Android, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Dimens.sm))
                Text(stringResource(R.string.action_install))
            }
        }
    }
}

// endregion

// region Folder Hero Preview

@Composable
fun FolderPreviewCard(
    item: StorageItem,
    details: FileDetails?,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.extraLarge
            )
            .padding(Dimens.xl)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            ShapeIcon(
                icon = Icons.Rounded.FolderOpen,
                shape = CategoryShape,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                size = 72.dp,
                iconSize = 36.dp
            )
            Spacer(Modifier.height(Dimens.md))
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Dimens.xxs))
            val d = details
            if (d != null) {
                Text(
                    text = "${formatSize(d.totalBytes)} · " +
                        pluralStringResource(R.plurals.file_count, d.fileCount, d.fileCount) + ", " +
                        pluralStringResource(R.plurals.folder_count, d.folderCount, d.folderCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = stringResource(R.string.storage_scanning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(Dimens.lg))
            Button(onClick = onOpenFolder) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Dimens.sm))
                Text(stringResource(R.string.action_open_folder))
            }
        }
    }
}

// endregion

// region Generic File Preview

@Composable
fun GenericPreviewCard(
    item: StorageItem,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 220.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.extraLarge
            )
            .clickable(onClick = onOpen)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ShapeIcon(
                icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                shape = MaterialShapes.Square,
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
                size = 64.dp,
                iconSize = 32.dp
            )
            Spacer(Modifier.height(Dimens.md))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = item.extension.uppercase().ifEmpty { stringResource(R.string.preview_file) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = Dimens.sm, vertical = Dimens.xs)
                )
            }
        }
    }
}

// endregion

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(Locale.ROOT, minutes, seconds)
}
