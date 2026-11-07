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
        WidgetTypes.CLOCK -> ClockWidget(now = data.now, zone = data.zone, modifier = modifier)

        WidgetTypes.NOTE -> NoteWidget(widget = widget, modifier = modifier)

        WidgetTypes.DEVICE -> DeviceWidget(widget = widget, device = data.device, modifier = modifier)

        else -> ErrorTile(
            title = widget.title ?: widget.id,
            message = stringResource(R.string.widget_unsupported_type, widget.type),
            modifier = modifier
        )
    }
}
