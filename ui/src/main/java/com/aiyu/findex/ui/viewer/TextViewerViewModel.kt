@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.aiyu.findex.ui.viewer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aiyu.findex.core.settings.SettingsRepository
import com.aiyu.findex.core.viewer.NotTextException
import com.aiyu.findex.core.viewer.SaveResult
import com.aiyu.findex.core.viewer.TextDocument
import com.aiyu.findex.core.viewer.TextDocuments
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.navigation.TextViewerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TextViewerState {
    data object Loading : TextViewerState
    /** [errorRes] is a string resource id. */
    data class Failed(val errorRes: Int) : TextViewerState
    data class Ready(val doc: TextDocument, val baseText: String, val conflict: Boolean = false) : TextViewerState
}

@HiltViewModel
class TextViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documents: TextDocuments,
    private val settings: SettingsRepository
) : ViewModel() {
    val route: TextViewerRoute = savedStateHandle.toRoute<TextViewerRoute>()

    val editor = TextFieldState()
    private val _state = MutableStateFlow<TextViewerState>(TextViewerState.Loading)
    val state: StateFlow<TextViewerState> = _state.asStateFlow()

    val wrap: StateFlow<Boolean> = settings.settings.map { it.textWrap }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            _state.value = try {
                val doc = documents.load(route.path)
                editor.edit { replace(0, length, doc.text) }
                editor.undoState.clearHistory()
                TextViewerState.Ready(doc, doc.text)
            } catch (e: NotTextException) {
                TextViewerState.Failed(R.string.viewer_text_not_text)
            } catch (e: Exception) {
                TextViewerState.Failed(R.string.viewer_text_error)
            }
        }
    }

    fun isDirty(): Boolean = (_state.value as? TextViewerState.Ready)?.let { !it.doc.readOnly && editor.text.toString() != it.baseText } == true

    fun toggleWrap() { viewModelScope.launch { settings.setTextWrap(!wrap.value) } }

    fun save(force: Boolean = false, then: () -> Unit = {}) {
        val s = _state.value as? TextViewerState.Ready ?: return
        val text = editor.text.toString()
        viewModelScope.launch {
            when (val r = documents.save(route.path, text, s.doc, force)) {
                SaveResult.Saved -> {
                    val fresh = runCatching { documents.load(route.path) }.getOrNull()
                    _state.value = TextViewerState.Ready(fresh ?: s.doc, text)
                    _messages.send(R.string.viewer_text_saved)
                    then()
                }
                SaveResult.Conflict -> _state.update { (it as? TextViewerState.Ready)?.copy(conflict = true) ?: it }
                is SaveResult.Failed -> _messages.send(R.string.viewer_text_error)
            }
        }
    }

    fun dismissConflict() = _state.update { (it as? TextViewerState.Ready)?.copy(conflict = false) ?: it }
}
