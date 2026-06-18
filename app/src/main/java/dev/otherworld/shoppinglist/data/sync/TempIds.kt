package dev.otherworld.shoppinglist.data.sync

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates strictly-decreasing negative ids for entities created while offline. Seeding from
 * the current time keeps ids from a later app session more negative than an earlier one's, so
 * un-synced temp ids never collide across restarts.
 */
@Singleton
class TempIds @Inject constructor() {
    private val counter = AtomicLong(-System.currentTimeMillis())
    fun next(): Long = counter.getAndDecrement()
}
