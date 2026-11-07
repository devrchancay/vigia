package com.ramonchancay.vigia.core.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.ramonchancay.vigia.core.common.dispatchers.Dispatcher
import com.ramonchancay.vigia.core.common.dispatchers.VigiaDispatchers
import com.ramonchancay.vigia.core.common.task.AlwaysOnTask
import com.ramonchancay.vigia.core.server.security.ServerTokenStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Foreground service that keeps the embedded server alive while the panel runs. Uses the `specialUse` type:
 * `dataSync` is capped at 6 h/day on Android 15+, and this server must run around the clock.
 */
@AndroidEntryPoint
class VigiaServerService : Service() {
    @Inject
    lateinit var server: VigiaServer

    @Inject
    lateinit var tokenStore: ServerTokenStore

    /** Pollers and other long-running work contributed by feature modules. */
    @Inject
    lateinit var tasks: Set<@JvmSuppressWildcards AlwaysOnTask>

    private var tasksStarted = false

    @Inject
    @Dispatcher(VigiaDispatchers.IO)
    lateinit var ioDispatcher: CoroutineDispatcher

    private val scope by lazy { CoroutineScope(SupervisorJob() + ioDispatcher) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), foregroundType())
        // The token exists from the first launch, so the setup screen can show it before any request arrives.
        scope.launch { tokenStore.currentToken() }
        server.start()
        if (!tasksStarted) {
            tasksStarted = true
            tasks.forEach { task ->
                scope.launch {
                    runCatching {
                        task.run()
                    }.onFailure { if (it !is CancellationException) Timber.e(it, "Always-on task failed") }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        server.stop()
        super.onDestroy()
    }

    private fun foregroundType(): Int = if (Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    ) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    } else {
        0
    }

    private fun notification(): Notification {
        getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.server_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_server_notification)
            .setContentTitle(getString(R.string.server_notification_title))
            .setContentText(getString(R.string.server_notification_text, VigiaServer.PORT))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "server"
        private const val NOTIFICATION_ID = 1

        /** Safe to call repeatedly; the server starts once. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, VigiaServerService::class.java))
        }
    }
}
