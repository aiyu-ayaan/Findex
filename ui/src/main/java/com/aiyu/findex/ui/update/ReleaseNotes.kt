package com.aiyu.findex.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.aiyu.findex.ui.theme.Dimens

private val INLINE = Regex("""\*\*(.+?)\*\*|`(.+?)`""")

/** A GitHub release body (the CHANGELOG entry) drawn: headings, bullets, **bold** and `code`. */
@Composable
fun ReleaseNotes(notes: String, modifier: Modifier = Modifier) {
    val lines = remember(notes) { notes.lines().map { it.trimEnd() }.filter { it.isNotBlank() } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.xs)) {
        lines.forEach { line ->
            val text = line.trimStart()
            when {
                text.startsWith("#") -> Text(
                    inline(text.trimStart('#').trim()),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = Dimens.sm)
                )
                text.startsWith("- ") || text.startsWith("* ") -> Row {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(Dimens.lg))
                    Text(inline(text.drop(2)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> Text(inline(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    INLINE.findAll(text).forEach { m ->
        append(text.substring(last, m.range.first))
        val bold = m.groups[1]?.value
        if (bold != null) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
        else withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(m.groups[2]!!.value) }
        last = m.range.last + 1
    }
    append(text.substring(last))
}
