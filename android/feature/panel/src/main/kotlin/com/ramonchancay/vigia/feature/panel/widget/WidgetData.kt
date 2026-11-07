package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import com.ramonchancay.vigia.core.device.DeviceStatus
import com.ramonchancay.vigia.core.model.UptimeStatus
import com.ramonchancay.vigia.core.model.WorkflowRunStatus
import java.time.Instant
import java.time.ZoneId

/**
 * Live values widgets read. Each is a [State] read inside the widget that needs it, so a change recomposes
 * only that widget, never the grid.
 */
@Immutable
internal class WidgetData(
    val now: State<Instant?>,
    val device: State<DeviceStatus?>,
    val uptime: State<Map<String, UptimeStatus>>,
    val workflows: State<Map<String, WorkflowRunStatus>>,
    val zone: ZoneId
) {
    /** Current time rounded down to the minute: relative times ("5m") recompose once a minute, not every tick. */
    val minute: State<Long> =
        derivedStateOf { (now.value?.toEpochMilli() ?: 0L) / MILLIS_PER_MINUTE * MILLIS_PER_MINUTE }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

/** Compact elapsed time: "now", "5m", "3h", "2d". Units are the same in English and Spanish. */
internal fun elapsed(fromMillis: Long, nowMillis: Long): String {
    val minutes = ((nowMillis - fromMillis) / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "<1m"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        else -> "${minutes / (60 * 24)}d"
    }
}
