package com.ramonchancay.vigia.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.ramonchancay.vigia.core.common.dispatchers.Dispatcher
import com.ramonchancay.vigia.core.common.dispatchers.VigiaDispatchers
import com.ramonchancay.vigia.core.data.config.BundledDefaultConfigSource
import com.ramonchancay.vigia.core.data.config.ConfigArchive
import com.ramonchancay.vigia.core.data.config.ConfigArchiveSerializer
import com.ramonchancay.vigia.core.data.config.DefaultConfigSource
import com.ramonchancay.vigia.core.data.config.PanelConfigEditor
import com.ramonchancay.vigia.core.data.config.PanelConfigRepository
import com.ramonchancay.vigia.core.data.config.StoredPanelConfigRepository
import dagger.Binds
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
internal interface DataModule {
    @Binds
    fun bindsPanelConfigRepository(impl: StoredPanelConfigRepository): PanelConfigRepository

    @Binds
    fun bindsPanelConfigEditor(impl: StoredPanelConfigRepository): PanelConfigEditor

    @Binds
    fun bindsDefaultConfigSource(impl: BundledDefaultConfigSource): DefaultConfigSource

    companion object {
        @Provides
        @Singleton
        fun providesConfigArchiveStore(
            @ApplicationContext context: Context,
            @Dispatcher(VigiaDispatchers.IO) ioDispatcher: CoroutineDispatcher
        ): DataStore<ConfigArchive> = DataStoreFactory.create(
            serializer = ConfigArchiveSerializer,
            // A corrupted file falls back to the bundled default instead of crashing the panel.
            corruptionHandler = ReplaceFileCorruptionHandler { ConfigArchive() },
            scope = CoroutineScope(ioDispatcher + SupervisorJob()),
            produceFile = { File(context.filesDir, "datastore/panel_config.json") }
        )
    }
}
