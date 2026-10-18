package com.aiyu.findex.core.operation

import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.OperationResult

/**
 * Localized notification texts. `core` has no string resources (they all live in `ui`),
 * so the `ui` module binds an implementation through Hilt.
 */
interface OperationStrings {
    val channelName: String
    val channelDescription: String
    val cancel: String
    val updateChannelName: String
    val updateNotificationBody: String
    fun updateNotificationTitle(version: String): String
    fun title(operation: FileOperation): String
    fun progress(currentItem: String, percent: Int, queued: Int): String
    /** Short headline of a finished operation, e.g. "Copied 3 items". */
    fun resultTitle(result: OperationResult): String
    fun result(result: OperationResult): String
}
