package com.aiyu.findex.ui.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.model.DriveType
import com.aiyu.findex.core.network.SmbRepository
import com.aiyu.findex.core.repository.StorageRepository
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.components.SectionHeader
import com.aiyu.findex.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One place a new tab can start: a device volume or a saved network share. */
data class TabLocation(val path: String, val name: String, val detail: String, val type: DriveType)

@HiltViewModel
class LocationsViewModel @Inject constructor(
    storageRepository: StorageRepository,
    smbRepository: SmbRepository
) : ViewModel() {
    val locations = combine(storageRepository.getStorageDrives(), smbRepository.servers) { drives, servers ->
        drives.map { TabLocation(it.path, it.name, it.path, it.type) } +
            servers.map { TabLocation(it.rootPath, it.label, "\\\\${it.host}\\${it.share}", DriveType.NETWORK) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Picks where a new tab opens, so a tab can browse the device or any network share. */
@Composable
fun NewTabSheet(onPick: (String) -> Unit, onDismiss: () -> Unit, viewModel: LocationsViewModel = hiltViewModel()) {
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Dimens.lg)) {
            SectionHeader(stringResource(R.string.new_tab_title), modifier = Modifier.padding(horizontal = Dimens.xl))
            locations.forEach { location ->
                ListItem(
                    headlineContent = { Text(location.name) },
                    supportingContent = { Text(location.detail, maxLines = 1) },
                    leadingContent = {
                        ShapeIcon(
                            icon = when (location.type) {
                                DriveType.NETWORK -> Icons.Rounded.Dns
                                DriveType.SD_CARD -> Icons.Rounded.SdCard
                                DriveType.USB -> Icons.Rounded.Usb
                                DriveType.INTERNAL -> Icons.Rounded.Storage
                            },
                            shape = MaterialShapes.Cookie9Sided,
                            container = if (location.type == DriveType.NETWORK) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                            content = if (location.type == DriveType.NETWORK) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .padding(horizontal = Dimens.sm)
                        .clip(MaterialTheme.shapes.large)
                        .clickable { onPick(location.path) }
                )
            }
        }
    }
}
