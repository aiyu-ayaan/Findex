package com.aiyu.findex.ui.about

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.SectionHeader
import com.aiyu.findex.ui.components.groupedShape
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.theme.Dimens

private const val REPO_URL = "https://github.com/aiyu-ayaan/Findex"
private const val GITHUB_URL = "https://github.com/aiyu-ayaan"
private const val PORTFOLIO_URL = "https://aiyu.co.in"

private data class Library(val name: String, val url: String, val license: String = "Apache-2.0")

/** Keep in sync with gradle/libs.versions.toml. */
private val libraries = listOf(
    Library("Kotlin", "https://github.com/JetBrains/kotlin"),
    Library("kotlinx.coroutines", "https://github.com/Kotlin/kotlinx.coroutines"),
    Library("kotlinx.serialization", "https://github.com/Kotlin/kotlinx.serialization"),
    Library("Jetpack Compose", "https://developer.android.com/jetpack/compose"),
    Library("Material 3 for Compose", "https://developer.android.com/jetpack/androidx/releases/compose-material3"),
    Library("AndroidX Core, Lifecycle, Activity, Navigation, DataStore, Room", "https://developer.android.com/jetpack/androidx"),
    Library("Hilt", "https://github.com/google/dagger"),
    Library("Coil", "https://github.com/coil-kt/coil"),
    Library("Zip4j", "https://github.com/srikanth-lingala/zip4j"),
    Library("Apache Commons Compress", "https://commons.apache.org/proper/commons-compress/"),
    Library("XZ for Java", "https://tukaani.org/xz/java.html", "0BSD"),
    Library("7-Zip-JBinding-4Android", "https://github.com/omicronapps/7-Zip-JBinding-4Android", "LGPL-2.1"),
    Library("smbj", "https://github.com/hierynomus/smbj"),
)

@Composable
private fun rememberOpenUrl(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { url -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }; Unit } }
}

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenAcknowledgements: () -> Unit) {
    val context = LocalContext.current
    val open = rememberOpenUrl()
    val info = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() }
    val version = info?.versionName.orEmpty()
    val build = info?.longVersionCode ?: 0L

    AboutScaffold(R.string.about_title, onBack) {
        item { SectionHeader(stringResource(R.string.about_section_app)) }
        item { Row(stringResource(R.string.app_name), stringResource(R.string.about_version_build, version, build), Icons.Rounded.Info, groupedShape(0, 2)) }
        item { Row(stringResource(R.string.about_source_code), "github.com/aiyu-ayaan/Findex", Icons.Rounded.Code, groupedShape(1, 2), external = true) { open(REPO_URL) } }

        item { SectionHeader(stringResource(R.string.about_section_developer)) }
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = Dimens.sm), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                Image(painterResource(R.drawable.developer), "aiyu-ayaan", Modifier.size(96.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                Text("aiyu-ayaan", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = Dimens.sm))
                Text(stringResource(R.string.about_developer_tagline), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        item { Row(stringResource(R.string.about_github), "github.com/aiyu-ayaan", Icons.Rounded.Code, groupedShape(0, 2), external = true) { open(GITHUB_URL) } }
        item { Row(stringResource(R.string.about_portfolio), "aiyu.co.in", Icons.Rounded.Language, groupedShape(1, 2), external = true) { open(PORTFOLIO_URL) } }

        item { SectionHeader(stringResource(R.string.about_section_open_source)) }
        item { Row(stringResource(R.string.about_acknowledgements), stringResource(R.string.about_acknowledgements_subtitle), Icons.Rounded.Description, groupedShape(0, 1), onClick = onOpenAcknowledgements) }
    }
}

@Composable
fun AcknowledgementsScreen(onBack: () -> Unit) {
    val open = rememberOpenUrl()
    AboutScaffold(R.string.about_acknowledgements, onBack) {
        items(libraries.size) { i ->
            val lib = libraries[i]
            Row(lib.name, "${lib.license} · ${lib.url.removePrefix("https://")}", Icons.Rounded.Description, groupedShape(i, libraries.size), external = true) { open(lib.url) }
        }
    }
}

/** Shared scaffold: collapsing top bar, back arrow (the nav graph provides predictive back). */
@Composable
private fun AboutScaffold(@StringRes title: Int, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = readableGutter(), end = readableGutter(), top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Dimens.xxl),
            verticalArrangement = Arrangement.spacedBy(Dimens.xxs),
            content = content
        )
    }
}

@Composable
private fun Row(title: String, subtitle: String, icon: ImageVector, shape: androidx.compose.ui.graphics.Shape, external: Boolean = false, onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        trailingContent = if (external) ({ Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) else null,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clip(shape).let { if (onClick != null) it.clickable(onClick = onClick) else it }
    )
}
