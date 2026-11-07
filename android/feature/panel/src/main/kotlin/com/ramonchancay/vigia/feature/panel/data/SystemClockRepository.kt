package com.ramonchancay.vigia.feature.panel.data

import com.ramonchancay.vigia.core.common.dispatchers.Dispatcher
import com.ramonchancay.vigia.core.common.dispatchers.VigiaDispatchers
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class SystemClockRepository @Inject constructor(
    private val clock: Clock,
    @param:Dispatcher(VigiaDispatchers.Default) private val dispatcher: CoroutineDispatcher
) : ClockRepository {
    override fun ticks(): Flow<Instant> = flow {
        while (true) {
            val now = clock.instant()
            emit(now)
            // Align to the next second boundary so the displayed time never lags by up to a second.
            delay(MILLIS_PER_SECOND - now.toEpochMilli() % MILLIS_PER_SECOND)
        }
    }.flowOn(dispatcher)

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
