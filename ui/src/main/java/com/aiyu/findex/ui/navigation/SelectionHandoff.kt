package com.aiyu.findex.ui.navigation

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-scoped hand-off of a path list to the next screen (routes hit the ~1 MB Bundle limit).
 * Lost on process death: the receiving screen pops with [com.aiyu.findex.ui.R.string.selection_expired].
 */
object SelectionHandoff {
    private val pending = ConcurrentHashMap<String, List<String>>()

    fun put(paths: List<String>): String = UUID.randomUUID().toString().also { pending[it] = paths }

    /** One-shot: a second take of the same token returns null. */
    fun take(token: String): List<String>? = pending.remove(token)
}
