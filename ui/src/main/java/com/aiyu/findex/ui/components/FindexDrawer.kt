package com.aiyu.findex.ui.components

import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRail
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.navigation.AnalyzerRoute
import com.aiyu.findex.ui.navigation.AppCleanerRoute
import com.aiyu.findex.ui.navigation.InstagramRoute
import com.aiyu.findex.ui.navigation.TelegramRoute
import com.aiyu.findex.ui.navigation.DualPaneRoute
import com.aiyu.findex.ui.navigation.CleanerRoute
import com.aiyu.findex.ui.navigation.DuplicatesRoute
import com.aiyu.findex.ui.navigation.FavoritesRoute
import com.aiyu.findex.ui.navigation.LargeFilesRoute
import com.aiyu.findex.ui.navigation.RecentChangesRoute
import com.aiyu.findex.ui.navigation.VaultRoute
import com.aiyu.findex.ui.navigation.NetworkRoute
import com.aiyu.findex.ui.navigation.HomeRoute
import com.aiyu.findex.ui.navigation.RecycleBinRoute
import com.aiyu.findex.ui.navigation.SettingsRoute
import com.aiyu.findex.ui.navigation.ShareRoute
import com.aiyu.findex.ui.navigation.TabsRoute
import com.aiyu.findex.ui.navigation.WhatsAppRoute
import com.aiyu.findex.ui.theme.Dimens

private data class DrawerEntry(val titleRes: Int, val icon: ImageVector, val route: Any)

private val mainEntries = listOf(
    DrawerEntry(R.string.nav_home, Icons.Rounded.Home, HomeRoute),
    DrawerEntry(R.string.nav_storage_graph, Icons.Rounded.PieChart, AnalyzerRoute),
    DrawerEntry(R.string.nav_storage_cleaner, Icons.Rounded.CleaningServices, CleanerRoute),
    DrawerEntry(R.string.nav_app_cleaner, Icons.Rounded.Apps, AppCleanerRoute),
    DrawerEntry(R.string.nav_whatsapp_cleaner, Icons.Rounded.Forum, WhatsAppRoute),
    DrawerEntry(R.string.nav_telegram_cleaner, Icons.AutoMirrored.Rounded.Send, TelegramRoute),
    DrawerEntry(R.string.nav_instagram_cleaner, Icons.Rounded.PhotoCamera, InstagramRoute),
    DrawerEntry(R.string.nav_duplicates, Icons.Rounded.FileCopy, DuplicatesRoute),
    DrawerEntry(R.string.nav_recycle_bin, Icons.Rounded.Delete, RecycleBinRoute),
)

private val moreEntries = listOf(
    DrawerEntry(R.string.nav_favorites, Icons.Rounded.Star, FavoritesRoute),
    DrawerEntry(R.string.nav_dual_pane, Icons.Rounded.VerticalSplit, DualPaneRoute()),
    DrawerEntry(R.string.nav_large_files, Icons.Rounded.SdStorage, LargeFilesRoute),
    DrawerEntry(R.string.nav_recent_changes, Icons.Rounded.History, RecentChangesRoute),
    DrawerEntry(R.string.nav_vault, Icons.Rounded.Lock, VaultRoute()),
)

