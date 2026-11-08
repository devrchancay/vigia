package com.ramonchancay.vigia.core.server

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.core.graphics.createBitmap
import com.ramonchancay.vigia.core.common.dispatchers.Dispatcher
import com.ramonchancay.vigia.core.common.dispatchers.VigiaDispatchers
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Captures what the panel shows, for `GET /screenshot`. The activity registers its window while it is started;
 * without a visible panel there is nothing honest to capture, so [png] returns null.
 */
@Singleton
class ScreenCapture @Inject constructor(
    @param:Dispatcher(VigiaDispatchers.Default) private val encodeDispatcher: CoroutineDispatcher
) {
    @Volatile
    private var window: WeakReference<Window>? = null

    fun register(window: Window) {
        this.window = WeakReference(window)
    }

    fun unregister(window: Window) {
        if (this.window?.get() === window) this.window = null
    }

    /** PNG of the panel window, or null when no panel is on screen or the copy fails. */
    suspend fun png(): ByteArray? {
        val target = window?.get() ?: return null
        val view = target.peekDecorView() ?: return null
        if (view.width == 0 || view.height == 0) return null
        val bitmap = createBitmap(view.width, view.height)
        val copied = suspendCancellableCoroutine { continuation ->
            PixelCopy.request(target, bitmap, { result ->
                continuation.resume(result == PixelCopy.SUCCESS)
            }, mainHandler)
        }
        if (!copied) {
            bitmap.recycle()
            return null
        }
        return withContext(encodeDispatcher) {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
                bitmap.recycle()
                out.toByteArray()
            }
        }
    }

    private companion object {
        val mainHandler = Handler(Looper.getMainLooper())

        /** Ignored for PNG (lossless); required by the API. */
        const val PNG_QUALITY = 100
    }
}
