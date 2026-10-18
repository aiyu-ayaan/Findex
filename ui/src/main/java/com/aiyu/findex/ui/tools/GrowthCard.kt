package com.aiyu.findex.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.core.db.StorageSnapshotEntity
import com.aiyu.findex.core.history.StorageHistoryRepository
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.util.formatSize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import kotlin.math.abs

@HiltViewModel
class GrowthViewModel @Inject constructor(private val history: StorageHistoryRepository) : ViewModel() {
    fun history(volume: String, days: Int): Flow<List<StorageSnapshotEntity>> = history.history(volume, days)
}

private val ranges = listOf(30 to R.string.growth_range_30d, 90 to R.string.growth_range_90d, 365 to R.string.growth_range_1y)

/** Used bytes over time for [volumePath], from the daily snapshots. */
@Composable
fun GrowthCard(volumePath: String, modifier: Modifier = Modifier, viewModel: GrowthViewModel = hiltViewModel()) {
    var days by rememberSaveable { mutableIntStateOf(30) }
    val points by remember(volumePath, days) { viewModel.history(volumePath, days) }.collectAsStateWithLifecycle(emptyList())
    val line = MaterialTheme.colorScheme.primary
    val fillTop = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val fillBottom = MaterialTheme.colorScheme.primary.copy(alpha = 0f)

    Card(
        shape = MaterialTheme.shapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Dimens.xl), verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
            Text(stringResource(R.string.growth_title), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                ranges.forEach { (d, label) -> FilterChip(days == d, { days = d }, { Text(stringResource(label)) }) }
            }
            if (points.size < 2) {
                Text(stringResource(R.string.growth_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val delta = points.last().usedBytes - points.first().usedBytes
                Text(
                    stringResource(if (delta >= 0) R.string.growth_delta_up else R.string.growth_delta_down, formatSize(abs(delta))),
                    style = MaterialTheme.typography.headlineSmall
                )
                val min = points.minOf { it.usedBytes }
                val span = (points.maxOf { it.usedBytes } - min).coerceAtLeast(1L).toFloat()
                Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                    val stroke = 3.dp.toPx()
                    val pad = stroke
                    val x = { i: Int -> pad + (size.width - 2 * pad) * i / (points.size - 1) }
                    val y = { v: Long -> size.height - pad - (size.height - 2 * pad) * (v - min) / span }
                    val path = Path()
                    points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(i), y(p.usedBytes)) else path.lineTo(x(i), y(p.usedBytes)) }
                    val area = Path().apply {
                        addPath(path); lineTo(x(points.size - 1), size.height); lineTo(x(0), size.height); close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(fillTop, fillBottom)))
                    drawPath(path, line, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawCircle(line, stroke * 1.6f, Offset(x(points.size - 1), y(points.last().usedBytes)))
                }
            }
        }
    }
}
