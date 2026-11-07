package com.ramonchancay.vigia

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class VigiaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Release builds log nothing: the panel must never leak tokens or URLs to logcat.
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    }
}
