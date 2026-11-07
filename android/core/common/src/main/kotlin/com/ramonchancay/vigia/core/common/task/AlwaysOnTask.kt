package com.ramonchancay.vigia.core.common.task

/**
 * Work that runs for as long as the always-on service lives (pollers, later schedules). Modules contribute
 * tasks with Hilt `@IntoSet`; the service starts each one in its own coroutine and cancels them on stop.
 */
fun interface AlwaysOnTask {
    /** Runs until cancelled. Must handle its own failures: an exception here is logged and the task ends. */
    suspend fun run()
}
