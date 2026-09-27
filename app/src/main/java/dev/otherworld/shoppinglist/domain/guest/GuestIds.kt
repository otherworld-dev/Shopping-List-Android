package dev.otherworld.shoppinglist.domain.guest

/**
 * Lists, items and areas that belong to a share link get local ids from [BASE] up. A friend's
 * server numbers its rows independently of ours, so its ids can't be used as they are; its
 * auto-increment ids stay far below 2^40, and offline temp ids are negative.
 */
object GuestIds {
    const val BASE: Long = 1L shl 40

    fun isGuest(id: Long): Boolean = id >= BASE

    fun fromSeq(seq: Long): Long = BASE + seq
}
