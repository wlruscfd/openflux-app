package org.openflux.app.ui.deploy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.openflux.app.R
import org.openflux.app.data.UsageDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayLabelFormat = DateTimeFormatter.ofPattern("MM-dd")

fun zeroFilledUsage(days: List<UsageDay>, count: Int): List<UsageDay> {
    if (count <= 0) return days
    val byDay = days.associateBy { it.day }
    val today = LocalDate.now()
    return (0 until count).map { back ->
        val date = today.minusDays((count - 1 - back).toLong())
        byDay[date.toString()] ?: UsageDay(date.toString(), 0, 0, 0)
    }
}

@Composable
fun DeployUsageTab(viewModel: DeployServerDetailViewModel) {
    val usage by viewModel.usage.collectAsState()
    val keyUsage by viewModel.keyUsage.collectAsState()
    val summary by viewModel.usageSummary.collectAsState()
    val error by viewModel.usageError.collectAsState()
    val days by viewModel.usageDays.collectAsState()
    val keyId by viewModel.usageKeyId.collectAsState()
    val keys by viewModel.keys.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90).forEach { option ->
                FilterChip(
                    selected = days == option,
                    onClick = { viewModel.setUsageDays(option) },
                    label = { Text("$option ${stringResource(R.string.deploy_usage_days)}") },
                )
            }
        }

        summary?.let { s ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    UsageRow(stringResource(R.string.deploy_usage_total), formatBytes(s.totalBytesSent))
                    UsageRow(stringResource(R.string.deploy_usage_today), formatBytes(s.todayBytesSent))
                    UsageRow(stringResource(R.string.deploy_usage_keys), "${s.enabledKeys}/${s.totalKeys}")
                    UsageRow(stringResource(R.string.deploy_usage_nodes_online), s.onlineNodes.toString())
                    if (s.overQuotaKeys > 0) {
                        UsageRow(stringResource(R.string.deploy_usage_over_quota), s.overQuotaKeys.toString())
                    }
                }
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        if (keys.isNotEmpty()) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = keyId == null,
                    onClick = { viewModel.selectUsageKey("") },
                    label = { Text(stringResource(R.string.deploy_usage_all_keys)) },
                )
                keys.forEach { k ->
                    FilterChip(
                        selected = keyId == k.id,
                        onClick = { viewModel.selectUsageKey(k.id) },
                        label = { Text(k.label.ifBlank { k.id.take(8) }) },
                    )
                }
            }
        }

        val series = zeroFilledUsage(if (keyId.isNullOrBlank()) usage else keyUsage, days)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                UsageBarChart(series, Modifier.fillMaxWidth().height(160.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    series.firstOrNull()?.let {
                        Text(runCatching { LocalDate.parse(it.day).format(dayLabelFormat) }.getOrDefault(it.day), style = MaterialTheme.typography.labelSmall)
                    }
                    series.lastOrNull()?.let {
                        Text(runCatching { LocalDate.parse(it.day).format(dayLabelFormat) }.getOrDefault(it.day), style = MaterialTheme.typography.labelSmall)
                    }
                }
                val total = series.sumOf { it.totalBytes }
                Text(
                    stringResource(R.string.deploy_usage_total_in_period, formatBytes(total)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        if (series.all { it.totalBytes == 0L }) {
            Text(
                stringResource(R.string.deploy_usage_no_data_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun UsageRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun UsageBarChart(series: List<UsageDay>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        if (series.isEmpty()) return@Canvas
        val peak = series.maxOf { it.totalBytes }.coerceAtLeast(1L)
        val slot = size.width / series.size
        val barWidth = (slot * 0.7f).coerceAtLeast(1f)
        series.forEachIndexed { index, day ->
            val x = index * slot + (slot - barWidth) / 2f
            val ratio = day.totalBytes.toFloat() / peak.toFloat()
            val barHeight = (size.height * ratio).coerceAtLeast(if (day.totalBytes > 0) 2f else 1f)
            val top = size.height - barHeight
            drawLine(
                color = if (day.totalBytes > 0) barColor else emptyColor,
                start = Offset(x, top),
                end = Offset(x + barWidth, top),
                strokeWidth = barHeight.coerceAtLeast(2f),
                cap = StrokeCap.Butt,
            )
        }
    }
}

@Composable
private fun UsageSparkline(series: List<UsageDay>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        if (series.size < 2) return@Canvas
        val peak = series.maxOf { it.totalBytes }.coerceAtLeast(1L)
        val stepX = size.width / (series.size - 1)
        val path = Path()
        series.forEachIndexed { index, day ->
            val x = index * stepX
            val y = size.height - (day.totalBytes.toFloat() / peak.toFloat()) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (value >= 100 || unit == 0) "${value.toInt()} ${units[unit]}"
    else String.format("%.1f %s", value, units[unit])
}
