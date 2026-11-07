package com.ramonchancay.vigia.core.server.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.ramonchancay.vigia.core.common.dispatchers.Dispatcher
import com.ramonchancay.vigia.core.common.dispatchers.VigiaDispatchers
import com.ramonchancay.vigia.core.server.security.ServerSettings
import com.ramonchancay.vigia.core.server.security.ServerSettingsSerializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
internal object ServerModule {
    @Provides
    @Singleton
    fun providesServerSettingsStore(
        @ApplicationContext context: Context,
        @Dispatcher(VigiaDispatchers.IO) ioDispatcher: CoroutineDispatcher
    ): DataStore<ServerSettings> = DataStoreFactory.create(
        serializer = ServerSettingsSerializer,
        // A corrupted file yields a new token; clients must re-pair, which is safer than guessing.
        corruptionHandler = ReplaceFileCorruptionHandler { ServerSettings() },
        scope = CoroutineScope(ioDispatcher + SupervisorJob()),
        produceFile = { File(context.filesDir, "datastore/server_settings.json") }
    )
}
