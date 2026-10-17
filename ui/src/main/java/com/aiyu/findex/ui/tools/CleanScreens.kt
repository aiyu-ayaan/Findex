package com.aiyu.findex.ui.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Gif
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.core.scanner.JunkType
import com.aiyu.findex.core.scanner.ScanGroup
import com.aiyu.findex.core.scanner.WhatsAppMediaType
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.components.MorphingShapeIcon
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.components.groupedShape
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.util.formatSize

/** How one scan group is presented. */
data class GroupPresentation(val titleRes: Int, val descRes: Int?, val icon: ImageVector, val shape: RoundedPolygon)

@Composable
fun CleanerScreen(onBack: () -> Unit, viewModel: CleanerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CleanScaffold(
        title = stringResource(R.string.cleaner_title),
        hint = stringResource(R.string.cleaner_desc),
        emptyText = stringResource(R.string.tool_all_clean_body),
        state = state,
        present = { type ->
            when (type) {
                JunkType.THUMBNAILS -> GroupPresentation(R.string.junk_thumbnails, R.string.junk_thumbnails_desc, Icons.Rounded.PhotoLibrary, MaterialShapes.Cookie9Sided)
                JunkType.TEMP_FILES -> GroupPresentation(R.string.junk_temp, R.string.junk_temp_desc, Icons.Rounded.Description, MaterialShapes.Cookie6Sided)
                JunkType.APK_FILES -> GroupPresentation(R.string.junk_apks, R.string.junk_apks_desc, Icons.Rounded.Android, MaterialShapes.SoftBurst)
                JunkType.EMPTY_FOLDERS -> GroupPresentation(R.string.junk_empty, R.string.junk_empty_desc, Icons.Rounded.FolderOff, MaterialShapes.Cookie4Sided)
            }
        },
        onToggle = viewModel::toggle,
        onClean = viewModel::clean,
        onRescan = viewModel::rescan,
        onBack = onBack
    )
}

@Composable
fun WhatsAppScreen(onBack: () -> Unit, viewModel: WhatsAppViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CleanScaffold(
        title = stringResource(R.string.whatsapp_cleaner_title),
        hint = stringResource(R.string.wa_hint),
        emptyText = stringResource(R.string.wa_not_found),
        state = state,
        present = { type ->
            when (type) {
                WhatsAppMediaType.IMAGES -> GroupPresentation(R.string.wa_images, null, Icons.Rounded.Image, MaterialShapes.Cookie9Sided)
                WhatsAppMediaType.VIDEOS -> GroupPresentation(R.string.wa_videos, null, Icons.Rounded.Movie, MaterialShapes.Cookie7Sided)
                WhatsAppMediaType.AUDIO -> GroupPresentation(R.string.wa_audio, null, Icons.Rounded.MusicNote, MaterialShapes.Sunny)
                WhatsAppMediaType.VOICE_NOTES -> GroupPresentation(R.string.wa_voice, null, Icons.Rounded.Mic, MaterialShapes.Cookie4Sided)
                WhatsAppMediaType.DOCUMENTS -> GroupPresentation(R.string.wa_documents, null, Icons.Rounded.Description, MaterialShapes.Cookie6Sided)
                WhatsAppMediaType.STICKERS -> GroupPresentation(R.string.wa_stickers, null, Icons.Rounded.EmojiEmotions, MaterialShapes.Clover4Leaf)
                WhatsAppMediaType.GIFS -> GroupPresentation(R.string.wa_gifs, null, Icons.Rounded.Gif, MaterialShapes.SoftBurst)
                WhatsAppMediaType.BACKUPS -> GroupPresentation(R.string.wa_backups, null, Icons.Rounded.Backup, MaterialShapes.Pentagon)
            }
        },
        onToggle = viewModel::toggle,
        onClean = viewModel::clean,
        onRescan = viewModel::rescan,
        onBack = onBack
    )
}

@Composable
private fun <T> CleanScaffold(
    title: String,
    hint: String,
    emptyText: String,
    state: CleanUiState<T>,
    present: (T) -> GroupPresentation,
    onToggle: (T) -> Unit,
    onClean: () -> Unit,
    onRescan: () -> Unit,
    onBack: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirm by remember { mutableStateOf(false) }
    val selectedSize = formatSize(state.selectedBytes)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(title) },
                subtitle = { Text(hint) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } },
                actions = {
                    IconButton(onClick = onRescan, enabled = !state.isScanning) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.tool_rescan))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = !state.isScanning && state.selected.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { confirm = true },
                    icon = { Icon(Icons.Rounded.CleaningServices, null) },
                    text = { Text(stringResource(R.string.tool_clean_button, selectedSize)) }
                )
            }
        }
    ) { padding ->
        if (state.isScanning) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                LoadingIndicator(Modifier.height(96.dp).width(96.dp))
                Spacer(Modifier.height(Dimens.lg))
                Text(stringResource(R.string.scanning), style = MaterialTheme.typography.titleMedium)
            }
            return@Scaffold
        }
        if (state.groups.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(padding).padding(Dimens.xxl)
            ) {
                ShapeIcon(
                    icon = Icons.Rounded.TaskAlt,
                    shape = MaterialShapes.Cookie12Sided,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 120.dp,
                    iconSize = 52.dp
                )
                Spacer(Modifier.height(Dimens.xl))
                Text(stringResource(R.string.tool_all_clean_title), style = MaterialTheme.typography.headlineSmallEmphasized)
                Text(emptyText, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = readableGutter(), end = readableGutter(),
                top = padding.calculateTopPadding() + Dimens.sm,
                bottom = padding.calculateBottomPadding() + 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.xxs)
        ) {
            item(key = "summary") {
                Card(
                    shape = MaterialTheme.shapes.extraLargeIncreased,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.md)
                ) {
                    Column(Modifier.padding(Dimens.xl)) {
                        Text(selectedSize, style = MaterialTheme.typography.displayMediumEmphasized)
                        Text(stringResource(R.string.tool_can_free), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Dimens.sm))
                        Text(
                            stringResource(R.string.tool_selected_of, selectedSize, formatSize(state.groups.sumOf { it.bytes })),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            itemsIndexed(state.groups, key = { _, g -> g.type.toString() }) { index, group ->
                GroupRow(
                    group = group,
                    presentation = present(group.type),
                    checked = group.type in state.selected,
                    shape = groupedShape(index, state.groups.size),
                    onToggle = { onToggle(group.type) }
                )
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            icon = { Icon(Icons.Rounded.CleaningServices, null) },
            title = { Text(stringResource(R.string.tool_clean_confirm_title, selectedSize)) },
            text = { Text(stringResource(R.string.tool_clean_confirm_body)) },
            confirmButton = { TextButton(onClick = { confirm = false; onClean() }) { Text(stringResource(R.string.action_clean)) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun <T> GroupRow(
    group: ScanGroup<T>,
    presentation: GroupPresentation,
    checked: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    onToggle: () -> Unit
) {
    val container by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "groupColor"
    )
    Surface(
        shape = shape,
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Dimens.lg, end = Dimens.sm, top = Dimens.md, bottom = Dimens.md)) {
            MorphingShapeIcon(
                icon = presentation.icon,
                from = presentation.shape,
                to = MaterialShapes.Circle,
                morphed = checked,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(Dimens.lg))
            Column(Modifier.weight(1f)) {
                Text(stringResource(presentation.titleRes), style = MaterialTheme.typography.titleMedium)
                presentation.descRes?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    pluralStringResource(R.plurals.item_count, group.files.size, group.files.size) + " · " + formatSize(group.bytes),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(Dimens.md))
        }
    }
}

