package com.aiyu.findex.ui.viewer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aiyu.findex.ui.navigation.AudioPlayerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class RepeatMode { OFF, ALL, ONE }

data class AudioUiState(
    val queue: List<String> = emptyList(),
    val index: Int = 0,
    val playing: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val repeat: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    val error: Boolean = false
) {
    val current: String? get() = queue.getOrNull(index)
}

/** Platform MediaPlayer. Playback ends when the screen leaves (no service); background playback would justify Media3. */
@HiltViewModel
class AudioPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val route: AudioPlayerRoute = savedStateHandle.toRoute<AudioPlayerRoute>()

    private val _state = MutableStateFlow(AudioUiState())
    val state: StateFlow<AudioUiState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
        }.build()
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = pause()
    }

    init {
        ContextCompat.registerReceiver(context, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        viewModelScope.launch {
            val queue = withContext(Dispatchers.IO) {
                val start = File(route.path)
                (start.parentFile?.listFiles { f -> f.isFile && f.extension.lowercase() in InAppViewers.AUDIO }?.sortedBy { it.name.lowercase() }?.map { it.path })
                    ?.takeIf { route.path in it } ?: listOf(route.path)
            }
            _state.update { it.copy(queue = queue, index = queue.indexOf(route.path)) }
            play(_state.value.index)
        }
    }

    private fun play(index: Int) {
        val path = _state.value.queue.getOrNull(index) ?: return
        player?.release()
        _state.update { it.copy(index = index, positionMs = 0, durationMs = 0, error = false) }
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            mp.setDataSource(path)
            mp.setOnPreparedListener { p ->
                _state.update { it.copy(durationMs = p.duration) }
                if (audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { p.start(); setPlaying(true) }
            }
            mp.setOnCompletionListener { onCompleted() }
            mp.setOnErrorListener { _, _, _ -> _state.update { it.copy(error = true, playing = false) }; true }
            mp.prepareAsync()
        } catch (e: Exception) {
            _state.update { it.copy(error = true, playing = false) }
        }
    }

    private fun setPlaying(on: Boolean) {
        _state.update { it.copy(playing = on) }
        ticker?.cancel()
        if (on) ticker = viewModelScope.launch {
            while (isActive) {
                player?.let { p -> runCatching { _state.update { s -> s.copy(positionMs = p.currentPosition) } } }
                delay(300)
            }
        }
    }

    private fun onCompleted() {
        val s = _state.value
        when {
            s.repeat == RepeatMode.ONE -> play(s.index)
            s.shuffle && s.queue.size > 1 -> play(randomOther(s))
            s.index + 1 < s.queue.size -> play(s.index + 1)
            s.repeat == RepeatMode.ALL -> play(0)
            else -> { setPlaying(false); audio.abandonAudioFocusRequest(focusRequest) }
        }
    }

    private fun randomOther(s: AudioUiState) = (s.queue.indices - s.index).random()

    fun toggle() { if (_state.value.playing) pause() else resume() }
    private fun resume() {
        val p = player ?: return
        if (!_state.value.error && audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { runCatching { p.start() }; setPlaying(true) }
    }
    fun pause() { runCatching { player?.pause() }; if (_state.value.playing) setPlaying(false) }

    fun next() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        play(if (s.shuffle && s.queue.size > 1) randomOther(s) else (s.index + 1) % s.queue.size)
    }

    /** Restarts the track when more than 3 s in, like most players. */
    fun previous() {
        val s = _state.value
        if (s.positionMs > 3000) seek(0) else play((s.index - 1 + s.queue.size) % s.queue.size)
    }

    fun seek(ms: Int) { runCatching { player?.seekTo(ms) }; _state.update { it.copy(positionMs = ms) } }
    fun playAt(index: Int) = play(index)
    fun cycleRepeat() = _state.update { it.copy(repeat = RepeatMode.entries[(it.repeat.ordinal + 1) % 3]) }
    fun toggleShuffle() = _state.update { it.copy(shuffle = !it.shuffle) }

    override fun onCleared() {
        ticker?.cancel()
        runCatching { context.unregisterReceiver(noisy) }
        player?.release()
        audio.abandonAudioFocusRequest(focusRequest)
    }
}
