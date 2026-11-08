package com.ramonchancay.vigia.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.room.Room
import com.ramonchancay.vigia.core.common.dispatchers.Dispatcher
import com.ramonchancay.vigia.core.common.dispatchers.VigiaDispatchers
import com.ramonchancay.vigia.core.common.task.AlwaysOnTask
import com.ramonchancay.vigia.core.data.config.BundledDefaultConfigSource
import com.ramonchancay.vigia.core.data.config.ConfigArchive
import com.ramonchancay.vigia.core.data.config.ConfigArchiveSerializer
import com.ramonchancay.vigia.core.data.config.DefaultConfigSource
import com.ramonchancay.vigia.core.data.config.PanelConfigEditor
import com.ramonchancay.vigia.core.data.config.PanelConfigRepository
import com.ramonchancay.vigia.core.data.config.StoredPanelConfigRepository
import com.ramonchancay.vigia.core.data.events.EventDao
import com.ramonchancay.vigia.core.data.events.EventPruner
import com.ramonchancay.vigia.core.data.events.EventRepository
import com.ramonchancay.vigia.core.data.events.RoomEventRepository
import com.ramonchancay.vigia.core.data.secrets.EncryptedSecretStore
import com.ramonchancay.vigia.core.data.secrets.KeystoreSecretCipher
import com.ramonchancay.vigia.core.data.secrets.SecretArchive
import com.ramonchancay.vigia.core.data.secrets.SecretArchiveSerializer
import com.ramonchancay.vigia.core.data.secrets.SecretCipher
import com.ramonchancay.vigia.core.data.secrets.SecretStore
import com.ramonchancay.vigia.core.data.status.RoomStatusRepository
import com.ramonchancay.vigia.core.data.status.StatusDao
import com.ramonchancay.vigia.core.data.status.StatusDatabase
import com.ramonchancay.vigia.core.data.status.StatusRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
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

    @Binds
    fun bindsSecretStore(impl: EncryptedSecretStore): SecretStore

    @Binds
    fun bindsSecretCipher(impl: KeystoreSecretCipher): SecretCipher

    @Binds
    fun bindsStatusRepository(impl: RoomStatusRepository): StatusRepository

    @Binds
    fun bindsEventRepository(impl: RoomEventRepository): EventRepository

    @Binds
    @IntoSet
    fun bindsEventPruner(impl: EventPruner): AlwaysOnTask

    companion object {
        @Provides
        @Singleton
        fun providesStatusDatabase(@ApplicationContext context: Context): StatusDatabase =
            Room.databaseBuilder(context, StatusDatabase::class.java, "status.db")
                // Source states refill within a minute and events are short-lived (TTL), so a schema change
                // can start from empty instead of migrating.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()

        @Provides
        fun providesStatusDao(database: StatusDatabase): StatusDao = database.statusDao()

        @Provides
        fun providesEventDao(database: StatusDatabase): EventDao = database.eventDao()

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

        @Provides
        @Singleton
        fun providesSecretArchiveStore(
            @ApplicationContext context: Context,
            @Dispatcher(VigiaDispatchers.IO) ioDispatcher: CoroutineDispatcher
        ): DataStore<SecretArchive> = DataStoreFactory.create(
            serializer = SecretArchiveSerializer,
            // Unreadable secrets are dropped; sources then report the missing secret and the owner sets it again.
            corruptionHandler = ReplaceFileCorruptionHandler { SecretArchive() },
            scope = CoroutineScope(ioDispatcher + SupervisorJob()),
            produceFile = { File(context.filesDir, "datastore/secrets.json") }
        )
    }
}
