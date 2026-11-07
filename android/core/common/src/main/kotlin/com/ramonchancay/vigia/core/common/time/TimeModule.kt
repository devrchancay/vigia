package com.ramonchancay.vigia.core.common.time

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
object TimeModule {
    /** UTC clock; convert to the device zone at display time so zone changes are picked up live. */
    @Provides
    fun providesClock(): Clock = Clock.systemUTC()
}
