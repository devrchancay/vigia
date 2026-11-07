package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ramonchancay.vigia.core.designsystem.component.PanelTile

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
