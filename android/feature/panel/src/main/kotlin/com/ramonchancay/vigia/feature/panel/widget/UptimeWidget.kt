package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ramonchancay.vigia.core.designsystem.component.PanelTile
import com.ramonchancay.vigia.core.designsystem.theme.StatusColors
import com.ramonchancay.vigia.core.model.UptimeStatus
import com.ramonchancay.vigia.core.model.UptimeTarget
import com.ramonchancay.vigia.core.model.WidgetConfig
import com.ramonchancay.vigia.core.model.WidgetParams
import com.ramonchancay.vigia.feature.panel.R

internal const val UPTIME_ROW_TAG = "uptime_row_"

/** One row per target: traffic light, name, latency (or the failure) and how long it has been in that state. */
@Composable
internal fun UptimeWidget(widget: WidgetConfig, data: WidgetData, modifier: Modifier = Modifier) {
    val params = remember(widget.params) { WidgetParams.uptime(widget) }
    val value = params.value
    if (value == null) {
        InvalidParamsTile(widget, params.errors, modifier)
        return
    }
    val statuses = data.uptime.value
    val minute = data.minute.value
    PanelTile(modifier = modifier.fillMaxSize(), title = widget.title ?: stringResource(R.string.uptime_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            value.targets.forEach { target ->
                UptimeRow(target, statuses[target.url], slowMs = value.slowMs, nowMillis = minute)
            }
        }
    }
}

@Composable
private fun UptimeRow(target: UptimeTarget, status: UptimeStatus?, slowMs: Int, nowMillis: Long) {
    val color = when {
        status == null -> StatusColors.Unknown
        !status.up -> StatusColors.Error
        (status.latencyMs ?: 0) > slowMs -> StatusColors.Warn
        else -> StatusColors.Ok
    }
    val detail = when {
        status == null -> stringResource(R.string.source_pending)
        status.error != null -> status.error
        !status.up -> "HTTP ${status.statusCode}"
        else -> "${status.latencyMs} ms"
    }
    Row(
        modifier = Modifier.fillMaxWidth().testTag(UPTIME_ROW_TAG + target.name),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(color)
        Text(
            text = target.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = detail.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (status?.up == false) StatusColors.Error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        if (status != null && nowMillis > 0) {
            Text(
                text = elapsed(status.changedAtMillis, nowMillis),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun StatusDot(color: Color) {
    Box(Modifier.size(10.dp).background(color, CircleShape))
}
