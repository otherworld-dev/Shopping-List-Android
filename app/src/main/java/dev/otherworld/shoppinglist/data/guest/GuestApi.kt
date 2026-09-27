package dev.otherworld.shoppinglist.data.guest

import dev.otherworld.shoppinglist.data.local.GuestShareEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unlocks password-protected links as needed. The server keeps an unlock in the session, which
 * ends after a while; the first call that finds it gone unlocks again with the stored password
 * and is retried once. Unlocking is limited to 5 a minute, so callers queue behind one attempt.
 */
@Singleton
class GuestApi @Inject constructor(
    private val api: PublicApi,
    private val passwords: GuestPasswords,
    private val json: Json,
) {
    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val generations = ConcurrentHashMap<Long, Long>()

    // An error body can only be read once, and a failure is often classified in two places.
    private val errors = Collections.synchronizedMap(WeakHashMap<HttpException, PublicError>())

    /** Counts the unlocks of a share, so a caller can tell another one unlocked while it waited. */
    fun generation(shareId: Long): Long = generations[shareId] ?: 0L

    suspend fun <T> withUnlock(share: GuestShareEntity, block: suspend () -> T): T {
        val seen = generation(share.id)
        try {
            return block()
        } catch (e: HttpException) {
            if (errorOf(e) != PublicError.PasswordRequired) throw e
        }
        unlock(share, seen)
        return block()
    }

    suspend fun unlock(share: GuestShareEntity, seen: Long) {
        locks.getOrPut(share.id) { Mutex() }.withLock {
            if (generation(share.id) != seen) return
            val password = passwords.get(share.id) ?: throw GuestPasswordNeededException(share.id)
            try {
                api.auth(PublicUrls.auth(share.server, share.token), PublicAuthRequest(password))
            } catch (e: HttpException) {
                when (errorOf(e)) {
                    PublicError.WrongPassword -> {
                        // Every failed unlock counts toward the server's brute-force throttle for
                        // this IP, which could lock the user out of their own web login too.
                        passwords.remove(share.id)
                        throw GuestPasswordNeededException(share.id)
                    }
                    PublicError.PasswordRequired -> throw GuestPasswordNeededException(share.id)
                    else -> throw e
                }
            }
            generations[share.id] = seen + 1
        }
    }

    fun errorOf(e: HttpException): PublicError = errors.getOrPut(e) {
        classifyPublicError(e.code(), e.response()?.errorBody()?.string(), json)
    }
}
