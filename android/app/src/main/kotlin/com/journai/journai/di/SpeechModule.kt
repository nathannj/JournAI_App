package com.journai.journai.di

import android.content.Context
import com.journai.journai.network.ProxyApi
import com.journai.journai.ui.screens.create.SpeechRecognitionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpeechModule {
    
    @Provides
    @Singleton
    fun provideSpeechRecognitionManager(
        api: ProxyApi,
        @ApplicationContext context: Context
    ): SpeechRecognitionManager {
        return SpeechRecognitionManager(api, context)
    }
}
