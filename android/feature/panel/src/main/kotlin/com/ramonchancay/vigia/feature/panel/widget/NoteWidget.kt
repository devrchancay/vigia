package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.ramonchancay.vigia.core.designsystem.component.PanelTile
import com.ramonchancay.vigia.core.model.WidgetConfig
import com.ramonchancay.vigia.core.model.WidgetParams

/** Short static text from `params.text`, for reminders. */
@Composable
internal fun NoteWidget(widget: WidgetConfig, modifier: Modifier = Modifier) {
    val params = remember(widget.params) { WidgetParams.note(widget) }
    val text = params.value?.text
    if (text == null) {
        InvalidParamsTile(widget, params.errors, modifier)
        return
    }
    PanelTile(modifier = modifier.fillMaxSize(), title = widget.title) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, overflow = TextOverflow.Ellipsis)
    }
}
