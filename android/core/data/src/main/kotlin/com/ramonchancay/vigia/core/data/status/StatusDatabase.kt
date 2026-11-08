package com.ramonchancay.vigia.core.data.status

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ramonchancay.vigia.core.data.events.EventDao
import com.ramonchancay.vigia.core.data.events.EventEntity

/** Polled source states and pushed events. Version 2 adds `events`, 3 `weather_state`, 4 the metric tables. */
@Database(
    entities = [
        UptimeStateEntity::class,
        WorkflowStateEntity::class,
        EventEntity::class,
        WeatherStateEntity::class,
        MetricStateEntity::class,
        MetricPointEntity::class
    ],
    version = 4,
    exportSchema = true
)
internal abstract class StatusDatabase : RoomDatabase() {
    abstract fun statusDao(): StatusDao

    abstract fun eventDao(): EventDao
}
