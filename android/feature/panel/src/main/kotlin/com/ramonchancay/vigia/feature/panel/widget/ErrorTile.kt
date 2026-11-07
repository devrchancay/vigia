package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ramonchancay.vigia.core.designsystem.component.PanelTile
import com.ramonchancay.vigia.core.model.ConfigError
import com.ramonchancay.vigia.core.model.WidgetConfig
import com.ramonchancay.vigia.feature.panel.R

/** Visible placeholder for a widget that cannot be rendered, so one bad entry never breaks the panel. */
@Composable
internal fun ErrorTile(title: String, message: String, modifier: Modifier = Modifier) {
    PanelTile(
        modifier = modifier
            .fillMaxSize()
            .border(1.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.large),
        title = title
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Error tile for params that do not decode or validate; names the first offending field. */
@Composable
internal fun InvalidParamsTile(widget: WidgetConfig, errors: List<ConfigError>, modifier: Modifier = Modifier) {
    val first = errors.firstOrNull()
    ErrorTile(
        title = widget.title ?: widget.id,
        message = stringResource(R.string.widget_invalid_param, first?.path ?: "params"),
        modifier = modifier
    )
}
