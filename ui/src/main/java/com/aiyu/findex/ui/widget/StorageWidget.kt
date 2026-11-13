package com.aiyu.findex.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import android.view.View
import android.widget.RemoteViews
import com.aiyu.findex.core.db.BookmarkDao
import com.aiyu.findex.core.settings.AppLocale
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.shortcuts.AppShortcut
import com.aiyu.findex.ui.shortcuts.folderIntent
import com.aiyu.findex.ui.shortcuts.screenIntent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface StorageWidgetEntryPoint {
    fun bookmarks(): BookmarkDao
}

/** Home screen widget: internal storage use, a Clean button and up to four bookmarked folders (quick screens fill the rest). */
class StorageWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val views = build(context)
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val BOOKMARK_VIEWS = intArrayOf(R.id.widget_bookmark_0, R.id.widget_bookmark_1, R.id.widget_bookmark_2, R.id.widget_bookmark_3)

        /** Redraws every placed widget, e.g. after bookmarks change. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StorageWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, StorageWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }

        private suspend fun build(context: Context): RemoteViews {
            val localized = AppLocale.wrap(context)
            val views = RemoteViews(localized.packageName, R.layout.widget_storage)
            val root = Environment.getExternalStorageDirectory()
            val stat = StatFs(root.path)
            val total = stat.totalBytes
            val free = stat.availableBytes
            val percent = if (total > 0) ((total - free) * 100 / total).toInt() else 0
            views.setTextViewText(R.id.widget_percent, localized.getString(R.string.widget_used, percent))
            views.setTextViewText(
                R.id.widget_free,
                localized.getString(R.string.widget_free, Formatter.formatShortFileSize(localized, free), Formatter.formatShortFileSize(localized, total))
            )
            views.setProgressBar(R.id.widget_progress, 100, percent, false)
            views.setOnClickPendingIntent(R.id.widget_root, activity(localized, 0, localized.folderIntent(root.path)))
            views.setOnClickPendingIntent(R.id.widget_clean, activity(localized, 1, localized.screenIntent(AppShortcut.CLEAN)))

            // Bookmarked folders first (files stay in the app), then quick screens fill the free slots.
            val dao = EntryPointAccessors.fromApplication(context, StorageWidgetEntryPoint::class.java).bookmarks()
            val folders = runCatching { dao.observeAll().first() }.getOrDefault(emptyList())
                .filter { "://" in it.path || File(it.path).isDirectory }
                .map { it.name to localized.folderIntent(it.path) }
            val quick = listOf(
                localized.getString(R.string.category_downloads) to localized.screenIntent(AppShortcut.DOWNLOADS),
                localized.getString(R.string.nav_search) to localized.screenIntent(AppShortcut.SEARCH),
                localized.getString(R.string.nav_recent_changes) to localized.screenIntent(AppShortcut.RECENT)
            )
            val chips = (folders + quick).take(BOOKMARK_VIEWS.size)
            BOOKMARK_VIEWS.forEachIndexed { i, id ->
                val chip = chips.getOrNull(i)
                views.setViewVisibility(id, if (chip == null) View.GONE else View.VISIBLE)
                if (chip != null) {
                    views.setTextViewText(id, chip.first)
                    views.setOnClickPendingIntent(id, activity(context, 10 + i, chip.second))
                }
            }
            return views
        }

        private fun activity(context: Context, request: Int, intent: Intent?): PendingIntent? = intent?.let {
            PendingIntent.getActivity(context, request, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
