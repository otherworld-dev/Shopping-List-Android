package dev.otherworld.shoppinglist.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.otherworld.shoppinglist.data.remote.PLACEHOLDER_BASE_URL
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Coil for item photos, sending each request through the client it belongs to: a photo on the
 * user's own server (on the placeholder host, see PhotoUrls) through the API client, which
 * fills in the server and the login; a share link's photo through the guest client, whose
 * cookies carry a password unlock. Both keep the trust-on-first-use TLS of their originals.
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    private val placeholderHost = PLACEHOLDER_BASE_URL.toHttpUrl().host

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @ApiClient apiClient: OkHttpClient,
        @GuestClient guestClient: OkHttpClient,
    ): ImageLoader {
        // The originals ask for JSON; these ask for the picture.
        val acceptImages = Interceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("Accept", "image/*").build())
        }
        val own = apiClient.newBuilder().addNetworkInterceptor(acceptImages).build()
        val guest = guestClient.newBuilder().addNetworkInterceptor(acceptImages).build()
        val route = Call.Factory { request ->
            if (request.url.host == placeholderHost) own.newCall(request) else guest.newCall(request)
        }
        return ImageLoader.Builder(context)
            .callFactory(route)
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("item_photos"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
