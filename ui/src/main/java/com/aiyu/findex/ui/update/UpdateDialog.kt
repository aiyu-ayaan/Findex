package com.aiyu.findex.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aiyu.findex.core.update.Release
import com.aiyu.findex.core.update.UpdateState
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.theme.Dimens

/**
 * App-wide: hosted once in FindexApp, so a new release is offered on whatever screen is open,
 * with its release notes. Dismissing (or the back gesture) hides it until the next check.
 */
@Composable
fun UpdateDialog(state: UpdateState, installed: String, canInstall: Boolean, onInstall: () -> Unit, onDismiss: () -> Unit) {
    if (state is UpdateState.Failed) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.update_failed)) },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } }
        )
        return
    }
    val release: Release = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Ready -> state.release
        else -> return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_title)) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.update_subtitle, release.name, installed), style = MaterialTheme.typography.bodyMedium)
                if (release.notes.isNotBlank()) {
                    Text(
                        stringResource(R.string.update_whats_new),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = Dimens.md, bottom = Dimens.xs)
                    )
                    ReleaseNotes(release.notes, Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()))
                }
                if (state is UpdateState.Downloading) {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().padding(top = Dimens.lg))
                    Text(stringResource(R.string.update_downloading, (state.progress * 100).toInt()), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Dimens.xs))
                }
            }
        },
        confirmButton = {
            if (state !is UpdateState.Downloading) {
                TextButton(onClick = onInstall) {
                    Text(stringResource(if (state is UpdateState.Ready && !canInstall) R.string.update_allow_install else R.string.update_install))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_not_now)) } }
    )
}
