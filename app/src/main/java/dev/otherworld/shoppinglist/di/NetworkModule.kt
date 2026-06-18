package dev.otherworld.shoppinglist.di

import dev.otherworld.shoppinglist.data.remote.JsonConverterFactory
import dev.otherworld.shoppinglist.data.remote.OcsAuthInterceptor
import dev.otherworld.shoppinglist.data.remote.OcsService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Any absolute placeholder; the auth interceptor swaps the host per request. */
    private const val PLACEHOLDER_BASE_URL = "https://shopping-list.invalid/"

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideLogging(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

    @Provides
    @Singleton
    @LoginClient
    fun provideLoginClient(logging: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Shopping List (Android)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            })
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @ApiClient
    fun provideApiClient(
        authInterceptor: OcsAuthInterceptor,
        logging: HttpLoggingInterceptor,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            // Generous timeouts: this can run on slow e-ink-device Wi-Fi where a cold TLS
            // connection takes several seconds. Too-tight timeouts spuriously fail a POST whose
            // request already reached the server, and the retry then creates a duplicate.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(@ApiClient client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(JsonConverterFactory(json))
            .build()

    @Provides
    @Singleton
    fun provideOcsService(retrofit: Retrofit): OcsService =
        retrofit.create(OcsService::class.java)
}
