package com.ramonchancay.vigia.core.data.status

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [UptimeStateEntity::class, WorkflowStateEntity::class], version = 1, exportSchema = true)
internal abstract class StatusDatabase : RoomDatabase() {
    abstract fun statusDao(): StatusDao
}
