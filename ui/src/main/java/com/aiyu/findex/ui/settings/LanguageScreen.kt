package com.aiyu.findex.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.aiyu.findex.core.settings.AppLocale
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.groupedShape
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.theme.Dimens

/** Picks the app language; "" = follow the system. */
@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val current = AppLocale.current(context)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val options = listOf("") + AppLocale.tags

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_language)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = readableGutter(),
                end = readableGutter(),
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + Dimens.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.xxs)
        ) {
            itemsIndexed(options, key = { _, tag -> tag }) { index, tag ->
                val selected = tag == current
                val shape = groupedShape(index, options.size)
                val container = if (selected) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
                Surface(
                    shape = shape,
                    color = container,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .selectable(selected = selected, role = Role.RadioButton) {
                            if (!selected) AppLocale.set(context, tag)
                        }
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                if (tag.isEmpty()) {
                                    stringResource(R.string.settings_language_system)
                                } else {
                                    AppLocale.nativeName(tag)
                                }
                            )
                        },
                        trailingContent = { RadioButton(selected = selected, onClick = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}
