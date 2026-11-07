package com.ramonchancay.vigia.feature.panel.widget

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import com.ramonchancay.vigia.core.device.DeviceStatus
import java.time.Instant
import java.time.ZoneId

/**
 * Live values widgets read. Each is a [State] read inside the widget that needs it, so a change recomposes
 * only that widget, never the grid.
 */
@Immutable
internal class WidgetData(val now: State<Instant?>, val device: State<DeviceStatus?>, val zone: ZoneId)
