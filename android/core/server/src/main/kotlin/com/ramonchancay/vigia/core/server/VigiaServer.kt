package com.ramonchancay.vigia.core.server

import android.content.Context
import android.os.SystemClock
import com.ramonchancay.vigia.core.data.config.PanelConfigEditor
import com.ramonchancay.vigia.core.data.secrets.SecretStore
import com.ramonchancay.vigia.core.server.api.ApiDependencies
import com.ramonchancay.vigia.core.server.api.StatusSnapshot
import com.ramonchancay.vigia.core.server.api.vigiaApi
import com.ramonchancay.vigia.core.server.security.IpAllowlist
import com.ramonchancay.vigia.core.server.security.ServerTokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/** The embedded HTTP server. Owned by [VigiaServerService]; at most one instance runs per process. */
@Singleton
class VigiaServer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val editor: PanelConfigEditor,
    private val secrets: SecretStore,
    private val statusSnapshot: StatusSnapshot,
    private val tokenStore: ServerTokenStore
) {
    private var server: EmbeddedServer<*, *>? = null
    private var startedAt = 0L

    val isRunning: Boolean get() = server != null

    @Synchronized
    fun start() {
        if (server != null) return
        startedAt = SystemClock.elapsedRealtime()
        val deps = ApiDependencies(
            editor = editor,
            secrets = secrets,
            statusSnapshot = statusSnapshot::current,
            tokenMatches = tokenStore::matches,
            isAllowed = IpAllowlist.DEFAULT::allows,
            appVersion = appVersion(),
            uptimeSeconds = { (SystemClock.elapsedRealtime() - startedAt) / MILLIS_PER_SECOND }
        )
        server = embeddedServer(CIO, port = PORT, host = "0.0.0.0") { vigiaApi(deps) }.start(wait = false)
        Timber.i("Vigia server listening on port %d", PORT)
    }

    @Synchronized
    fun stop() {
        server?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        server = null
    }

    private fun appVersion(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            ?: "unknown"

    companion object {
        const val PORT = 8787
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
