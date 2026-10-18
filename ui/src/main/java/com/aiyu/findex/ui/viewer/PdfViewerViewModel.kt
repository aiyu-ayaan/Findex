package com.aiyu.findex.ui.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aiyu.findex.ui.navigation.PdfViewerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed interface PdfState {
    data object Loading : PdfState
    data class Ready(val pages: Int) : PdfState
    /** [canTryPassword]: API 35+ can open protected files with a password; older devices can only hand off. */
    data class Protected(val canTryPassword: Boolean, val wrong: Boolean) : PdfState
    data object Failed : PdfState
}

@HiltViewModel
class PdfViewerViewModel @Inject constructor(savedStateHandle: SavedStateHandle) : ViewModel() {
    val route: PdfViewerRoute = savedStateHandle.toRoute<PdfViewerRoute>()

    private val _state = MutableStateFlow<PdfState>(PdfState.Loading)
    val state: StateFlow<PdfState> = _state.asStateFlow()

    // PdfRenderer is not thread-safe and only one page may be open at a time.
    private val lock = Mutex()
    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val cache = object : LruCache<Int, Bitmap>(6) {}

    init { open(null) }

    fun open(password: String?) {
        viewModelScope.launch {
            _state.value = withContext(Dispatchers.IO) {
                lock.withLock {
                    runCatching { renderer?.close(); fd?.close() }
                    renderer = null
                    try {
                        val pfd = ParcelFileDescriptor.open(File(route.path), ParcelFileDescriptor.MODE_READ_ONLY).also { fd = it }
                        val r = if (Build.VERSION.SDK_INT >= 35 && password != null) {
                            PdfRenderer(pfd, LoadParams.Builder().setPassword(password).build())
                        } else PdfRenderer(pfd)
                        renderer = r
                        PdfState.Ready(r.pageCount)
                    } catch (e: SecurityException) {
                        PdfState.Protected(canTryPassword = Build.VERSION.SDK_INT >= 35, wrong = password != null)
                    } catch (e: Exception) {
                        PdfState.Failed
                    }
                }
            }
        }
    }

    /** Page [index] scaled to [widthPx]. Null if the renderer is gone. */
    suspend fun page(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(index)?.takeIf { it.width == widthPx }?.let { return@withContext it }
        lock.withLock {
            val r = renderer ?: return@withLock null
            r.openPage(index).use { p ->
                val bmp = Bitmap.createBitmap(widthPx, (widthPx * p.height.toFloat() / p.width).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put(index, bmp)
                bmp
            }
        }
    }

    /** Height/width ratio of page [index], so unrendered pages hold their space. */
    suspend fun ratio(index: Int): Float = withContext(Dispatchers.IO) {
        lock.withLock { renderer?.openPage(index)?.use { it.height.toFloat() / it.width } ?: 1.414f }
    }

    override fun onCleared() {
        runCatching { renderer?.close(); fd?.close() }
    }
}
