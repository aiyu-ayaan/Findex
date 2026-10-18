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
@Serializable object AboutRoute
@Serializable object AcknowledgementsRoute
