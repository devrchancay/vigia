package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramonchancay.vigia.core.designsystem.component.PanelTile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal const val CLOCK_TIME_TAG = "clock_time"

/**
 * Time and date, sized to fill the tile. [now] is read here, not by the caller, so each tick recomposes only
 * this widget.
 */
@Composable
internal fun ClockWidget(now: State<Instant?>, zone: ZoneId, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val timeFormatter = remember(locale) { DateTimeFormatter.ofPattern("HH:mm:ss", locale) }
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    val local = now.value?.atZone(zone)

    PanelTile(modifier = modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = local?.let(timeFormatter::format).orEmpty(),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 160.sp),
                modifier = Modifier.fillMaxWidth().weight(1f).testTag(CLOCK_TIME_TAG)
            )
            Text(
                text = local?.let(dateFormatter::format).orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
