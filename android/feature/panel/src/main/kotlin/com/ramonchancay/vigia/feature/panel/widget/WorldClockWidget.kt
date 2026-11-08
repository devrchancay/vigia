package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ramonchancay.vigia.core.designsystem.component.PanelTile
import com.ramonchancay.vigia.core.model.WidgetConfig
import com.ramonchancay.vigia.core.model.WidgetParams
import com.ramonchancay.vigia.core.model.WorldZone
import com.ramonchancay.vigia.feature.panel.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal const val WORLD_ROW_TAG = "world_row_"

/** Time in other cities, computed on the device (no network). Recomposes once a minute. */
@Composable
internal fun WorldClockWidget(widget: WidgetConfig, data: WidgetData, modifier: Modifier = Modifier) {
    val params = remember(widget.params) { WidgetParams.worldClock(widget) }
    val value = params.value
    if (value == null) {
        InvalidParamsTile(widget, params.errors, modifier)
        return
    }
    val minute = data.minute.value
    val rows = value.zones.map { cityTime(it, minute, data.zone) }
    PanelTile(modifier = modifier.fillMaxSize(), title = widget.title ?: stringResource(R.string.world_clock_title)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxHeight < COMPACT_BELOW) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rows.forEach { CompactCity(it) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { rows.forEach { CityRow(it) } }
            }
        }
    }
}

/** [dayOffset] is +1/-1 when that city is already on another calendar day than the panel. */
internal data class CityTime(val label: String, val time: String, val weekday: String, val dayOffset: Long)

@Composable
private fun cityTime(zone: WorldZone, nowMillis: Long, localZone: ZoneId): CityTime {
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val weekday = remember(locale) { DateTimeFormatter.ofPattern("EEE", locale) }
    if (nowMillis <= 0) return CityTime(zone.label, "--:--", "", 0)
    val instant = Instant.ofEpochMilli(nowMillis)
    val there = instant.atZone(ZoneId.of(zone.zone))
    val here = instant.atZone(localZone)
    val offset = ChronoUnit.DAYS.between(here.toLocalDate(), there.toLocalDate())
    return CityTime(zone.label, time.format(there), weekday.format(there), offset)
}

private fun CityTime.offsetLabel(): String = when {
    dayOffset > 0 -> "+$dayOffset"
    dayOffset < 0 -> "$dayOffset"
    else -> ""
}

@Composable
private fun CityRow(city: CityTime) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(WORLD_ROW_TAG + city.label),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            city.label,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            city.weekday,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(city.time, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        // Fixed width so times line up whether or not a city shows a day offset.
        Text(
            city.offsetLabel(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.width(24.dp)
        )
    }
}

@Composable
private fun CompactCity(city: CityTime) {
    Row(
        modifier = Modifier.testTag(WORLD_ROW_TAG + city.label),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            city.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(city.time, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (city.dayOffset != 0L) {
            Text(
                city.offsetLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
