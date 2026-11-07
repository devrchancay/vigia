package com.ramonchancay.vigia.feature.panel.di

import com.ramonchancay.vigia.feature.panel.data.ClockRepository
import com.ramonchancay.vigia.feature.panel.data.SystemClockRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface PanelModule {
    @Binds
    fun bindsClockRepository(impl: SystemClockRepository): ClockRepository
}
