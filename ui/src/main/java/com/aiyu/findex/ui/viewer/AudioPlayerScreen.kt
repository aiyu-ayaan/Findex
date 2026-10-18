package com.aiyu.findex.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.FileThumbnail
import com.aiyu.findex.ui.theme.Dimens
import java.io.File

@Composable
fun AudioPlayerScreen(onBack: () -> Unit, viewModel: AudioPlayerViewModel = hiltViewModel()) {
    val s by viewModel.state.collectAsStateWithLifecycle()
    val current = s.current?.let(::File)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.viewer_audio_queue)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxWidth().padding(Dimens.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                if (current != null) FileThumbnail(current.path, false, current.extension, size = 200.dp, lastModified = current.lastModified())
                Text(
                    current?.nameWithoutExtension.orEmpty(), style = MaterialTheme.typography.titleLarge,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Dimens.lg)
                )
                if (s.error) Text(stringResource(R.string.viewer_audio_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = s.positionMs.toFloat(), onValueChange = { viewModel.seek(it.toInt()) },
                    valueRange = 0f..s.durationMs.coerceAtLeast(1).toFloat(), enabled = s.durationMs > 0,
                    modifier = Modifier.padding(top = Dimens.md)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(clock(s.positionMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(clock(s.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    FilledTonalIconToggleButton(checked = s.shuffle, onCheckedChange = { viewModel.toggleShuffle() }) { Icon(Icons.Rounded.Shuffle, stringResource(R.string.viewer_audio_shuffle)) }
                    IconButton(onClick = viewModel::previous) { Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.viewer_audio_previous)) }
                    FilledIconButton(onClick = viewModel::toggle, modifier = Modifier.size(IconButtonDefaults.largeContainerSize())) {
                        Icon(
                            if (s.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            stringResource(if (s.playing) R.string.viewer_audio_pause else R.string.viewer_audio_play),
                            Modifier.size(IconButtonDefaults.largeIconSize)
                        )
                    }
                    IconButton(onClick = viewModel::next) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.viewer_audio_next)) }
                    FilledTonalIconToggleButton(checked = s.repeat != RepeatMode.OFF, onCheckedChange = { viewModel.cycleRepeat() }) {
                        Icon(if (s.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, stringResource(R.string.viewer_audio_repeat))
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(s.queue) { i, path ->
                    ListItem(
                        headlineContent = { Text(File(path).name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                        colors = ListItemDefaults.colors(containerColor = if (i == s.index) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
                        modifier = Modifier.clickable { viewModel.playAt(i) }
                    )
                }
            }
        }
    }
}

private fun clock(ms: Int): String = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
