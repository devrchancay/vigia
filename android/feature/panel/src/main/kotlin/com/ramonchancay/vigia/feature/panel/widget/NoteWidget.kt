package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.ramonchancay.vigia.core.designsystem.component.PanelTile
import com.ramonchancay.vigia.core.model.WidgetConfig
import com.ramonchancay.vigia.feature.panel.R
import kotlinx.serialization.json.JsonPrimitive

/** Short static text from `params.text`, for reminders. */
@Composable
internal fun NoteWidget(widget: WidgetConfig, modifier: Modifier = Modifier) {
    val text = remember(widget.params) { (widget.params["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content }
    if (text == null) {
        ErrorTile(
            title = widget.title ?: widget.id,
            message = stringResource(R.string.widget_missing_param, "text"),
            modifier = modifier
        )
        return
    }
    PanelTile(modifier = modifier.fillMaxSize(), title = widget.title) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, overflow = TextOverflow.Ellipsis)
    }
}
