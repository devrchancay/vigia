package com.ramonchancay.vigia.feature.sources.uptime

import com.ramonchancay.vigia.core.common.dispatchers.Dispatcher
import com.ramonchancay.vigia.core.common.dispatchers.VigiaDispatchers
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Result of one request; [error] is a short reason when no HTTP response arrived. */
data class UptimeCheck(val up: Boolean, val statusCode: Int?, val latencyMs: Long?, val error: String?)

/** One GET per check (some servers reject HEAD). Only the status line and headers are read. */
class UptimeChecker @Inject constructor(
    private val client: OkHttpClient,
    @param:Dispatcher(VigiaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) {
    /** Up when the status equals [expectStatus], or is 2xx/3xx when none is given. */
    suspend fun check(url: String, expectStatus: Int?): UptimeCheck = withContext(ioDispatcher) {
        val started = System.nanoTime()
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                val latency = (System.nanoTime() - started) / NANOS_PER_MILLI
                val up = expectStatus?.let { response.code == it } ?: (response.code in 200..399)
                UptimeCheck(up = up, statusCode = response.code, latencyMs = latency, error = null)
            }
        } catch (e: IOException) {
            UptimeCheck(up = false, statusCode = null, latencyMs = null, error = e.javaClass.simpleName)
        } catch (e: IllegalArgumentException) {
            UptimeCheck(up = false, statusCode = null, latencyMs = null, error = "InvalidUrl")
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
