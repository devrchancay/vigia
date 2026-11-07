package com.ramonchancay.vigia.core.server

import com.ramonchancay.vigia.core.common.dispatchers.Dispatcher
import com.ramonchancay.vigia.core.common.dispatchers.VigiaDispatchers
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Base URLs at which this phone's API can be reached, Tailscale first (the recommended way in). */
class ServerAddresses @Inject constructor(
    @param:Dispatcher(VigiaDispatchers.IO) private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun baseUrls(): List<String> = withContext(ioDispatcher) {
        val addresses = runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        }.getOrDefault(emptyList())
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress.orEmpty() }
        sortTailscaleFirst(addresses).map { "http://$it:${VigiaServer.PORT}" }
    }

    companion object {
        /** Tailscale hands out addresses in 100.64.0.0/10. */
        fun isTailscale(ipv4: String): Boolean {
            val parts = ipv4.split('.').mapNotNull { it.toIntOrNull() }
            return parts.size == 4 && parts[0] == 100 && parts[1] in 64..127
        }

        fun sortTailscaleFirst(addresses: List<String>): List<String> =
            addresses.distinct().sortedByDescending { isTailscale(it) }
    }
}
