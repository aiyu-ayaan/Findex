package com.aiyu.findex.ui.settings

import android.os.Build
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
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.foundation.clickable
import com.aiyu.findex.core.update.UpdateRepository
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
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.model.AppSettings
import com.aiyu.findex.core.model.ThemeMode
import com.aiyu.findex.core.settings.SettingsRepository
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.components.SectionHeader
import com.aiyu.findex.ui.components.groupedShape
import com.aiyu.findex.ui.components.SettingItem
import com.aiyu.findex.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repo: SettingsRepository, updates: UpdateRepository) : ViewModel() {
    val installedVersion = updates.installedName

    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }
    fun setDynamic(on: Boolean) = viewModelScope.launch { repo.setDynamicColor(on) }
    fun setHidden(on: Boolean) = viewModelScope.launch { repo.setShowHidden(on) }
    fun setGrid(on: Boolean) = viewModelScope.launch { repo.setGridView(on) }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenAbout: () -> Unit, onOpenUpdates: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = readableGutter(), end = readableGutter(),
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Dimens.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.xxs)
        ) {
            item { SectionHeader(stringResource(R.string.settings_appearance)) }
            item {
                Surface(shape = groupedShape(0, if (dynamicSupported) 2 else 1), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(Dimens.lg)) {
                        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(Dimens.md))
                        // Connected button group: one choice, shapes morph on selection.
                        val options = listOf(
                            Triple(ThemeMode.SYSTEM, R.string.settings_theme_system, Icons.Rounded.Contrast),
                            Triple(ThemeMode.LIGHT, R.string.settings_theme_light, Icons.Rounded.LightMode),
                            Triple(ThemeMode.DARK, R.string.settings_theme_dark, Icons.Rounded.DarkMode)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                            options.forEachIndexed { index, (mode, label, icon) ->
                                ToggleButton(
                                    checked = settings.themeMode == mode,
                                    onCheckedChange = { viewModel.setTheme(mode) },
                                    shapes = when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(icon, contentDescription = null, modifier = Modifier.padding(end = Dimens.xs).height(ToggleButtonDefaults.IconSize))
                                    Text(stringResource(label), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            if (dynamicSupported) {
                item {
                    SwitchRow(Icons.Rounded.Palette, R.string.settings_dynamic_color, R.string.settings_dynamic_color_desc, settings.dynamicColor, viewModel::setDynamic, groupedShape(1, 2))
                }
            }
            item { SectionHeader(stringResource(R.string.settings_files)) }
            item { SwitchRow(Icons.Rounded.Visibility, R.string.show_hidden_files, R.string.settings_show_hidden_desc, settings.showHidden, viewModel::setHidden, groupedShape(0, 2)) }
            item { SwitchRow(Icons.Rounded.GridView, R.string.settings_grid, R.string.settings_grid_desc, settings.gridView, viewModel::setGrid, groupedShape(1, 2)) }
            item { SectionHeader(stringResource(R.string.settings_updates)) }
            item {
                SettingItem(
                    headlineContent = { Text(stringResource(R.string.settings_updates)) },
                    supportingContent = { Text(stringResource(R.string.settings_updates_subtitle)) },
                    leadingContent = { Icon(Icons.Rounded.SystemUpdate, null) },
                    modifier = Modifier.clip(groupedShape(0, 1)).clickable(onClick = onOpenUpdates)
                )
            }
            item { SectionHeader(stringResource(R.string.settings_about)) }
            item {
                SettingItem(
                    headlineContent = { Text(stringResource(R.string.about_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_about_subtitle, viewModel.installedVersion)) },
                    leadingContent = { Icon(Icons.Rounded.Info, null) },
                    modifier = Modifier.clip(groupedShape(0, 1)).clickable(onClick = onOpenAbout)
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    titleRes: Int,
    descRes: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    shape: androidx.compose.ui.graphics.Shape
) {
    SettingItem(
        headlineContent = { Text(stringResource(titleRes)) },
        supportingContent = { Text(stringResource(descRes)) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier
            .clip(shape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
    )
}

