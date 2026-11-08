package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ramonchancay.vigia.core.model.WidgetConfig
import com.ramonchancay.vigia.core.model.WidgetTypes
import com.ramonchancay.vigia.feature.panel.R

/** Renders one widget by its type; never throws for an unknown type or bad params. */
@Composable
internal fun PanelWidget(widget: WidgetConfig, data: WidgetData, modifier: Modifier = Modifier) {
    when (widget.type) {
        WidgetTypes.CLOCK -> ClockWidget(now = data.now, minute = data.minute, zone = data.zone, modifier = modifier)

        WidgetTypes.NOTE -> NoteWidget(widget = widget, modifier = modifier)

        WidgetTypes.DEVICE -> DeviceWidget(widget = widget, device = data.device, modifier = modifier)

        WidgetTypes.UPTIME -> UptimeWidget(widget = widget, data = data, modifier = modifier)

        WidgetTypes.GITHUB_ACTIONS -> GithubActionsWidget(widget = widget, data = data, modifier = modifier)

        WidgetTypes.AGENTS -> AgentsWidget(widget = widget, data = data, modifier = modifier)

        WidgetTypes.FEED -> FeedWidget(widget = widget, data = data, modifier = modifier)

        WidgetTypes.WORLD_CLOCK -> WorldClockWidget(widget = widget, data = data, modifier = modifier)

        WidgetTypes.WEATHER -> WeatherWidget(widget = widget, data = data, modifier = modifier)

        WidgetTypes.X_FOLLOWERS -> XFollowersWidget(widget = widget, data = data, modifier = modifier)

        WidgetTypes.WEB -> WebWidget(widget = widget, modifier = modifier)

        else -> ErrorTile(
            title = widget.title ?: widget.id,
            message = stringResource(R.string.widget_unsupported_type, widget.type),
            modifier = modifier
        )
    }
}
