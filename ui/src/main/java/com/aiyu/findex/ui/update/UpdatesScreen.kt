package com.aiyu.findex.ui.update

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.model.AppSettings
import com.aiyu.findex.core.settings.SettingsRepository
import com.aiyu.findex.core.update.UpdateChannel
import com.aiyu.findex.core.update.UpdateRepository
import com.aiyu.findex.core.update.UpdateState
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.groupedShape
import com.aiyu.findex.ui.components.SettingItem
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdatesViewModel @Inject constructor(private val repo: SettingsRepository, private val updates: UpdateRepository) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val state = updates.state
    val installedVersion = updates.installedName
    fun setAutoUpdate(on: Boolean) = viewModelScope.launch { repo.setAutoUpdate(on) }
    fun setChannel(channel: UpdateChannel) = viewModelScope.launch { repo.setUpdateChannel(channel) }
    fun checkNow(channel: UpdateChannel) = viewModelScope.launch { updates.check(channel, manual = true) }
}

/** Own screen so Settings stays short; a found release opens the global [UpdateDialog]. */
@Composable
fun UpdatesScreen(onBack: () -> Unit, viewModel: UpdatesViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val updateState by viewModel.state.collectAsStateWithLifecycle()
    val channel = settings.updateChannel ?: UpdateChannel.forVersion(viewModel.installedVersion)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.updates_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = readableGutter(), end = readableGutter(), top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Dimens.xxl),
            verticalArrangement = Arrangement.spacedBy(Dimens.xxs)
        ) {
            item {
                SettingItem(
                    headlineContent = { Text(stringResource(R.string.settings_auto_update)) },
                    supportingContent = { Text(stringResource(R.string.settings_auto_update_desc)) },
                    leadingContent = { Icon(Icons.Rounded.SystemUpdate, null) },
                    trailingContent = { Switch(checked = settings.autoUpdate, onCheckedChange = null) },
                    modifier = Modifier.clip(groupedShape(0, 3)).toggleable(settings.autoUpdate, role = Role.Switch, onValueChange = viewModel::setAutoUpdate)
                )
            }
            item {
                Surface(shape = groupedShape(1, 3), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(Dimens.lg)) {
                        Text(stringResource(R.string.settings_channel), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Dimens.md))
                        val labels = mapOf(UpdateChannel.STABLE to R.string.channel_stable, UpdateChannel.BETA to R.string.channel_beta, UpdateChannel.ALPHA to R.string.channel_alpha)
                        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                            UpdateChannel.entries.forEachIndexed { index, option ->
                                ToggleButton(
                                    checked = channel == option,
                                    onCheckedChange = { viewModel.setChannel(option) },
                                    shapes = when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        UpdateChannel.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(labels.getValue(option)), maxLines = 1) }
                            }
                        }
                    }
                }
            }
            item {
                val status = when (val u = updateState) {
                    UpdateState.Checking -> stringResource(R.string.update_checking)
                    UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
                    is UpdateState.Available -> stringResource(R.string.update_available_row, u.release.name)
                    else -> stringResource(R.string.updates_current, viewModel.installedVersion)
                }
                SettingItem(
                    headlineContent = { Text(stringResource(R.string.settings_check_updates)) },
                    supportingContent = { Text(status) },
                    leadingContent = { Icon(Icons.Rounded.Refresh, null) },
                    modifier = Modifier.clip(groupedShape(2, 3)).clickable(enabled = updateState != UpdateState.Checking) { viewModel.checkNow(channel) }
                )
            }
        }
    }
}
