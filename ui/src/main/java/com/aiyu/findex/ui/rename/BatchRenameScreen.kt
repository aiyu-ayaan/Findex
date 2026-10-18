package com.aiyu.findex.ui.rename

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.core.operation.CaseMode
import com.aiyu.findex.core.operation.RenamePreview
import com.aiyu.findex.core.operation.RenameRule
import com.aiyu.findex.core.operation.RenameStatus
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.theme.Dimens

private val TOKENS = listOf(
    "{name}" to R.string.rename_token_name,
    "{ext}" to R.string.rename_token_ext,
    "{n:3}" to R.string.rename_token_counter,
    "{date}" to R.string.rename_token_date,
    "{parent}" to R.string.rename_token_parent
)

private val CASES = listOf(
    CaseMode.NONE to R.string.rename_case_none,
    CaseMode.LOWER to R.string.rename_case_lower,
    CaseMode.UPPER to R.string.rename_case_upper,
    CaseMode.TITLE to R.string.rename_case_title
)

private fun RenameStatus.label(): Int? = when (this) {
    RenameStatus.OK -> null
    RenameStatus.UNCHANGED -> R.string.rename_status_unchanged
    RenameStatus.DUPLICATE -> R.string.rename_status_duplicate
    RenameStatus.EXISTS -> R.string.rename_status_exists
    RenameStatus.INVALID -> R.string.rename_status_invalid
    RenameStatus.BAD_REGEX -> R.string.rename_status_bad_regex
}

@Composable
fun BatchRenameScreen(onBack: () -> Unit, viewModel: BatchRenameViewModel = hiltViewModel()) {
    val rule by viewModel.rule.collectAsStateWithLifecycle()
    val previews by viewModel.previews.collectAsStateWithLifecycle()
    val expired by viewModel.expired.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current

    LaunchedEffect(expired) {
        if (expired) {
            Toast.makeText(context, R.string.selection_expired, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.rename_title)) },
                subtitle = {
                    val changed = previews.count { it.status == RenameStatus.OK }
                    Text(pluralStringResource(R.plurals.rename_summary, changed, changed))
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = viewModel.canApply(previews)) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.apply(); onBack() },
                    icon = { Icon(Icons.Rounded.Check, null) },
                    text = { Text(stringResource(R.string.rename_apply)) }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = readableGutter(), end = readableGutter(),
                top = padding.calculateTopPadding() + Dimens.sm,
                bottom = padding.calculateBottomPadding() + 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            item(key = "form") { RuleForm(rule, viewModel) }
            items(previews, key = { it.item.path }) { PreviewRow(it) }
        }
    }
}


@Composable
private fun RuleForm(rule: RenameRule, vm: BatchRenameViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
        OutlinedTextField(
            value = rule.pattern,
            onValueChange = { v -> vm.update { it.copy(pattern = v) } },
            label = { Text(stringResource(R.string.rename_pattern_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            TOKENS.forEach { (token, label) ->
                AssistChip(onClick = { vm.update { it.copy(pattern = it.pattern + token) } }, label = { Text(stringResource(label)) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            OutlinedTextField(
                value = rule.find,
                onValueChange = { v -> vm.update { it.copy(find = v) } },
                label = { Text(stringResource(R.string.rename_find_label)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = rule.replace,
                onValueChange = { v -> vm.update { it.copy(replace = v) } },
                label = { Text(stringResource(R.string.rename_replace_label)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            FilterChip(rule.regex, { vm.update { it.copy(regex = !it.regex) } }, { Text(stringResource(R.string.rename_regex)) })
            FilterChip(rule.ignoreCase, { vm.update { it.copy(ignoreCase = !it.ignoreCase) } }, { Text(stringResource(R.string.rename_ignore_case)) })
            FilterChip(rule.touchExtension, { vm.update { it.copy(touchExtension = !it.touchExtension) } }, { Text(stringResource(R.string.rename_touch_extension)) })
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            CASES.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = rule.case == mode,
                    onClick = { vm.update { it.copy(case = mode) } },
                    shape = SegmentedButtonDefaults.itemShape(i, CASES.size)
                ) { Text(stringResource(label), maxLines = 1) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
            NumberField(R.string.rename_counter_start, rule.counterStart, Modifier.weight(1f)) { v -> vm.update { it.copy(counterStart = v) } }
            NumberField(R.string.rename_counter_step, rule.counterStep, Modifier.weight(1f)) { v -> vm.update { it.copy(counterStep = v) } }
            NumberField(R.string.rename_counter_pad, rule.counterPad, Modifier.weight(1f)) { v -> vm.update { it.copy(counterPad = v.coerceIn(0, 12)) } }
        }
    }
}

@Composable
private fun NumberField(label: Int, value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { s -> onChange(if (s.isEmpty()) 0 else s.toIntOrNull() ?: value) },
        label = { Text(stringResource(label), maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun PreviewRow(p: RenamePreview) {
    val bad = p.status != RenameStatus.OK && p.status != RenameStatus.UNCHANGED
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Dimens.md)) {
            Column(Modifier.weight(1f)) {
                Text(p.item.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                Text("→ " + p.newName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            }
            p.status.label()?.let { res ->
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (bad) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        stringResource(res),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (bad) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Dimens.sm, vertical = Dimens.xs)
                    )
                }
            }
        }
    }
}
