package com.aiyu.findex.ui.components

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
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
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
import com.aiyu.findex.ui.navigation.CleanerRoute
import com.aiyu.findex.ui.navigation.HomeRoute
import com.aiyu.findex.ui.navigation.RecycleBinRoute
import com.aiyu.findex.ui.navigation.SettingsRoute
import com.aiyu.findex.ui.navigation.TabsRoute
import com.aiyu.findex.ui.navigation.WhatsAppRoute
import com.aiyu.findex.ui.theme.Dimens

private data class DrawerEntry(val titleRes: Int, val icon: ImageVector, val route: Any)

private val mainEntries = listOf(
    DrawerEntry(R.string.nav_home, Icons.Rounded.Home, HomeRoute),
    DrawerEntry(R.string.nav_storage_graph, Icons.Rounded.PieChart, AnalyzerRoute),
    DrawerEntry(R.string.nav_storage_cleaner, Icons.Rounded.CleaningServices, CleanerRoute),
    DrawerEntry(R.string.nav_whatsapp_cleaner, Icons.Rounded.Forum, WhatsAppRoute),
    DrawerEntry(R.string.nav_recycle_bin, Icons.Rounded.Delete, RecycleBinRoute),
)

@Composable
fun FindexDrawerSheet(
    onNavigate: (Any) -> Unit,
    tabCount: Int,
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
                    selected = entry.route == HomeRoute,
                    onClick = { onNavigate(entry.route) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }

            HorizontalDivider(Modifier.padding(horizontal = Dimens.xxl, vertical = Dimens.sm))

            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.Tab, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_tabs)) },
                badge = { if (tabCount > 0) Text(tabCount.toString(), style = MaterialTheme.typography.labelLarge) },
                selected = false,
                onClick = { onNavigate(TabsRoute) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            // Explain unavailable destinations instead of hiding them.
            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.CloudQueue, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_network_storage)) },
                badge = {
                    Text(
                        stringResource(R.string.badge_soon),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                },
                selected = false,
                onClick = {},
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_settings)) },
                selected = false,
                onClick = { onNavigate(SettingsRoute) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
            Spacer(Modifier.height(Dimens.lg))
        }
    }
}
