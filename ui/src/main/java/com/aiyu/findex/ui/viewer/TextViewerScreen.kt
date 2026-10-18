@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.aiyu.findex.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.ui.LocalSnackbarHostState
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.theme.Dimens
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextViewerScreen(onBack: () -> Unit, viewModel: TextViewerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val wrap by viewModel.wrap.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbarHostState.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var confirmLeave by remember { mutableStateOf(false) }
    val editor = viewModel.editor
    // Reading the text here makes the guard and FAB follow every keystroke.
    val dirty = (state as? TextViewerState.Ready)?.let { !it.doc.readOnly && editor.text.toString() != it.baseText } == true

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(context.getString(it)) } }
    // Predictive back stays enabled when clean: the handler only exists while there is something to lose.
    BackHandler(enabled = dirty) { confirmLeave = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(File(viewModel.route.path).name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                navigationIcon = { IconButton(onClick = { if (dirty) confirmLeave = true else onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } },
                actions = {
                    if (state is TextViewerState.Ready) {
                        IconButton(onClick = { editor.undoState.undo() }, enabled = editor.undoState.canUndo) { Icon(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.viewer_text_undo)) }
                        IconButton(onClick = { editor.undoState.redo() }, enabled = editor.undoState.canRedo) { Icon(Icons.AutoMirrored.Rounded.Redo, stringResource(R.string.viewer_text_redo)) }
                        IconToggleButton(checked = wrap, onCheckedChange = { viewModel.toggleWrap() }) { Icon(Icons.AutoMirrored.Rounded.WrapText, stringResource(R.string.viewer_text_wrap)) }
                    }
                }
            )
        },
        floatingActionButton = {
            AnimatedVisibility(dirty, enter = scaleIn(), exit = scaleOut()) {
                FloatingActionButton(onClick = { viewModel.save() }) { Icon(Icons.Rounded.Save, stringResource(R.string.viewer_text_save)) }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                TextViewerState.Loading -> LoadingIndicator(Modifier.align(Alignment.Center))
                is TextViewerState.Failed -> Text(stringResource(s.errorRes), Modifier.align(Alignment.Center).padding(Dimens.xl), color = MaterialTheme.colorScheme.onSurfaceVariant)
                is TextViewerState.Ready -> Column(Modifier.fillMaxSize()) {
                    val note = when {
                        s.doc.truncated -> R.string.viewer_text_truncated
                        s.doc.readOnly -> R.string.viewer_text_read_only
                        else -> null
                    }
                    if (note != null) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(note), Modifier.padding(Dimens.lg, Dimens.sm), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    val body = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).let { if (wrap) it else it.horizontalScroll(rememberScrollState()) }
                    BasicTextField(
                        state = editor,
                        readOnly = s.doc.readOnly,
                        lineLimits = TextFieldLineLimits.MultiLine(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = body.padding(Dimens.lg)
                    )
                    if (s.conflict) {
                        AlertDialog(
                            onDismissRequest = viewModel::dismissConflict,
                            title = { Text(stringResource(R.string.viewer_text_conflict_title)) },
                            text = { Text(stringResource(R.string.viewer_text_conflict_body)) },
                            confirmButton = { TextButton(onClick = { viewModel.dismissConflict(); viewModel.save(force = true) }) { Text(stringResource(R.string.viewer_text_overwrite)) } },
                            dismissButton = { TextButton(onClick = viewModel::dismissConflict) { Text(stringResource(R.string.cancel)) } }
                        )
                    }
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.viewer_text_unsaved_title)) },
            text = { Text(stringResource(R.string.viewer_text_unsaved_body)) },
            confirmButton = { TextButton(onClick = { confirmLeave = false; viewModel.save(then = onBack) }) { Text(stringResource(R.string.viewer_text_save)) } },
            dismissButton = {
                Row2(
                    { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) } },
                    { TextButton(onClick = { confirmLeave = false; onBack() }) { Text(stringResource(R.string.viewer_text_discard)) } }
                )
            }
        )
    }
}

@Composable
private fun Row2(a: @Composable () -> Unit, b: @Composable () -> Unit) = androidx.compose.foundation.layout.Row { a(); b() }
