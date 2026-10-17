package com.aiyu.findex.ui.explorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import com.aiyu.findex.core.model.SortBy
import com.aiyu.findex.core.model.SortSpec
import com.aiyu.findex.core.model.ArchiveFormat
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.FileThumbnail
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.util.formatDate
import com.aiyu.findex.ui.util.formatSize

enum class ItemAction { OPEN, OPEN_IN_NEW_TAB, SHARE, SHOW_IN_FOLDER, RESTORE, COPY, CUT, RENAME, COMPRESS, EXTRACT, BOOKMARK, DETAILS, DELETE }

val ExplorerItemActions = ItemAction.entries.toSet() - ItemAction.SHOW_IN_FOLDER - ItemAction.RESTORE

/** Long-press-free per-item actions, opened from the row's overflow button. */
@Composable
fun ItemActionSheet(
    item: StorageItem,
    onAction: (ItemAction) -> Unit,
    onDismiss: () -> Unit,
    allowed: Set<ItemAction> = ExplorerItemActions
) {
    @Composable
    fun row(action: ItemAction, icon: ImageVector, labelRes: Int, tint: Color = MaterialTheme.colorScheme.onSurface) {
        if (action in allowed) ActionRow(icon, labelRes, tint) { onAction(action) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Dimens.lg)) {
            ListItem(
                headlineContent = { Text(item.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
                supportingContent = { Text(if (item.isDirectory) formatDate(item.lastModified) else formatSize(item.size) + " · " + formatDate(item.lastModified)) },
                leadingContent = { FileThumbnail(item.path, item.isDirectory, item.extension, size = 52.dp) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            HorizontalDivider(Modifier.padding(horizontal = Dimens.xl, vertical = Dimens.sm))
            if (!item.isDirectory) row(ItemAction.OPEN, Icons.AutoMirrored.Rounded.OpenInNew, R.string.action_open)
            if (item.isDirectory) row(ItemAction.OPEN_IN_NEW_TAB, Icons.Rounded.Tab, R.string.action_open_in_new_tab)
            if (!item.isDirectory) row(ItemAction.SHARE, Icons.Rounded.Share, R.string.action_share)
            row(ItemAction.SHOW_IN_FOLDER, Icons.Rounded.FolderOpen, R.string.action_show_in_folder)
            row(ItemAction.RESTORE, Icons.Rounded.RestoreFromTrash, R.string.action_restore)
            row(ItemAction.COPY, Icons.Rounded.ContentCopy, R.string.action_copy)
            row(ItemAction.CUT, Icons.Rounded.ContentCut, R.string.action_cut)
            row(ItemAction.RENAME, Icons.Rounded.DriveFileRenameOutline, R.string.action_rename)
            if (item.isArchive) row(ItemAction.EXTRACT, Icons.Rounded.Unarchive, R.string.action_extract)
            else row(ItemAction.COMPRESS, Icons.Rounded.FolderZip, R.string.action_compress)
            if (item.isDirectory) row(ItemAction.BOOKMARK, Icons.Rounded.BookmarkBorder, R.string.action_bookmark)
            row(ItemAction.DETAILS, Icons.Rounded.Info, R.string.action_details)
            HorizontalDivider(Modifier.padding(horizontal = Dimens.xl, vertical = Dimens.sm))
            // Destructive action separated and in the error color.
            row(ItemAction.DELETE, Icons.Rounded.Delete, R.string.action_delete, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, labelRes: Int, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(labelRes), color = tint) },
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .padding(horizontal = Dimens.sm)
            .clip(MaterialTheme.shapes.large)
            .selectable(selected = false, role = Role.Button, onClick = onClick)
    )
}

/** Sort field + direction, and view options. */
@Composable
fun SortSheet(
    sort: SortSpec,
    showHidden: Boolean,
    onSort: (SortSpec) -> Unit,
    onShowHidden: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = Dimens.xl).padding(bottom = Dimens.xl)) {
            Text(stringResource(R.string.sort_title), style = MaterialTheme.typography.titleLargeEmphasized)
            Spacer(Modifier.height(Dimens.md))
            val labels = mapOf(
                SortBy.NAME to R.string.sort_name,
                SortBy.DATE to R.string.sort_date,
                SortBy.SIZE to R.string.sort_size,
                SortBy.TYPE to R.string.sort_type
            )
            SortBy.entries.forEach { by ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .selectable(selected = sort.by == by, role = Role.RadioButton) { onSort(sort.copy(by = by)) }
                        .padding(vertical = Dimens.xs)
                ) {
                    RadioButton(selected = sort.by == by, onClick = null, modifier = Modifier.padding(Dimens.md))
                    Text(stringResource(labels.getValue(by)), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(Dimens.md))
            // Connected button group for direction.
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), modifier = Modifier.fillMaxWidth()) {
                ToggleButton(
                    checked = sort.ascending,
                    onCheckedChange = { onSort(sort.copy(ascending = true)) },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.sort_ascending)) }
                ToggleButton(
                    checked = !sort.ascending,
                    onCheckedChange = { onSort(sort.copy(ascending = false)) },
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.sort_descending)) }
            }
            Spacer(Modifier.height(Dimens.lg))
            ToggleButton(
                checked = showHidden,
                onCheckedChange = onShowHidden,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(if (showHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
                Spacer(Modifier.width(Dimens.sm))
                Text(stringResource(R.string.show_hidden_files))
            }
        }
    }
}

