package com.ramonchancay.vigia.core.data.di

import com.ramonchancay.vigia.core.data.config.BundledPanelConfigRepository
import com.ramonchancay.vigia.core.data.config.PanelConfigRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds
    fun bindsPanelConfigRepository(impl: BundledPanelConfigRepository): PanelConfigRepository
}
