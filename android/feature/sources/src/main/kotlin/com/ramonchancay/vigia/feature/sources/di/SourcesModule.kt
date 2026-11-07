package com.ramonchancay.vigia.feature.sources.di

import com.ramonchancay.vigia.core.common.task.AlwaysOnTask
import com.ramonchancay.vigia.feature.sources.SourceScheduler
import com.ramonchancay.vigia.feature.sources.github.GithubActionsClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
internal interface SourcesModule {
    @Binds
    @IntoSet
    fun bindsSourceScheduler(impl: SourceScheduler): AlwaysOnTask

    companion object {
        /** GitHub rejects requests without a User-Agent; this one names the project, never the owner. */
        private const val USER_AGENT = "Vigia (+https://github.com/devrchancay/vigia)"

        @Provides
        @Singleton
        fun providesOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .build()

        @Provides
        @Named(GithubActionsClient.BASE_URL_NAME)
        fun providesGithubBaseUrl(): String = GithubActionsClient.DEFAULT_BASE_URL
    }
}
