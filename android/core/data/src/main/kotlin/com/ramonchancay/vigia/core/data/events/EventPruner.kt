package com.ramonchancay.vigia.core.data.events

import com.ramonchancay.vigia.core.common.task.AlwaysOnTask
import javax.inject.Inject
import kotlinx.coroutines.delay
import timber.log.Timber

/** Deletes expired events every few minutes so the table only holds what the panel can still show. */
internal class EventPruner @Inject constructor(private val events: EventRepository) : AlwaysOnTask {
    override suspend fun run() {
        while (true) {
            val removed = events.deleteExpired()
            if (removed > 0) Timber.d("Pruned %d expired events", removed)
            delay(INTERVAL_MILLIS)
        }
    }

    private companion object {
        const val INTERVAL_MILLIS = 5 * 60 * 1_000L
    }
}
