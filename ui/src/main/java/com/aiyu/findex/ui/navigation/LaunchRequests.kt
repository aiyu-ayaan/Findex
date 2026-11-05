package com.aiyu.findex.ui.navigation

/**
 * Intent contract for requests from outside the NavHost. MainActivity hands the intent itself to FindexApp
 * (per activity), so nothing here holds state.
 */
object LaunchRequests {
    const val ACTION_OPEN_FOLDER = "com.aiyu.findex.action.OPEN_FOLDER"
    const val EXTRA_FOLDER_PATH = "com.aiyu.findex.extra.FOLDER_PATH"
    const val ACTION_OPEN_SCREEN = "com.aiyu.findex.action.OPEN_SCREEN"
    /** An `AppShortcut` name: launcher shortcuts, the widget, notifications and the tile open screens through it. */
    const val EXTRA_SCREEN = "com.aiyu.findex.extra.SCREEN"
}
