package com.aiyu.findex.ui.imagecompress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.ImageOutputFormat
import com.aiyu.findex.core.model.ImageOutputMode
import com.aiyu.findex.core.model.ImageTransformSpec
import com.aiyu.findex.core.operation.ImageTransformer
import com.aiyu.findex.core.operation.OperationManager
import com.aiyu.findex.ui.navigation.ImageCompressRoute
import com.aiyu.findex.ui.navigation.SelectionHandoff
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

enum class Preset(val edge: Int?) { SMALL(1280), MEDIUM(1920), LARGE(2560), CUSTOM(null) }

data class CompressUiState(
    val preset: Preset = Preset.MEDIUM,
    val customEdge: String = "",
    val scalePercent: Int = 100,
    val quality: Int = 82,
    val format: ImageOutputFormat = ImageOutputFormat.KEEP,
    val output: ImageOutputMode = ImageOutputMode.COPY_WITH_SUFFIX,
    val keepMetadata: Boolean = false,
    val estimateBytes: Long? = null
) {
    val spec: ImageTransformSpec
        get() {
            val edge = if (preset == Preset.CUSTOM) customEdge.toIntOrNull()?.takeIf { it > 0 } else preset.edge
            return ImageTransformSpec(
                maxLongEdgePx = edge,
                scalePercent = if (edge == null && scalePercent < 100) scalePercent else null,
                quality = quality, format = format, output = output, keepMetadata = keepMetadata
            )
        }
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ImageCompressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transformer: ImageTransformer,
    private val operationManager: OperationManager
) : ViewModel() {
    private val token = savedStateHandle.toRoute<ImageCompressRoute>().token
    private val paths: List<String> = SelectionHandoff.take(token).orEmpty()

    val expired: Boolean = paths.isEmpty()
    val count: Int get() = paths.size

    private val _state = MutableStateFlow(CompressUiState())
    val state: StateFlow<CompressUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.debounce(400).mapLatest { it.spec }.distinctUntilChanged().collect { spec ->
                val est = if (paths.isEmpty()) null else transformer.estimateBytes(paths, spec)
                _state.update { it.copy(estimateBytes = est) }
            }
        }
    }

    fun update(change: (CompressUiState) -> CompressUiState) = _state.update { change(it).copy(estimateBytes = it.estimateBytes) }

    fun start() {
        val spec = _state.value.spec.let {
            if (it.output == ImageOutputMode.INTO_FOLDER) it.copy(outputDir = File(paths.first()).parent + "/Compressed") else it
        }
        if (paths.isNotEmpty()) operationManager.enqueue(FileOperation.ImageTransform(paths, spec))
    }
}
