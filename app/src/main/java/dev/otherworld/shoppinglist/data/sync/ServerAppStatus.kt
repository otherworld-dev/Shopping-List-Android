package dev.otherworld.shoppinglist.data.sync

import dev.otherworld.shoppinglist.data.auth.Account
import dev.otherworld.shoppinglist.data.auth.CredentialStore
import dev.otherworld.shoppinglist.data.remote.OcsService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A 404 from the lists call: the server has no Shopping List app (Shopping-List#60). Every
 * server with the app answers that route, so unlike the other calls it never 404s for a list
 * deleted elsewhere.
 */
fun isServerAppMissing(e: Throwable): Boolean = e is HttpException && e.code() == 404

/** What the lists call last said about each account's server, keyed by [ServerAppState.key]. */
data class ServerAppState(val installedFor: String? = null, val missingFor: String? = null) {

    /** After the lists call for [account] answered, or failed with [error]. */
    fun answered(account: String?, error: Throwable?): ServerAppState = when {
        error == null -> copy(installedFor = account, missingFor = missingFor.takeIf { it != account })
        isServerAppMissing(error) -> copy(missingFor = account, installedFor = installedFor.takeIf { it != account })
        else -> this // offline or down says nothing either way
    }

    fun installed(account: String?) = account != null && installedFor == account

    fun missing(account: String?) = account != null && missingFor == account

    companion object {
        fun key(account: Account) = "${account.loginName}@${account.server}"
    }
}

/**
 * Whether the signed-in server has the Shopping List app installed and enabled. Without it every
 * call 404s, which the queue would read as "deleted elsewhere" and drop, so the queue isn't sent
 * until the lists call has answered once for this account.
 */
@Singleton
class ServerAppStatus @Inject constructor(
    private val service: OcsService,
    private val credentialStore: CredentialStore,
) {
    private val state = MutableStateFlow(ServerAppState())

    /** True while the signed-in account's server is known to be missing the app. */
    val missing: Flow<Boolean> = combine(state, credentialStore.accountFlow) { s, account ->
        s.missing(account?.let(ServerAppState::key))
    }

    /** Runs the lists call, noting from its answer whether the app is there. */
    suspend fun <T> watch(call: suspend () -> T): T {
        val account = credentialStore.current()?.let(ServerAppState::key)
        try {
            return call().also { state.update { it.answered(account, null) } }
        } catch (e: Exception) {
            if (e !is CancellationException) state.update { it.answered(account, e) }
            throw e
        }
    }

    /** Whether the own server's queue can be sent; asks the server if it isn't known for this account. */
    suspend fun readyToSync(): Boolean {
        val account = credentialStore.current()?.let(ServerAppState::key) ?: return false
        if (state.value.installed(account)) return true
        return try {
            watch { service.getLists() }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Missing, offline or down: the queue waits either way and the next drain asks again.
            false
        }
    }
}
