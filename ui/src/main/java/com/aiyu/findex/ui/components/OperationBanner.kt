package com.aiyu.findex.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.aiyu.findex.core.model.ActiveOperation
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.di.operationCount
import com.aiyu.findex.ui.di.operationTitleRes
import com.aiyu.findex.ui.theme.Dimens

/** Live progress of the running background operation, with cancel. */
@Composable
fun OperationBanner(
    active: ActiveOperation,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = active.progress.progressFraction,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "opProgress"
    )
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(start = Dimens.lg, end = Dimens.xs, top = Dimens.xs, bottom = Dimens.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.xxs)) {
                    Text(
                        text = stringResource(operationTitleRes(active.operation), operationCount(active.operation)),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (active.progress.currentItemName.isNotEmpty()) {
                        Text(
                            text = active.progress.currentItemName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel))
                }
            }
            if (active.progress.isIndeterminate) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth().padding(end = Dimens.md))
            } else {
                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(end = Dimens.md))
            }
        }
    }
}
