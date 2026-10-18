package com.aiyu.findex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import coil3.compose.AsyncImage
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.theme.tone
import androidx.compose.runtime.remember

private val IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "svg", "avif")
private val VIDEO = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts")
private val AUDIO = setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "opus", "amr", "mid")
private val DOCS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "odt", "ods", "odp", "md", "epub", "json", "xml", "html")
private val ARCHIVES = setOf("zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz")

fun categoryOf(extension: String): FileCategory? = when (extension.lowercase()) {
    in IMAGE -> FileCategory.PHOTOS
    in VIDEO -> FileCategory.VIDEOS
    in AUDIO -> FileCategory.AUDIO
    in DOCS -> FileCategory.DOCUMENTS
    in ARCHIVES -> FileCategory.ARCHIVES
    "apk" -> FileCategory.APKS
    else -> null
}

val FileCategory.icon: ImageVector
    get() = when (this) {
        FileCategory.PHOTOS -> Icons.Rounded.Image
        FileCategory.VIDEOS -> Icons.Rounded.Movie
        FileCategory.AUDIO -> Icons.Rounded.MusicNote
        FileCategory.DOCUMENTS -> Icons.Rounded.Description
        FileCategory.DOWNLOADS -> Icons.Rounded.Download
        FileCategory.ARCHIVES -> Icons.Rounded.FolderZip
        FileCategory.APKS -> Icons.Rounded.Android
    }

/** One shape for every category so the tiles read as an even set; color and glyph tell them apart. */
val CategoryShape: RoundedPolygon = MaterialShapes.Cookie9Sided

val FileCategory.titleRes: Int
    get() = when (this) {
        FileCategory.PHOTOS -> R.string.category_photos
        FileCategory.VIDEOS -> R.string.category_videos
        FileCategory.AUDIO -> R.string.category_audio
        FileCategory.DOCUMENTS -> R.string.category_documents
        FileCategory.DOWNLOADS -> R.string.category_downloads
        FileCategory.ARCHIVES -> R.string.category_archives
        FileCategory.APKS -> R.string.category_apks
    }

/**
 * Leading visual for a file row: a real thumbnail for images/videos, otherwise a
 * category-toned shape icon. Folders use the primary container.
 */
@Composable
fun FileThumbnail(
    path: String,
    isDirectory: Boolean,
    extension: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    /** Part of the thumbnail cache key; pass the file's date so edits refresh the thumbnail. */
    lastModified: Long = 0L
) {
    if (isDirectory) {
        ShapeIcon(
            icon = Icons.Rounded.Folder,
            shape = MaterialShapes.Square,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            size = size,
            iconSize = size / 2,
            modifier = modifier
        )
        return
    }
    val category = categoryOf(extension)
    if (category == FileCategory.PHOTOS || category == FileCategory.VIDEOS) {
        val tone = category.tone
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(MaterialShapes.Square.toShape())
                .background(tone.container)
        ) {
            AsyncImage(
                // Cached 256px thumbnail instead of decoding the full photo / a video frame on every bind.
                model = remember(path, lastModified) { Thumb(path, lastModified, isVideo = category == FileCategory.VIDEOS) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        }
        return
    }
    if (category == null) {
        ShapeIcon(
            icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
            shape = MaterialShapes.Square,
            container = MaterialTheme.colorScheme.surfaceContainerHighest,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
            size = size,
            iconSize = size / 2,
            modifier = modifier
        )
    } else {
        ShapeIcon(
            icon = category.icon,
            shape = MaterialShapes.Square,
            container = category.tone.container,
            content = category.tone.onContainer,
            size = size,
            iconSize = size / 2,
            modifier = modifier
        )
    }
}
