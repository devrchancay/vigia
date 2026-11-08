package com.ramonchancay.vigia.core.data.events

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ramonchancay.vigia.core.model.EventLevel
import com.ramonchancay.vigia.core.model.StoredEvent

@Entity(tableName = "events", indices = [Index("source"), Index("expiresAtMillis")])
internal data class EventEntity(
    @PrimaryKey val id: String,
    val source: String,
    val eventKey: String?,
    val kind: String?,
    val title: String,
    val body: String?,
    val level: String,
    val url: String?,
    val atMillis: Long?,
    val receivedAtMillis: Long,
    val expiresAtMillis: Long
) {
    fun toModel() = StoredEvent(
        id = id,
        source = source,
        key = eventKey,
        kind = kind,
        title = title,
        body = body,
        level = EventLevel.entries.firstOrNull { it.name == level } ?: EventLevel.Info,
        url = url,
        atMillis = atMillis,
        receivedAtMillis = receivedAtMillis,
        expiresAtMillis = expiresAtMillis
    )
}