@Composable
fun FindexDrawerSheet(
    onNavigate: (Any) -> Unit,
    tabCount: Int,
    isCurrent: (Any) -> Boolean,
    modifier: Modifier = Modifier
) {
    // Plain column: PushDrawer already supplies the surface behind it.
    Column(modifier.systemBarsPadding()) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = Dimens.xl, vertical = Dimens.xl)
            ) {
                ShapeIcon(
                    icon = Icons.Rounded.Folder,
                    shape = MaterialShapes.Cookie9Sided,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = Dimens.xxxl + Dimens.xs,
                    iconSize = Dimens.xl + Dimens.xs
                )
                Spacer(Modifier.width(Dimens.lg))
                Column {
                    Text(
                        text = stringResource(R.string.drawer_title),
                        style = MaterialTheme.typography.headlineSmallEmphasized
                    )
                    Text(
                        text = stringResource(R.string.drawer_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            mainEntries.forEach { entry ->
                NavigationDrawerItem(
                    icon = { Icon(entry.icon, contentDescription = null) },
                    label = { Text(stringResource(entry.titleRes)) },
                    selected = isCurrent(entry.route),
                    onClick = { onNavigate(entry.route) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }

            HorizontalDivider(Modifier.padding(horizontal = Dimens.xxl, vertical = Dimens.sm))
            Text(
                text = stringResource(R.string.drawer_section_more),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.xxl, vertical = Dimens.sm)
            )
            moreEntries.forEach { entry ->
                NavigationDrawerItem(
                    icon = { Icon(entry.icon, contentDescription = null) },
                    label = { Text(stringResource(entry.titleRes)) },
                    selected = isCurrent(entry.route),
                    onClick = { onNavigate(entry.route) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }

            HorizontalDivider(Modifier.padding(horizontal = Dimens.xxl, vertical = Dimens.sm))

            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.Tab, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_tabs)) },
                badge = { if (tabCount > 0) Text(tabCount.toString(), style = MaterialTheme.typography.labelLarge) },
                selected = isCurrent(TabsRoute),
                onClick = { onNavigate(TabsRoute) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.CloudQueue, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_network_storage)) },
                selected = isCurrent(NetworkRoute),
                onClick = { onNavigate(NetworkRoute) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.Devices, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_share_phone)) },
                selected = isCurrent(ShareRoute),
                onClick = { onNavigate(ShareRoute) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_settings)) },
                selected = isCurrent(SettingsRoute),
                onClick = { onNavigate(SettingsRoute) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            Spacer(Modifier.height(Dimens.lg))
        }
    }
}

private data class RailEntry(val labelRes: Int, val icon: ImageVector, val route: Any)

private val railEntries = listOf(
    RailEntry(R.string.nav_home, Icons.Rounded.Home, HomeRoute),
    RailEntry(R.string.rail_graph, Icons.Rounded.PieChart, AnalyzerRoute),
    RailEntry(R.string.rail_cleaner, Icons.Rounded.CleaningServices, CleanerRoute),
    RailEntry(R.string.rail_whatsapp, Icons.Rounded.Forum, WhatsAppRoute),
    RailEntry(R.string.rail_bin, Icons.Rounded.Delete, RecycleBinRoute),
    RailEntry(R.string.nav_tabs, Icons.Rounded.Tab, TabsRoute),
    // Settings stays reachable from Home's top bar; the rail is capped at 7 items.
    RailEntry(R.string.rail_network, Icons.Rounded.CloudQueue, NetworkRoute),
)

/** Medium and expanded windows: the same destinations as the drawer, as an always-visible rail (MD3: 3–7 items). */
@Composable
fun FindexNavRail(
    onNavigate: (Any) -> Unit,
    tabCount: Int,
    isCurrent: (Any) -> Boolean,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        header = {
            ShapeIcon(
                icon = Icons.Rounded.Folder,
                shape = MaterialShapes.Cookie9Sided,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                size = Dimens.xxxl,
                iconSize = Dimens.xl,
                modifier = Modifier.padding(vertical = Dimens.md)
            )
        },
        modifier = modifier
    ) {
        railEntries.forEach { entry ->
            NavigationRailItem(
                selected = isCurrent(entry.route),
                onClick = { onNavigate(entry.route) },
                icon = {
                    if (entry.route == TabsRoute && tabCount > 0) {
                        BadgedBox(badge = { Badge { Text(tabCount.toString()) } }) { Icon(entry.icon, contentDescription = null) }
                    } else {
                        Icon(entry.icon, contentDescription = null)
                    }
                },
                label = { Text(stringResource(entry.labelRes), maxLines = 1) }
            )
        }
    }
}
