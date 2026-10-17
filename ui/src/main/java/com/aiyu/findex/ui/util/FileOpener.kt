package com.aiyu.findex.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.aiyu.findex.ui.R
import java.io.File

private fun Context.uriFor(file: File) = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

private fun mimeOf(file: File) =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

/** Opens a file in another app. @return false when no app can handle it. */
fun Context.openFile(path: String): Boolean {
    val file = File(path)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriFor(file), mimeOf(file))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_SHORT).show()
        false
    }
}

fun Context.shareFiles(paths: List<String>) {
    val files = paths.map(::File).filter { it.isFile }
    if (files.isEmpty()) return
    val uris = ArrayList(files.map { uriFor(it) })
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first()).setType(mimeOf(files.first()))
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).setType("*/*")
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
