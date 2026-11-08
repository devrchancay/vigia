package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramonchancay.vigia.core.designsystem.component.PanelTile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal const val CLOCK_TIME_TAG = "clock_time"

/** Widest possible time in a monospace font; every "HH:mm:ss" value has the same width. */
private const val TIME_TEMPLATE = "88:88:88"
private const val COMPACT_TIME_TEMPLATE = "88:88"

/** Tiles shorter than this (about one row of the default grid) use the one-line layouts. */
internal val COMPACT_BELOW = 140.dp
private val MIN_FONT_SIZE = 16.sp
private val MAX_FONT_SIZE = 200.sp

/**
 * Time and date, sized to fill the tile. [now] is read here, not by the caller, so each tick recomposes only
 * this widget. The font size is computed once per tile size from [TIME_TEMPLATE], so a tick only lays out one
 * line of text (auto-sizing on every change cost ~19 ms per frame on a Helio G25).
 */
@Composable
internal fun ClockWidget(now: State<Instant?>, minute: State<Long>, zone: ZoneId, modifier: Modifier = Modifier) {
    PanelTile(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // A short tile (one grid row) gets one line without seconds; a tall one gets the full clock.
            if (maxHeight < COMPACT_BELOW) CompactClock(minute, zone) else FullClock(now, zone)
        }
    }
}

@Composable
private fun FullClock(now: State<Instant?>, zone: ZoneId) {
    val locale = LocalConfiguration.current.locales[0]
    val timeFormatter = remember(locale) { DateTimeFormatter.ofPattern("HH:mm:ss", locale) }
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FittedTime(
            text = now.value?.atZone(zone)?.let(timeFormatter::format).orEmpty(),
            template = TIME_TEMPLATE,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        // The date only changes at midnight; formatting it here does not recompose the Text when equal.
        Text(
            text = now.value?.atZone(zone)?.let(dateFormatter::format).orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** Reads the per-minute state, so this recomposes once a minute instead of every second. */
@Composable
private fun CompactClock(minute: State<Long>, zone: ZoneId) {
    val locale = LocalConfiguration.current.locales[0]
    val timeFormatter = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEE d MMM", locale) }
    val local = minute.value.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(zone) }
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FittedTime(
            text = local?.let(timeFormatter::format).orEmpty(),
            template = COMPACT_TIME_TEMPLATE,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        Text(
            text = local?.let(dateFormatter::format).orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** Time text sized once per available space from a monospace [template] of the widest value. */
@Composable
private fun FittedTime(text: String, template: String, modifier: Modifier) {
    val style = MaterialTheme.typography.displayLarge
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        val measurer = rememberTextMeasurer(cacheSize = 0)
        val density = LocalDensity.current
        val fontSize =
            remember(constraints, style, density, template) { fitFontSize(measurer, style, constraints, template) }
        Text(
            text = text,
            style = style.copy(fontSize = fontSize, lineHeight = fontSize),
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(CLOCK_TIME_TAG)
        )
    }
}

/** Largest font size (binary search, 1 sp precision) at which [template] fits in [constraints]. */
private fun fitFontSize(
    measurer: TextMeasurer,
    style: TextStyle,
    constraints: Constraints,
    template: String
): TextUnit {
    if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) return style.fontSize
    var low = MIN_FONT_SIZE.value
    var high = MAX_FONT_SIZE.value
    while (high - low > 1f) {
        val mid = (low + high) / 2
        val size = mid.sp
        val result = measurer.measure(
            text = template,
            style = style.copy(fontSize = size, lineHeight = size),
            maxLines = 1,
            softWrap = false
        )
        val fits = result.size.width <= constraints.maxWidth && result.size.height <= constraints.maxHeight
        if (fits) low = mid else high = mid
    }
    return low.sp
}
