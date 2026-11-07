package com.ramonchancay.vigia.core.device.di

import com.ramonchancay.vigia.core.device.DeviceStatusRepository
import com.ramonchancay.vigia.core.device.SystemDeviceStatusRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface DeviceModule {
    @Binds
    fun bindsDeviceStatusRepository(impl: SystemDeviceStatusRepository): DeviceStatusRepository
}
