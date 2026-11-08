package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ramonchancay.vigia.core.designsystem.component.PanelTile
import com.ramonchancay.vigia.core.designsystem.theme.StatusColors
import com.ramonchancay.vigia.core.model.StoredEvent
import com.ramonchancay.vigia.core.model.WidgetConfig
import com.ramonchancay.vigia.core.model.WidgetParams
import com.ramonchancay.vigia.feature.panel.R

internal const val AGENT_ROW_TAG = "agent_row_"

/** Agent session states, by the event `kind` the Claude Code hook sends. Order = display priority. */
internal enum class AgentState(val kind: String, val color: Color, val symbol: String) {
    WaitingPermission("waiting_permission", StatusColors.Error, "!"),
    Working("working", StatusColors.Warn, "●"),
    Done("done", StatusColors.Ok, "✓");

    companion object {
        fun of(kind: String?): AgentState = entries.firstOrNull { it.kind == kind } ?: Working
    }
}

/**
 * One row per agent session (event key): sessions waiting for permission first, then working, then done.
 * Within a state, the most recent change first.
 */
@Composable
internal fun AgentsWidget(widget: WidgetConfig, data: WidgetData, modifier: Modifier = Modifier) {
    val params = remember(widget.params) { WidgetParams.agents(widget) }
    val value = params.value
    if (value == null) {
        InvalidParamsTile(widget, params.errors, modifier)
        return
    }
    val minute = data.minute.value
    val sessions = data.events.value
        .filter { it.source == value.source && it.expiresAtMillis > minute }
        .sortedWith(compareBy<StoredEvent> { AgentState.of(it.kind).ordinal }.thenByDescending { it.receivedAtMillis })
        .take(value.max)
    PanelTile(modifier = modifier.fillMaxSize(), title = widget.title ?: stringResource(R.string.agents_title)) {
        if (sessions.isEmpty()) {
            Text(
                stringResource(R.string.agents_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sessions.forEach { AgentRow(it, minute) }
        }
    }
}

@Composable
private fun AgentRow(event: StoredEvent, nowMillis: Long) {
    val state = AgentState.of(event.kind)
    Row(
        modifier = Modifier.fillMaxWidth().testTag(AGENT_ROW_TAG + event.key.orEmpty()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(state.symbol, style = MaterialTheme.typography.titleMedium, color = state.color)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                event.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (state == AgentState.WaitingPermission) FontWeight.SemiBold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            event.body?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            elapsed(event.receivedAtMillis, nowMillis),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
