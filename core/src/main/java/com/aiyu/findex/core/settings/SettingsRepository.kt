package com.aiyu.findex.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aiyu.findex.core.model.AppSettings
import com.aiyu.findex.core.model.SortBy
import com.aiyu.findex.core.model.SortSpec
import com.aiyu.findex.core.model.ThemeMode
import com.aiyu.findex.core.update.UpdateChannel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

/** App configuration only. Records and lists belong in the Room database. */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val showHidden = booleanPreferencesKey("show_hidden")
        val gridView = booleanPreferencesKey("grid_view")
        val sortBy = stringPreferencesKey("sort_by")
        val sortAscending = booleanPreferencesKey("sort_ascending")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val autoUpdate = booleanPreferencesKey("auto_update")
        val updateChannel = stringPreferencesKey("update_channel")
        val lastNotifiedUpdateVersion = stringPreferencesKey("last_notified_update_version")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.themeMode].toEnum(ThemeMode.SYSTEM),
            dynamicColor = p[Keys.dynamicColor] ?: true,
            showHidden = p[Keys.showHidden] ?: false,
            gridView = p[Keys.gridView] ?: false,
            sort = SortSpec(p[Keys.sortBy].toEnum(SortBy.NAME), p[Keys.sortAscending] ?: true),
            onboardingDone = p[Keys.onboardingDone] ?: false,
            autoUpdate = p[Keys.autoUpdate] ?: true,
            updateChannel = p[Keys.updateChannel]?.let { n -> UpdateChannel.entries.firstOrNull { it.name == n } }
        )
    }

    val lastNotifiedUpdateVersion: Flow<String?> = context.dataStore.data.map { it[Keys.lastNotifiedUpdateVersion] }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.themeMode] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.dynamicColor] = enabled }
    suspend fun setShowHidden(enabled: Boolean) = edit { it[Keys.showHidden] = enabled }
    suspend fun setGridView(enabled: Boolean) = edit { it[Keys.gridView] = enabled }
    suspend fun setAutoUpdate(enabled: Boolean) = edit { it[Keys.autoUpdate] = enabled }
    suspend fun setUpdateChannel(channel: UpdateChannel) = edit { it[Keys.updateChannel] = channel.name }
    suspend fun setLastNotifiedUpdateVersion(version: String) = edit { it[Keys.lastNotifiedUpdateVersion] = version }
    suspend fun setOnboardingDone() = edit { it[Keys.onboardingDone] = true }
    suspend fun setSort(sort: SortSpec) = edit {
        it[Keys.sortBy] = sort.by.name
        it[Keys.sortAscending] = sort.ascending
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
}

