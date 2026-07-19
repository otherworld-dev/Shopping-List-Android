package dev.otherworld.shoppinglist.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.otherworld.shoppinglist.data.auth.CredentialStore
import dev.otherworld.shoppinglist.data.tls.AcceptedCertStore
import dev.otherworld.shoppinglist.data.tls.ActiveServerHost
import dev.otherworld.shoppinglist.data.tls.CertApprover
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Wiring for the TLS trust layer. Exposes the accepted-certificate store through its write
 *  interface, and the logged-in server's host, so app-layer code doesn't depend on the
 *  SharedPreferences-backed store or on CredentialStore directly. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TlsModule {
    @Binds
    abstract fun bindCertApprover(impl: AcceptedCertStore): CertApprover

    companion object {
        @Provides
        fun provideActiveServerHost(credentialStore: CredentialStore): ActiveServerHost =
            ActiveServerHost {
                credentialStore.current()?.server?.toHttpUrlOrNull()?.host?.lowercase()
            }
    }
}
