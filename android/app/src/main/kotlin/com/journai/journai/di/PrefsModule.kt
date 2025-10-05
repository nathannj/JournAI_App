package com.journai.journai.di

import android.content.Context
import com.journai.journai.auth.SecurePrefs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PrefsModule {

    @Provides
    @Singleton
    fun provideSecurePrefs(
        @ApplicationContext context: Context
    ): SecurePrefs = SecurePrefs(context)
}