@Composable
fun DetailsSheet(item: StorageItem, load: suspend (StorageItem) -> FileDetails, onDismiss: () -> Unit) {
    val details by produceState<FileDetails?>(null, item) { value = load(item) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = Dimens.xl).padding(bottom = Dimens.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileThumbnail(item.path, item.isDirectory, item.extension, size = 56.dp)
                Spacer(Modifier.width(Dimens.lg))
                Text(item.name, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(Dimens.xl))
            DetailLine(R.string.details_path, item.path)
            DetailLine(R.string.details_modified, formatDate(item.lastModified))
            if (!item.isDirectory) DetailLine(R.string.details_type, item.mimeType ?: item.extension.uppercase())
            val d = details
            if (d == null) {
                LoadingIndicator(Modifier.align(Alignment.CenterHorizontally))
            } else {
                DetailLine(R.string.details_size, formatSize(d.totalBytes))
                if (item.isDirectory) {
                    DetailLine(
                        R.string.details_contains,
                        pluralStringResource(R.plurals.file_count, d.fileCount, d.fileCount) + ", " +
                            pluralStringResource(R.plurals.folder_count, d.folderCount, d.folderCount)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailLine(labelRes: Int, value: String) {
    Column(Modifier.padding(vertical = Dimens.sm)) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Single text field dialog for rename / new folder. Pre-selects the name without its extension. */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember {
        val end = initial.lastIndexOf('.').takeIf { it > 0 } ?: initial.length
        mutableStateOf(TextFieldValue(initial, TextRange(0, end)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val valid = value.text.isNotBlank() && '/' !in value.text
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                isError = value.text.isNotEmpty() && !valid,
                supportingText = if (value.text.isNotEmpty() && !valid) {
                    { Text(stringResource(R.string.error_invalid_name)) }
                } else null,
                label = { Text(stringResource(R.string.label_name)) },
                modifier = Modifier.focusRequester(focus)
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value.text) }, enabled = valid) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** ZIP name + optional AES password. */
@Composable
fun CompressDialog(
    defaultName: String,
    onConfirm: (name: String, password: String?, format: ArchiveFormat) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(defaultName) }
    var format by rememberSaveable { mutableStateOf(ArchiveFormat.ZIP) }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compress_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.label_archive_name)) },
                    suffix = { Text(".${format.extension}") }
                )
                Text(stringResource(R.string.compress_format_label), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween), modifier = Modifier.fillMaxWidth()) {
                    val formats = ArchiveFormat.Writable
                    formats.forEachIndexed { index, option ->
                        ToggleButton(
                            checked = format == option,
                            onCheckedChange = { format = option },
                            shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                formats.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            contentPadding = PaddingValues(horizontal = Dimens.xs),
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(option.labelRes), maxLines = 1) }
                    }
                }
                if (format.passwordOnWrite) {
                    PasswordField(password, { password = it }, showPassword, { showPassword = it }, R.string.compress_password_hint)
                }
                Text(
                    stringResource(if (format.passwordOnWrite) R.string.compress_password_help else R.string.compress_password_zip_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, password.takeIf { format.passwordOnWrite && it.isNotEmpty() }, format) }, enabled = name.isNotBlank() && '/' !in name) {
                Text(stringResource(R.string.compress_button))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun ExtractDialog(
    archiveName: String,
    onConfirm: (password: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.extract_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                Text(stringResource(R.string.extract_dialog_body, archiveName), style = MaterialTheme.typography.bodyMedium)
                if (ArchiveFormat.of(archiveName)?.passwordOnRead != false) {
                    PasswordField(password, { password = it }, showPassword, { showPassword = it }, R.string.extract_password_hint)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(password.ifEmpty { null }) }) { Text(stringResource(R.string.extract_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    labelRes: Int
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(stringResource(labelRes)) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { onVisibleChange(!visible) }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = stringResource(if (visible) R.string.cd_hide_password else R.string.cd_show_password)
                )
            }
        }
    )
}

@Composable
/** Defaults to moving into the Recycle bin; [permanentOnly] skips the bin (e.g. deleting from the bin itself). */
fun DeleteDialog(count: Int, onConfirm: (permanent: Boolean) -> Unit, onDismiss: () -> Unit, permanentOnly: Boolean = false) {
    var permanent by rememberSaveable { mutableStateOf(permanentOnly) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(pluralStringResource(if (permanent) R.plurals.delete_title else R.plurals.trash_title, count, count)) },
        text = {
            Column {
                Text(stringResource(if (permanent) R.string.delete_body else R.string.trash_body))
                if (!permanentOnly) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = Dimens.md)
                            .clip(MaterialTheme.shapes.small)
                            .toggleable(value = permanent, role = Role.Checkbox, onValueChange = { permanent = it })
                    ) {
                        Checkbox(checked = permanent, onCheckedChange = null)
                        Spacer(Modifier.width(Dimens.sm))
                        Text(stringResource(R.string.delete_permanently))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(permanent) }) {
                if (permanent) Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                else Text(stringResource(R.string.action_move_to_bin))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

private val ArchiveFormat.labelRes: Int
    get() = when (this) {
        ArchiveFormat.ZIP -> R.string.compress_format_zip
        ArchiveFormat.SEVEN_Z -> R.string.compress_format_7z
        ArchiveFormat.TAR -> R.string.compress_format_tar
        else -> R.string.compress_format_tar_gz
    }
