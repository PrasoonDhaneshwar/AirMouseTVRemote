package com.prasoon.airmousetv.di

import android.content.Context
import com.prasoon.airmousetv.data.repository.NetworkMonitor
import com.prasoon.airmousetv.data.repository.NsdDiscoveryEngine
import com.prasoon.airmousetv.data.repository.RemoteRepository
import com.prasoon.airmousetv.data.repository.RemoteSessionManager
import com.prasoon.airmousetv.data.repository.TvCacheManager
import com.prasoon.airmousetv.data.repository.TvPortScanner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)  // App lifetime
object RepositoryModule {

    @Provides
    @Singleton  // Single instance for entire app
    fun provideCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Provides
    @Singleton
    fun provideNsdDiscoveryEngine(
        @ApplicationContext context: Context
    ): NsdDiscoveryEngine = NsdDiscoveryEngine(context)

    @Provides
    @Singleton
    fun provideTvPortScanner(
        cache: TvCacheManager
    ): TvPortScanner = TvPortScanner(cache)

    @Provides
    @Singleton
    fun provideTvCacheManager(
        @ApplicationContext context: Context
    ): TvCacheManager = TvCacheManager(context)

    @Provides
    @Singleton
    fun provideNetworkMonitor(
        @ApplicationContext context: Context
    ): NetworkMonitor = NetworkMonitor(context)

    @Provides
    @Singleton
    fun provideRemoteSessionManager(
        @ApplicationContext context: Context
    ): RemoteSessionManager = RemoteSessionManager(context)

    @Provides
    @Singleton
    fun provideRemoteRepository(
        discovery: NsdDiscoveryEngine,
        scanner: TvPortScanner,
        cache: TvCacheManager,
        monitor: NetworkMonitor,
        session: RemoteSessionManager
    ): RemoteRepository = RemoteRepository(
        networkMonitor = monitor,
        nsdDiscoveryEngine = discovery,
        tvPortScanner = scanner,
        tvCacheManager = cache,
        session = session
    )
}