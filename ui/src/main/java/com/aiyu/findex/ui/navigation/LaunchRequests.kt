package com.aiyu.findex.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow

/** Requests from outside the NavHost, such as a pinned folder shortcut opening [folder]. */
object LaunchRequests {
    const val ACTION_OPEN_FOLDER = "com.aiyu.findex.action.OPEN_FOLDER"
    const val EXTRA_FOLDER_PATH = "com.aiyu.findex.extra.FOLDER_PATH"

    /**
     * A state, not a SharedFlow: a cold start emits before the NavHost collects, and a
     * replay-less SharedFlow would drop it. The NavHost resets it to null after navigating.
     */
    val folder = MutableStateFlow<String?>(null)
}
