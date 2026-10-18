package com.aiyu.findex.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aiyu.findex.core.repository.Tag
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.theme.TAG_TONE_COUNT
import com.aiyu.findex.ui.theme.TagTones

private val colorNames = intArrayOf(
    R.string.tags_color_red, R.string.tags_color_orange, R.string.tags_color_yellow, R.string.tags_color_green,
    R.string.tags_color_teal, R.string.tags_color_blue, R.string.tags_color_purple, R.string.tags_color_pink
)

/** Up to [max] small dots after a file name. */
@Composable
fun TagDots(tags: List<Tag>, modifier: Modifier = Modifier, max: Int = 3) {
    if (tags.isEmpty()) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
        tags.take(max).forEach { Row(Modifier.size(8.dp).clip(CircleShape).background(TagTones[it.colorIndex].accent)) {} }
    }
}

/** Check state is for the whole selection: [initial] holds the tags every selected item has. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagPickerSheet(
    tags: List<Tag>,
    initial: Set<Long>,
    onApply: (Set<Long>) -> Unit,
    onCreate: (name: String, colorIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var checked by rememberSaveable { mutableStateOf(initial.toList()) }
    var creating by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = Dimens.xl).padding(bottom = Dimens.xl)) {
            Text(stringResource(R.string.tags_title), style = MaterialTheme.typography.titleLarge)
            if (tags.isEmpty()) {
                Text(stringResource(R.string.tags_empty), Modifier.padding(vertical = Dimens.lg), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(Modifier.padding(vertical = Dimens.lg), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                tags.forEach { tag ->
                    val tone = TagTones[tag.colorIndex]
                    val on = tag.id in checked
                    FilterChip(
                        selected = on,
                        onClick = { checked = if (on) checked - tag.id else checked + tag.id },
                        label = { Text(tag.name) },
                        leadingIcon = { if (on) Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) else Row(Modifier.size(10.dp).clip(CircleShape).background(tone.accent)) {} },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = tone.container, selectedLabelColor = tone.onContainer, selectedLeadingIconColor = tone.onContainer
                        )
                    )
                }
                AssistChip(onClick = { creating = true }, label = { Text(stringResource(R.string.tags_new)) }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) })
            }
            Button(onClick = { onApply(checked.toSet()) }, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.tags_apply)) }
        }
    }
    if (creating) TagEditDialog(null, onDismiss = { creating = false }, onConfirm = { n, c -> creating = false; onCreate(n, c) })
}

/** Create ([tag] null) or edit a tag: name plus one of the eight tones. */
@Composable
fun TagEditDialog(tag: Tag?, onDismiss: () -> Unit, onConfirm: (name: String, colorIndex: Int) -> Unit) {
    var name by rememberSaveable { mutableStateOf(tag?.name.orEmpty()) }
    var color by rememberSaveable { mutableStateOf(tag?.colorIndex ?: 0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (tag == null) R.string.tags_new else R.string.tags_rename)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.lg)) {
                OutlinedTextField(name, { name = it.take(24) }, singleLine = true, label = { Text(stringResource(R.string.tags_name_hint)) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.md), verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                    repeat(TAG_TONE_COUNT) { i ->
                        val desc = stringResource(colorNames[i])
                        Row(
                            Modifier.size(Dimens.xxl).clip(CircleShape).background(TagTones[i].accent)
                                .clickable { color = i }.semantics { contentDescription = desc },
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                        ) { if (i == color) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.surface) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, color) }, enabled = name.isNotBlank()) { Text(stringResource(if (tag == null) R.string.action_create else R.string.tags_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
