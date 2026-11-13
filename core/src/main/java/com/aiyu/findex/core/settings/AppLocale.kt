package com.aiyu.findex.core.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Per-app language. API 33+ hands it to the system [LocaleManager] (which also powers Settings → App languages);
 * below that the choice is kept in prefs and applied in each Context's attachBaseContext via [wrap].
 * The empty tag means "follow the system".
 */
object AppLocale {
    /** Must match the values-* folders; the manifest's locale list is generated from those. */
    val tags = listOf(
        "en", "hi", "es", "pt-BR", "id", "ar", "fr", "ru", "de",
        "tr", "ja", "ko", "it", "vi", "th", "zh-CN", "zh-TW", "pl", "bn",
        "ta", "te", "mr", "gu", "kn", "ml", "pa", "ur",
    )

    private const val PREFS = "app_locale"
    private const val KEY = "tag"

    fun current(context: Context): String = if (Build.VERSION.SDK_INT >= 33) {
        val applied = context.getSystemService(LocaleManager::class.java).applicationLocales
        if (applied.isEmpty) "" else tags.firstOrNull {
            Locale.forLanguageTag(it).language == applied[0].language &&
                (it.length == 2 || Locale.forLanguageTag(it).country == applied[0].country)
        } ?: ""
    } else {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
    }

    fun set(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            // The system recreates the activity itself.
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
            context.findActivity()?.recreate()
        }
    }

    /** Only does anything below API 33; the system applies the locale itself from there. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }

    /** A language's own name, e.g. "Español", so it is readable whatever the current UI language is. */
    fun nativeName(tag: String): String =
        Locale.forLanguageTag(tag).let { it.getDisplayName(it).replaceFirstChar { c -> c.titlecase(it) } }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
