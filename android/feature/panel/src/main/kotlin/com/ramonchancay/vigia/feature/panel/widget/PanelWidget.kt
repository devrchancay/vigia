package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ramonchancay.vigia.core.model.WidgetConfig
import com.ramonchancay.vigia.feature.panel.R
import java.time.Instant
import java.time.ZoneId

/** Widget types this version can render. Anything else becomes an error tile. */
internal object WidgetTypes {
    const val CLOCK = "clock"
    const val NOTE = "note"
}

/** Renders one widget by its type; never throws for an unknown type or bad params. */
@Composable
internal fun PanelWidget(widget: WidgetConfig, now: State<Instant?>, zone: ZoneId, modifier: Modifier = Modifier) {
    when (widget.type) {
        WidgetTypes.CLOCK -> ClockWidget(now = now, zone = zone, modifier = modifier)

        WidgetTypes.NOTE -> NoteWidget(widget = widget, modifier = modifier)

        else -> ErrorTile(
            title = widget.title ?: widget.id,
            message = stringResource(R.string.widget_unsupported_type, widget.type),
            modifier = modifier
        )
    }
}
