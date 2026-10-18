package com.aiyu.findex.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.aiyu.findex.core.network.SmbContentProvider
import com.aiyu.findex.core.network.SmbPath
import com.aiyu.findex.ui.R
import java.io.File

private fun Context.uriFor(path: String, size: Long = -1L): Uri {
    SmbPath.parse(path)?.let { (serverId, inner) ->
        return SmbContentProvider.uriFor(this, serverId, inner, size)
    }
    return FileProvider.getUriForFile(this, "$packageName.fileprovider", File(path))
}

/** Opens a local or network file in the default app (streaming over SMB when remote). @return false when no app can handle it. */
fun Context.openFile(path: String, size: Long = -1L): Boolean {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriFor(path, size), SmbContentProvider.mimeTypeForName(path))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.error_no_app, Toast.LENGTH_SHORT).show()
        false
    }
}

fun Context.shareFiles(paths: List<String>) {
    val valid = paths.filter { SmbPath.parse(it) != null || File(it).isFile }
    if (valid.isEmpty()) return
    val uris = ArrayList(valid.map { uriFor(it) })
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_STREAM, uris.first())
            .setType(SmbContentProvider.mimeTypeForName(valid.first()))
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            .setType("*/*")
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
