package com.aiyu.findex.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object OnboardingRoute
@Serializable object HomeRoute
@Serializable data class ExplorerRoute(val path: String)
@Serializable data class CategoryRoute(val category: String)
@Serializable object AnalyzerRoute
@Serializable object CleanerRoute
@Serializable object WhatsAppRoute
@Serializable object SettingsRoute
