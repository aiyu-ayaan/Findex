package com.aiyu.findex.ui.link

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.theme.Dimens
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen QR scanner for pairing with Findex for desktop: CameraX preview plus ZXing on the luminance
 * plane of each analysis frame. Asks for the camera itself; a denial, a missing camera or a camera that
 * fails to open shows a message here instead of crashing. [onResult] gets the first decoded text, once.
 */
@Composable
fun QrScannerDialog(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim)) {
            when {
                cameraError || denied -> ScannerMessage(
                    text = stringResource(if (denied) R.string.link_camera_denied else R.string.link_camera_error),
                    onClose = onDismiss
                )
                granted -> {
                    CameraPreview(onResult = onResult, onError = { cameraError = true })
                    // Viewfinder frame.
                    Box(
                        Modifier.align(Alignment.Center).size(260.dp)
                            .border(3.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraLarge)
                    )
                    // On a solid pill: the preview behind can be any brightness.
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(Dimens.xl)
                    ) {
                        Text(
                            stringResource(R.string.link_scan_prompt),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.md)
                        )
                    }
                }
            }
            FilledTonalIconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).safeDrawingPadding().padding(Dimens.md)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun ScannerMessage(text: String, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Dimens.xl),
        verticalArrangement = Arrangement.spacedBy(Dimens.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.NoPhotography, null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(48.dp))
        Text(text, color = MaterialTheme.colorScheme.inverseOnSurface, textAlign = TextAlign.Center)
        TextButton(onClick = onClose) { Text(stringResource(R.string.link_pair_by_address)) }
    }
}

@Composable
private fun CameraPreview(onResult: (String) -> Unit, onError: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val result by rememberUpdatedState(onResult)
    val error by rememberUpdatedState(onError)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val done = remember { AtomicBoolean(false) }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get().also { provider = it }
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { a ->
                        a.setAnalyzer(analysisExecutor) { image ->
                            val text = image.use { decode(it) }
                            if (text != null && done.compareAndSet(false, true)) {
                                ContextCompat.getMainExecutor(context).execute { result(text) }
                            }
                        }
                    }
                val selector = when {
                    p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> { error(); return@addListener }
                }
                p.unbindAll()
                p.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (e: Exception) {
                // No camera, camera in use, or a broken camera service: say so instead of crashing.
                error()
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { provider?.unbindAll() }
            analysisExecutor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private val qrReader = ThreadLocal.withInitial {
    MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
    }
}

/** QR text in the frame's luminance (Y) plane, or null. Rotation does not matter: QR is rotation-invariant. */
private fun decode(image: ImageProxy): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val buffer = plane.buffer.duplicate().apply { rewind() }
    val rowStride = plane.rowStride
    val width = image.width
    val height = image.height
    val data = ByteArray(rowStride * height).also { buffer.get(it, 0, minOf(it.size, buffer.remaining())) }
    val source = PlanarYUVLuminanceSource(data, rowStride, height, 0, 0, width, height, false)
    val reader = qrReader.get()!!
    return try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: NotFoundException) {
        null
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }
}
