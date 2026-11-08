package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramonchancay.vigia.core.designsystem.component.PanelTile
import com.ramonchancay.vigia.core.designsystem.theme.StatusColors
import com.ramonchancay.vigia.core.model.MetricStatus
import com.ramonchancay.vigia.core.model.WidgetConfig
import com.ramonchancay.vigia.core.model.WidgetParams
import com.ramonchancay.vigia.feature.panel.R
import java.text.NumberFormat

internal const val METRIC_VALUE_TAG = "metric_value"

/** Follower count of an X account in big digits, with the change over the last day. */
@Composable
internal fun XFollowersWidget(widget: WidgetConfig, data: WidgetData, modifier: Modifier = Modifier) {
    val params = remember(widget.params) { WidgetParams.xFollowers(widget) }
    val value = params.value
    if (value == null) {
        InvalidParamsTile(widget, params.errors, modifier)
        return
    }
    val metric = data.metrics.value[MetricStatus.xFollowersKey(value.username)]
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    PanelTile(modifier = modifier.fillMaxSize(), title = widget.title ?: "X · @${value.username}") {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val number = metric?.value?.let(format::format)
            val change = metric?.changeLastDay?.let { delta ->
                val sign = if (delta > 0) "+" else ""
                stringResource(R.string.metric_change_day, sign + format.format(delta)) to when {
                    delta > 0 -> StatusColors.Ok
                    delta < 0 -> StatusColors.Error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            }
            val footer = metric?.error ?: change?.first ?: stringResource(R.string.metric_no_change_yet)
            val footerColor = when {
                metric?.error != null -> StatusColors.Warn
                change != null -> change.second
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            if (maxHeight < COMPACT_BELOW) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        number ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag(METRIC_VALUE_TAG)
                    )
                    Text(footer, style = MaterialTheme.typography.bodyMedium, color = footerColor, maxLines = 1)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        number ?: if (metric == null) stringResource(R.string.source_pending) else "—",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag(METRIC_VALUE_TAG)
                    )
                    Text(footer, style = MaterialTheme.typography.bodyMedium, color = footerColor)
                }
            }
        }
    }
}
