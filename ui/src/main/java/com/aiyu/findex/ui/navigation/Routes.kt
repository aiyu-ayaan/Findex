package com.aiyu.findex.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object OnboardingRoute
@Serializable object HomeRoute
/** [path] opens or focuses a tab there; null resumes the open tabs. */
@Serializable data class ExplorerRoute(val path: String? = null)
@Serializable data class CategoryRoute(val category: String)
@Serializable object AnalyzerRoute
@Serializable object CleanerRoute
@Serializable object WhatsAppRoute
@Serializable object SettingsRoute
@Serializable object RecycleBinRoute
@Serializable object TabsRoute
@Serializable object SearchRoute
@Serializable object DuplicatesRoute
/** Browses the contents of the archive at [path] without extracting it. */
@Serializable data class ArchiveRoute(val path: String)
@Serializable object NetworkRoute
@Serializable object ShareRoute
@Serializable object AboutRoute
@Serializable object AcknowledgementsRoute
@Serializable object UpdatesRoute

// Next features (see development/docs/NEXT_FEATURES_DESIGN.md §0.4). Path lists travel via SelectionHandoff tokens.
@Serializable data class BatchRenameRoute(val token: String)
/** [importToken] set: unlock, then confirm and import the handed-off paths. */
@Serializable data class VaultRoute(val importToken: String? = null)
@Serializable object LargeFilesRoute
/** Dialog destination. */
@Serializable data class ImageCompressRoute(val token: String)
@Serializable data class TextViewerRoute(val path: String)
@Serializable data class PdfViewerRoute(val path: String)
@Serializable data class AudioPlayerRoute(val path: String)
@Serializable object FavoritesRoute
@Serializable data class TaggedFilesRoute(val tagId: Long)
@Serializable object RecentChangesRoute
@Serializable object TelegramRoute
@Serializable object InstagramRoute
@Serializable object AppCleanerRoute
/** [path] opens the first pane there; null starts on primary storage. */
@Serializable data class DualPaneRoute(val path: String? = null)
