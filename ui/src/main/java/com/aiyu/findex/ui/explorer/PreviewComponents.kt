package com.aiyu.findex.ui.explorer

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaPlayer
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.network.SmbPath
import com.aiyu.findex.ui.LocalSnackbarHostState
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.CategoryShape
import com.aiyu.findex.ui.components.FileThumbnail
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.components.Thumb
import com.aiyu.findex.ui.components.isMediaExtension
import com.aiyu.findex.ui.components.isVideoExtension
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.theme.tone
import com.aiyu.findex.ui.util.contentUriFor
import com.aiyu.findex.ui.util.formatDate
import com.aiyu.findex.ui.util.formatSize
import com.aiyu.findex.ui.util.openFile
import com.aiyu.findex.ui.util.openFileWithChooser
import com.aiyu.findex.ui.util.shareFiles
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

// region Image Preview & Built-in Media Viewer (Images + Videos + Bottom Filmstrip)

@Composable
fun ImagePreviewCard(
    item: StorageItem,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
    galleryItems: List<StorageItem> = emptyList(),
    onDelete: ((StorageItem) -> Unit)? = null
) {
    val isRemote = remember(item.path) { SmbPath.parse(item.path) != null }
    val file = remember(item.path) { File(item.path) }
    val model = remember(item.path, item.lastModified) {
        if (isRemote) Thumb(item.path, item.lastModified, isVideo = false) else file
    }
    val meta by produceState<ImageMeta?>(null, item.path) {
        if (!isRemote) value = FileInspector.inspectImage(file)
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
    ) {
        AsyncImage(
            model = model,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 220.dp)
                .clickable(onClick = onOpenExternal)
                .padding(Dimens.xs)
        )

        // Overlay top-end: Fullscreen action button
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Dimens.sm)
        ) {
            IconButton(
                onClick = onOpenExternal,
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
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
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
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
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

@Composable
fun ImageLightboxDialog(
    item: StorageItem,
    meta: ImageMeta?,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit
) {
    MediaViewerDialog(
        items = listOf(item),
        initialPath = item.path,
        onDismiss = onDismiss
    )
}

/**
 * Full-screen Material 3 Expressive media gallery viewer supporting both images and videos:
 * - Predictive back gesture (`PredictiveBackHandler`) that smoothly scales, shifts, and rounds the viewer over the underlying screen
 * - Horizontal swipe paging across all images and videos in the current folder/list
 * - Pinch-to-zoom, double-tap zoom, pan, and 90° rotation for images (without blocking pager swipe when unzoomed)
 * - Inline video playback (local & SMB stream) or one-tap external video player launch
 * - Bottom filmstrip bar showing only small thumbnail previews (no filenames), with active highlight and tap-to-jump
 * - Top & bottom options including "Open in another app", Share, Rotate, Details, and Delete
 */
@Composable
fun MediaViewerDialog(
    items: List<StorageItem>,
    initialPath: String,
    onDismiss: () -> Unit,
    onDelete: ((StorageItem) -> Unit)? = null
) {
    val mediaItems = remember(items, initialPath) {
        val filtered = items.filter { !it.isDirectory && isMediaExtension(it.extension) }
        if (filtered.isNotEmpty()) filtered else items.filter { !it.isDirectory }
    }
    if (mediaItems.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val initialIndex = remember(mediaItems, initialPath) {
        mediaItems.indexOfFirst { it.path == initialPath }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialIndex) { mediaItems.size }
    val currentIndex = pagerState.currentPage.coerceIn(0, mediaItems.lastIndex)
    val currentItem = mediaItems[currentIndex]
    val isVideo = isVideoExtension(currentItem.extension)
    val isRemote = remember(currentItem.path) { SmbPath.parse(currentItem.path) != null }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showChrome by remember { mutableStateOf(true) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var overflowMenuOpen by remember { mutableStateOf(false) }

    // Per-page rotation & zoom reset trigger
    var pageRotation by remember(currentItem.path) { mutableFloatStateOf(0f) }
    var resetZoomToken by remember(currentItem.path) { mutableIntStateOf(0) }
    var isCurrentZoomed by remember(currentItem.path) { mutableStateOf(false) }

    // Predictive back gesture state: first resets zoom if zoomed in, otherwise shrinks & dismisses the viewer
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backFromLeft by remember { mutableStateOf(true) }
    PredictiveBackHandler(enabled = !showDetailsSheet) { events ->
        try {
            events.collect { event ->
                backProgress = event.progress
                backFromLeft = event.swipeEdge == BackEventCompat.EDGE_LEFT
            }
            if (isCurrentZoomed || pageRotation != 0f) {
                pageRotation = 0f
                resetZoomToken++
            } else {
                onDismiss()
            }
        } finally {
            backProgress = 0f
        }
    }
    val backScale by animateFloatAsState(
        targetValue = 1f - 0.12f * backProgress,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "viewerBackScale"
    )

    val imageMeta by produceState<ImageMeta?>(null, currentItem.path) {
        if (!isRemote && !isVideo) value = FileInspector.inspectImage(File(currentItem.path))
    }

    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape((28f * backProgress).dp),
        tonalElevation = 2.dp,
        shadowElevation = (16f * backProgress).dp,
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .graphicsLayer {
                scaleX = backScale
                scaleY = backScale
                translationX = (if (backFromLeft) 1 else -1) * backProgress * 40.dp.toPx()
                alpha = 1f - 0.2f * backProgress
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Main Pager: swipe through images and videos
            HorizontalPager(
                state = pagerState,
                key = { page -> mediaItems[page].path },
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageItem = mediaItems[page]
                val pageIsVideo = isVideoExtension(pageItem.extension)
                val isCurrentPage = page == pagerState.currentPage
                if (pageIsVideo) {
                    MediaVideoSlide(
                        item = pageItem,
                        isCurrentPage = isCurrentPage,
                        onToggleChrome = { showChrome = !showChrome },
                        onOpenExternal = { context.openFile(pageItem.path, pageItem.size) },
                        onOpenInAnotherApp = { context.openFileWithChooser(pageItem.path, pageItem.size) }
                    )
                } else {
                    MediaImageSlide(
                        item = pageItem,
                        rotationDegrees = if (isCurrentPage) pageRotation else 0f,
                        resetToken = if (isCurrentPage) resetZoomToken else 0,
                        onZoomChanged = { zoomed -> if (isCurrentPage) isCurrentZoomed = zoomed },
                        onToggleChrome = { showChrome = !showChrome }
                    )
                }
            }

            // Top Chrome Bar
            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(effectsSpec) + slideInVertically(spatialSpec) { -it / 2 },
                exit = fadeOut(effectsSpec) + slideOutVertically(spatialSpec) { -it / 2 },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = Dimens.sm, vertical = Dimens.xs)
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = Dimens.xs)
                        ) {
                            Text(
                                text = currentItem.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val posText = stringResource(R.string.viewer_item_position, currentIndex + 1, mediaItems.size)
                            val dimText = imageMeta?.let { " · ${it.width} × ${it.height}" }.orEmpty()
                            Text(
                                text = "$posText$dimText · ${formatSize(currentItem.size)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!isVideo && (isCurrentZoomed || pageRotation != 0f)) {
                            IconButton(
                                onClick = {
                                    pageRotation = 0f
                                    resetZoomToken++
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.action_reset_zoom),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (!isVideo) {
                            IconButton(onClick = { pageRotation = (pageRotation + 90f) % 360f }) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.RotateRight,
                                    contentDescription = stringResource(R.string.action_rotate),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        IconButton(
                            onClick = { context.openFileWithChooser(currentItem.path, currentItem.size) }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = stringResource(R.string.action_open_in_another_app),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box {
                            IconButton(onClick = { overflowMenuOpen = true }) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.cd_more_options),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DropdownMenu(
                                expanded = overflowMenuOpen,
                                onDismissRequest = { overflowMenuOpen = false },
                                shape = MaterialTheme.shapes.large
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_open_in_another_app)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                                    onClick = {
                                        overflowMenuOpen = false
                                        context.openFileWithChooser(currentItem.path, currentItem.size)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_share)) },
                                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                    onClick = {
                                        overflowMenuOpen = false
                                        context.shareFiles(listOf(currentItem.path))
                                    }
                                )
                                if (!isVideo) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_rotate)) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = null) },
                                        onClick = {
                                            overflowMenuOpen = false
                                            pageRotation = (pageRotation + 90f) % 360f
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_details)) },
                                    leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                                    onClick = {
                                        overflowMenuOpen = false
                                        showDetailsSheet = true
                                    }
                                )
                                if (onDelete != null) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.action_delete),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            overflowMenuOpen = false
                                            onDelete(currentItem)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Action Pill + Thumbnail Filmstrip (image-only thumbnails, no names)
            AnimatedVisibility(
                visible = showChrome,
                enter = fadeIn(effectsSpec) + slideInVertically(spatialSpec) { it / 2 },
                exit = fadeOut(effectsSpec) + slideOutVertically(spatialSpec) { it / 2 },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(top = Dimens.sm, bottom = Dimens.md)
                ) {
                    // Quick Action Pill Bar — opaque M3 surfaceContainerHigh with explicit contentColor & tonal icon buttons
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 4.dp,
                        shadowElevation = 6.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(horizontal = Dimens.lg)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
                            modifier = Modifier.padding(horizontal = Dimens.sm, vertical = Dimens.xs)
                        ) {
                            FilledTonalButton(
                                onClick = { context.openFileWithChooser(currentItem.path, currentItem.size) },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = Dimens.md, vertical = Dimens.xs)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(Dimens.xs))
                                Text(
                                    text = stringResource(R.string.action_open_in_another_app),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }

                            FilledTonalIconButton(
                                onClick = { context.shareFiles(listOf(currentItem.path)) },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(
                                    Icons.Rounded.Share,
                                    contentDescription = stringResource(R.string.action_share)
                                )
                            }

                            FilledTonalIconButton(
                                onClick = { showDetailsSheet = true },
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(
                                    Icons.Rounded.Info,
                                    contentDescription = stringResource(R.string.action_details)
                                )
                            }

                            if (onDelete != null) {
                                FilledTonalIconButton(
                                    onClick = { onDelete(currentItem) },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = stringResource(R.string.action_delete)
                                    )
                                }
                            }
                        }
                    }

                    if (mediaItems.size > 1) {
                        Spacer(Modifier.height(Dimens.sm))
                        MediaFilmstripBar(
                            items = mediaItems,
                            selectedIndex = currentIndex,
                            onSelect = { index ->
                                scope.launch { pagerState.animateScrollToPage(index) }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDetailsSheet) {
        MediaViewerDetailsSheet(
            item = currentItem,
            imageMeta = imageMeta,
            onDismiss = { showDetailsSheet = false }
        )
    }
}

/**
 * Bottom horizontal filmstrip showing small thumbnail previews of all images and videos in the current folder/list.
 * Renders ONLY the thumbnail image (no filenames), with expressive size/border transition on the active item.
 */
@Composable
private fun MediaFilmstripBar(
    items: List<StorageItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val fastSpatialDp = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.Dp>()
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    LaunchedEffect(selectedIndex, items.size) {
        if (selectedIndex in items.indices) {
            val target = (selectedIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.md)
    ) {
        LazyRow(
            state = listState,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.xs),
            contentPadding = PaddingValues(horizontal = Dimens.md, vertical = Dimens.sm),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(items, key = { _, item -> item.path }) { index, item ->
                val selected = index == selectedIndex
                val isVid = isVideoExtension(item.extension)
                val thumbWidth by animateDpAsState(
                    targetValue = if (selected) 68.dp else 48.dp,
                    animationSpec = fastSpatialDp,
                    label = "stripWidth"
                )
                val thumbHeight by animateDpAsState(
                    targetValue = if (selected) 56.dp else 48.dp,
                    animationSpec = fastSpatialDp,
                    label = "stripHeight"
                )
                val itemAlpha by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.6f,
                    animationSpec = fastEffects,
                    label = "stripAlpha"
                )
                val shape = if (selected) MaterialTheme.shapes.medium else MaterialTheme.shapes.small

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = thumbWidth, height = thumbHeight)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .then(
                            if (selected) {
                                Modifier.border(
                                    width = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = shape
                                )
                            } else Modifier
                        )
                        .alpha(itemAlpha)
                        .clickable { onSelect(index) }
                ) {
                    AsyncImage(
                        model = remember(item.path, item.lastModified, isVid) {
                            Thumb(item.path, item.lastModified, isVideo = isVid)
                        },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isVid) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(Dimens.xxs)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Interactive zoomable, rotatable image page.
 * Single-finger horizontal drags pass through to [HorizontalPager] when unzoomed (`scale <= 1.01f`),
 * preventing gesture conflicts while preserving smooth pinch-to-zoom and pan.
 */
@Composable
private fun MediaImageSlide(
    item: StorageItem,
    rotationDegrees: Float,
    resetToken: Int,
    onZoomChanged: (Boolean) -> Unit,
    onToggleChrome: () -> Unit
) {
    val context = LocalContext.current
    val isRemote = remember(item.path) { SmbPath.parse(item.path) != null }
    val thumbModel = remember(item.path, item.lastModified) {
        Thumb(item.path, item.lastModified, isVideo = false)
    }
    val fullModel = remember(item.path, item.size, isRemote) {
        if (isRemote) context.contentUriFor(item.path, item.size) else File(item.path)
    }

    var scale by remember(item.path, resetToken) { mutableFloatStateOf(1f) }
    var offset by remember(item.path, resetToken) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1.02f)
    }

    val animatedRotation by animateFloatAsState(
        targetValue = rotationDegrees,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "imageRotation"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(item.path, resetToken) {
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onDoubleTap = {
                        if (scale > 1.15f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(item.path, resetToken) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val pointerCount = event.changes.count { it.pressed }

                        if (pointerCount > 1 || scale > 1.01f) {
                            val newScale = (scale * zoomChange).coerceIn(1f, 6f)
                            val maxX = ((containerSize.width * (newScale - 1f)) / 2f).coerceAtLeast(0f)
                            val maxY = ((containerSize.height * (newScale - 1f)) / 2f).coerceAtLeast(0f)
                            val candidateX = offset.x + panChange.x
                            val candidateY = offset.y + panChange.y

                            val atHorizontalEdge = pointerCount == 1 &&
                                newScale > 1.01f &&
                                (candidateX > maxX + 8f || candidateX < -maxX - 8f)

                            scale = newScale
                            offset = if (newScale > 1.01f) {
                                Offset(
                                    x = candidateX.coerceIn(-maxX, maxX),
                                    y = candidateY.coerceIn(-maxY, maxY)
                                )
                            } else {
                                Offset.Zero
                            }

                            if (!atHorizontalEdge) {
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val layerModifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
                rotationZ = animatedRotation
            }
        // Instant cached thumbnail layer underneath so remote SMB images never show a blank screen while streaming full-res
        AsyncImage(
            model = thumbModel,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = layerModifier
        )
        AsyncImage(
            model = fullModel,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = layerModifier
        )
    }
}

/**
 * Video slide inside the built-in Media Viewer: shows the video frame thumbnail with a prominent
 * inline Play button (streaming over SMB or playing locally) plus a one-tap option to open in an external video app.
 */
@Composable
private fun MediaVideoSlide(
    item: StorageItem,
    isCurrentPage: Boolean,
    onToggleChrome: () -> Unit,
    onOpenExternal: () -> Unit,
    onOpenInAnotherApp: () -> Unit
) {
    val context = LocalContext.current
    var playingInline by remember(item.path) { mutableStateOf(false) }
    var isPaused by remember(item.path) { mutableStateOf(false) }
    var videoViewRef by remember(item.path) { mutableStateOf<VideoView?>(null) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage && playingInline) {
            runCatching { videoViewRef?.stopPlayback() }
            playingInline = false
            isPaused = false
        }
    }

    DisposableEffect(item.path) {
        onDispose {
            runCatching { videoViewRef?.stopPlayback() }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onToggleChrome
            )
    ) {
        if (playingInline) {
            val uri = remember(item.path, item.size) { context.contentUriFor(item.path, item.size) }
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        videoViewRef = this
                        setVideoURI(uri)
                        setOnPreparedListener { mp ->
                            mp.isLooping = false
                            start()
                            isPaused = false
                        }
                        setOnCompletionListener {
                            playingInline = false
                            isPaused = false
                        }
                        setOnErrorListener { _, _, _ ->
                            playingInline = false
                            onOpenExternal()
                            true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Floating Play/Pause toggle in top-start or bottom-end of video when playing
            FilledTonalIconButton(
                onClick = {
                    val vv = videoViewRef ?: return@FilledTonalIconButton
                    if (vv.isPlaying) {
                        vv.pause()
                        isPaused = true
                    } else {
                        vv.start()
                        isPaused = false
                    }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .alpha(if (isPaused) 0.95f else 0f)
            ) {
                Icon(
                    if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = stringResource(if (isPaused) R.string.action_play else R.string.action_pause),
                    modifier = Modifier.size(32.dp)
                )
            }
        } else {
            AsyncImage(
                model = remember(item.path, item.lastModified) {
                    Thumb(item.path, item.lastModified, isVideo = true)
                },
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                FilledIconButton(
                    onClick = { playingInline = true },
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.preview_play_video),
                        modifier = Modifier.size(40.dp)
                    )
                }

                FilledTonalButton(onClick = onOpenExternal) {
                    Icon(
                        Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(Dimens.xs))
                    Text(stringResource(R.string.action_open))
                }
            }
        }
    }
}

@Composable
private fun MediaViewerDetailsSheet(
    item: StorageItem,
    imageMeta: ImageMeta?,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = Dimens.xl)
                .padding(bottom = Dimens.xl)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileThumbnail(
                    path = item.path,
                    isDirectory = false,
                    extension = item.extension,
                    size = 56.dp,
                    lastModified = item.lastModified
                )
                Spacer(Modifier.width(Dimens.lg))
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(Dimens.xl))
            DetailLine(R.string.details_path, item.path)
            DetailLine(R.string.details_size, formatSize(item.size))
            DetailLine(R.string.details_modified, formatDate(item.lastModified))
            DetailLine(R.string.details_type, item.mimeType ?: item.extension.uppercase())
            imageMeta?.let { meta ->
                DetailLine(
                    R.string.details_dimensions,
                    "${meta.width} × ${meta.height} (${String.format(Locale.ROOT, "%.1f MP", meta.megapixels)})"
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
    val isRemote = remember(item.path) { SmbPath.parse(item.path) != null }
    val file = remember(item.path) { File(item.path) }
    val model = remember(item.path, item.lastModified) {
        if (isRemote) Thumb(item.path, item.lastModified, isVideo = true) else file
    }
    val meta by produceState<VideoMeta?>(null, item.path) {
        if (!isRemote) value = FileInspector.inspectVideo(file)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
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
            model = model,
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
                com.aiyu.findex.ui.components.FileThumbnail(
                    path = item.path,
                    isDirectory = false,
                    extension = item.extension,
                    size = 56.dp,
                    lastModified = item.lastModified
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
                    .heightIn(min = 96.dp, max = 180.dp)
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
            .padding(Dimens.lg)
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
                size = 56.dp,
                iconSize = 28.dp
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
        }
    }
}

// endregion

// region APK Hero Preview

@Composable
fun ApkPreviewCard(
    item: StorageItem,
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
            .padding(Dimens.lg)
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
                        .size(56.dp)
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
        }
    }
}

// endregion

// region Folder Hero Preview

@Composable
fun FolderPreviewCard(
    item: StorageItem,
    details: FileDetails?,
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
            .padding(Dimens.lg)
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
                size = 56.dp,
                iconSize = 28.dp
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
            .height(120.dp)
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
            com.aiyu.findex.ui.components.FileThumbnail(
                path = item.path,
                isDirectory = item.isDirectory,
                extension = item.extension,
                size = 64.dp,
                lastModified = item.lastModified
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

internal fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(Locale.ROOT, minutes, seconds)
}
