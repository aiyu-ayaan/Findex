package com.aiyu.findex.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.util.openFileWithChooser
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PdfViewerScreen(onBack: () -> Unit, viewModel: PdfViewerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var goTo by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(File(viewModel.route.path).name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } },
                actions = {
                    IconButton(onClick = { context.openFileWithChooser(viewModel.route.path) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.action_open_in_another_app)) }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                PdfState.Loading -> LoadingIndicator(Modifier.align(Alignment.Center))
                PdfState.Failed -> Text(stringResource(R.string.viewer_pdf_error), Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                is PdfState.Protected -> ProtectedPdf(s, onPassword = viewModel::open, onOpenWith = { context.openFileWithChooser(viewModel.route.path) })
                is PdfState.Ready -> {
                    var widthPx by remember { mutableStateOf(0) }
                    var zoom by remember { mutableFloatStateOf(1f) }
                    var panX by remember { mutableFloatStateOf(0f) }
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { widthPx = it.width }
                            // Two-finger pinch only, so one-finger scrolling still reaches the list.
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    do {
                                        val event = awaitPointerEvent()
                                        if (event.changes.size > 1) {
                                            zoom = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                                            val limit = size.width * (zoom - 1f) / 2f
                                            panX = (panX + event.calculatePan().x).coerceIn(-limit, limit)
                                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                            .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = panX }
                    ) {
                        items(s.pages) { index ->
                            PdfPage(viewModel, index, widthPx)
                        }
                    }
                    val current = listState.firstVisibleItemIndex + 1
                    AssistChip(
                        onClick = { goTo = true },
                        label = { Text(stringResource(R.string.viewer_pdf_page_of, current, s.pages)) },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(Dimens.lg)
                    )
                    if (goTo) GoToPageDialog(s.pages, onDismiss = { goTo = false }) { page ->
                        goTo = false
                        scope.launch { listState.scrollToItem(page - 1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(viewModel: PdfViewerViewModel, index: Int, widthPx: Int) {
    if (widthPx <= 0) return
    // Rendering is cheap to cancel: leaving the viewport cancels this producer.
    val bitmap by produceState<Bitmap?>(null, index, widthPx) { value = viewModel.page(index, widthPx) }
    val ratio by produceState(1.414f, index) { value = viewModel.ratio(index) }
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f / ratio))
    } else {
        Box(Modifier.fillMaxWidth().aspectRatio(1f / ratio))
    }
}

@Composable
private fun ProtectedPdf(s: PdfState.Protected, onPassword: (String) -> Unit, onOpenWith: () -> Unit) {
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(Dimens.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.viewer_pdf_protected), style = MaterialTheme.typography.titleMedium)
        if (s.canTryPassword) {
            OutlinedTextField(
                value = password, onValueChange = { password = it }, singleLine = true, isError = s.wrong,
                label = { Text(stringResource(R.string.viewer_pdf_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.padding(top = Dimens.lg)
            )
            Button(onClick = { onPassword(password) }, enabled = password.isNotEmpty(), modifier = Modifier.padding(top = Dimens.md)) { Text(stringResource(R.string.action_open)) }
        }
        TextButton(onClick = onOpenWith, modifier = Modifier.padding(top = Dimens.md)) { Text(stringResource(R.string.action_open_in_another_app)) }
    }
}

@Composable
private fun GoToPageDialog(pages: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val page = text.toIntOrNull()?.takeIf { it in 1..pages }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.viewer_pdf_go_to_page)) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.filter(Char::isDigit).take(5) }, singleLine = true,
                label = { Text("1 – $pages") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = { TextButton(onClick = { page?.let(onGo) }, enabled = page != null) { Text(stringResource(R.string.action_open)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
