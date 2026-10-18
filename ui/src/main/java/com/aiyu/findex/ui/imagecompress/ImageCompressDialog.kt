package com.aiyu.findex.ui.imagecompress

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.core.model.ImageOutputFormat
import com.aiyu.findex.core.model.ImageOutputMode
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.util.formatSize

private fun Preset.label() = when (this) {
    Preset.SMALL -> R.string.imgc_preset_small
    Preset.MEDIUM -> R.string.imgc_preset_medium
    Preset.LARGE -> R.string.imgc_preset_large
    Preset.CUSTOM -> R.string.imgc_preset_custom
}

private fun ImageOutputFormat.label() = when (this) {
    ImageOutputFormat.KEEP -> R.string.imgc_format_keep
    ImageOutputFormat.JPEG -> R.string.imgc_format_jpeg
    ImageOutputFormat.WEBP -> R.string.imgc_format_webp
    ImageOutputFormat.PNG -> R.string.imgc_format_png
}

private fun ImageOutputMode.label() = when (this) {
    ImageOutputMode.COPY_WITH_SUFFIX -> R.string.imgc_output_copy
    ImageOutputMode.REPLACE -> R.string.imgc_output_replace
    ImageOutputMode.INTO_FOLDER -> R.string.imgc_output_folder
}

@Composable
fun ImageCompressDialog(onDismiss: () -> Unit, viewModel: ImageCompressViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel.expired) {
        if (viewModel.expired) {
            Toast.makeText(context, R.string.selection_expired, Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.imgc_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Preset.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = s.preset == p,
                            onClick = { viewModel.update { it.copy(preset = p) } },
                            shape = SegmentedButtonDefaults.itemShape(i, Preset.entries.size)
                        ) { Text(stringResource(p.label()), maxLines = 1) }
                    }
                }
                if (s.preset == Preset.CUSTOM) {
                    OutlinedTextField(
                        value = s.customEdge,
                        onValueChange = { v -> viewModel.update { it.copy(customEdge = v.filter(Char::isDigit).take(5)) } },
                        label = { Text(stringResource(R.string.imgc_max_edge)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (s.customEdge.isEmpty()) {
                        Text(stringResource(R.string.imgc_scale_percent) + ": ${s.scalePercent}%", style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = s.scalePercent.toFloat(),
                            onValueChange = { v -> viewModel.update { it.copy(scalePercent = (v.toInt() / 5 * 5).coerceIn(10, 100)) } },
                            valueRange = 10f..100f
                        )
                    }
                }
                Text(stringResource(R.string.imgc_quality) + ": ${s.quality}", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = s.quality.toFloat(),
                    onValueChange = { v -> viewModel.update { it.copy(quality = v.toInt()) } },
                    valueRange = 1f..100f,
                    enabled = s.format != ImageOutputFormat.PNG
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    ImageOutputFormat.entries.forEach { f ->
                        FilterChip(s.format == f, { viewModel.update { it.copy(format = f) } }, { Text(stringResource(f.label())) })
                    }
                }
                ImageOutputMode.entries.forEach { m ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimens.minTouch)
                            .selectable(selected = s.output == m, role = Role.RadioButton, onClick = { viewModel.update { it.copy(output = m) } })
                    ) {
                        RadioButton(selected = s.output == m, onClick = null)
                        Text(stringResource(m.label()), Modifier.padding(start = Dimens.md))
                    }
                }
                if (s.output == ImageOutputMode.REPLACE) {
                    Text(stringResource(R.string.imgc_replace_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.imgc_keep_metadata), Modifier.weight(1f))
                    Switch(s.keepMetadata, { v -> viewModel.update { it.copy(keepMetadata = v) } })
                }
                s.estimateBytes?.let { Text(stringResource(R.string.imgc_estimate, formatSize(it)), style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = { viewModel.start(); onDismiss() }, enabled = !viewModel.expired) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
