package com.prasoon.airmousetv.di

import android.content.Context
import com.prasoon.airmousetv.airmouse.repository.AirMouseSession
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hands the app the air mouse. The air mouse lives in its own Gradle module, which knows nothing of
 * Hilt so that it builds and tests without annotation processing; this is where the two meet.
 */
@Module
@InstallIn(SingletonComponent::class)
object AirMouseModule {

    /** One session for the app's whole life, so the pointer link survives configuration changes. */
    @Provides
    @Singleton
    fun airMouseSession(@ApplicationContext context: Context): AirMouseSession = AirMouseSession(context)
}
