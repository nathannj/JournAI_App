package com.journai.journai.di

import android.content.Context
import com.journai.journai.network.ProxyApi
import com.journai.journai.network.OrganizeService
import com.journai.journai.BuildConfig
import com.journai.journai.auth.AuthManager
import com.journai.journai.auth.SecurePrefs
import com.journai.journai.auth.DeviceKeyManager
import com.journai.journai.auth.IntegrityService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Singleton
import javax.inject.Provider
import kotlinx.coroutines.runBlocking

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context
    , authInterceptor: Interceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val builder = OkHttpClient.Builder()
        if (!BuildConfig.DEBUG) {
            val host = context.getString(com.journai.journai.R.string.proxy_prod_host)
            val pin = context.getString(com.journai.journai.R.string.proxy_prod_pin)
            val pinner = okhttp3.CertificatePinner.Builder()
                .add(host, pin)
                .build()
            builder.certificatePinner(pinner)
        }
        return builder
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        @ApplicationContext context: Context,
        client: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        val baseUrl = if (BuildConfig.DEBUG) {
            context.getString(com.journai.journai.R.string.proxy_base_url)
        } else {
            context.getString(com.journai.journai.R.string.proxy_base_url_prod)
        }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    fun provideProxyApi(retrofit: Retrofit): ProxyApi = retrofit.create(ProxyApi::class.java)

    @Provides
    @Singleton
    fun provideOrganizeService(api: ProxyApi, moshi: Moshi, securePrefs: SecurePrefs): OrganizeService =
        OrganizeService(api, moshi, securePrefs)

    @Provides
    @Singleton
    fun provideAuthManager(
        @ApplicationContext context: Context,
        api: ProxyApi,
        securePrefs: SecurePrefs
    ): AuthManager {
        val keyManager = DeviceKeyManager()
        val integrity = IntegrityService(context)
        return AuthManager(context, api, securePrefs, keyManager, integrity)
    }


    @Provides
    @Singleton
    fun provideAuthInterceptor(
        securePrefs: SecurePrefs,
        authManager: Provider<AuthManager>
    ): Interceptor = Interceptor { chain ->
        var token = securePrefs.getJwt() ?: runBlocking { authManager.get().ensureToken() }
        var req = chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        var resp = chain.proceed(req)
        if (resp.code == 401) {
            resp.close()
            token = runBlocking { authManager.get().register() }
            req = chain.request().newBuilder().header("Authorization", "Bearer $token").build()
            resp = chain.proceed(req)
        }
        resp
    }
}


