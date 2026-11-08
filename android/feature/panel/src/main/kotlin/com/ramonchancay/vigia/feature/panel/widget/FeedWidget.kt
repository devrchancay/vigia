package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ramonchancay.vigia.core.designsystem.component.PanelTile
import com.ramonchancay.vigia.core.designsystem.theme.StatusColors
import com.ramonchancay.vigia.core.model.EventLevel
import com.ramonchancay.vigia.core.model.StoredEvent
import com.ramonchancay.vigia.core.model.WidgetConfig
import com.ramonchancay.vigia.core.model.WidgetParams
import com.ramonchancay.vigia.feature.panel.R

internal const val FEED_ROW_TAG = "feed_row_"

/** Newest events of the configured sources: level dot, title (and body if asked), source when mixed, age. */
@Composable
internal fun FeedWidget(widget: WidgetConfig, data: WidgetData, modifier: Modifier = Modifier) {
    val params = remember(widget.params) { WidgetParams.feed(widget) }
    val value = params.value
    if (value == null) {
        InvalidParamsTile(widget, params.errors, modifier)
        return
    }
    val minute = data.minute.value
    val items = data.events.value
        .filter { it.source in value.sources && it.expiresAtMillis > minute }
        .take(value.max)
    val showSource = value.sources.size > 1
    PanelTile(modifier = modifier.fillMaxSize(), title = widget.title ?: stringResource(R.string.feed_title)) {
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.feed_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEach { FeedRow(it, showSource, value.showBody, minute) }
        }
    }
}

@Composable
private fun FeedRow(event: StoredEvent, showSource: Boolean, showBody: Boolean, nowMillis: Long) {
    val color = when (event.level) {
        EventLevel.Info -> StatusColors.Unknown
        EventLevel.Ok -> StatusColors.Ok
        EventLevel.Warn -> StatusColors.Warn
        EventLevel.Error -> StatusColors.Error
    }
    Row(
        modifier = Modifier.fillMaxWidth().testTag(FEED_ROW_TAG + event.id),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        StatusDotTop(color)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                event.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showBody && event.body != null) {
                Text(
                    event.body.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            if (showSource) {
                "${event.source} · ${elapsed(
                    event.receivedAtMillis,
                    nowMillis
                )}"
            } else {
                elapsed(event.receivedAtMillis, nowMillis)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Status dot aligned with the first line of text. */
@Composable
private fun StatusDotTop(color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.layout.Box(Modifier.padding(top = 7.dp)) { StatusDot(color) }
}
