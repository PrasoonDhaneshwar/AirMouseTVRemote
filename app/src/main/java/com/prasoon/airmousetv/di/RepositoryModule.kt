package com.prasoon.airmousetv.di

import android.content.Context
import com.prasoon.airmousetv.data.repository.RemoteRepository
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
    @Singleton  // Single instance for entire app
    fun provideRemoteRepository(
        @ApplicationContext context: Context,
        scope: CoroutineScope
    ): RemoteRepository {
        return RemoteRepository(context, scope)
    }
}