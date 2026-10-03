package dev.otherworld.shoppinglist.data.remote

import dev.otherworld.shoppinglist.data.remote.dto.ShareDto
import dev.otherworld.shoppinglist.domain.model.toModel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareDtoCodeTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    @Test
    fun `a link share keeps its invite code`() {
        val dto = json.decodeFromString(ShareDto.serializer(), """{"id":7,"sharedWithType":3,"token":"abc","code":"K7QM3XPD"}""")
        assertEquals("K7QM3XPD", dto.toModel().code)
    }

    // Servers before 1.10.0, user and group shares, and old links not yet opened have no code.
    @Test
    fun `no code is fine`() {
        assertNull(json.decodeFromString(ShareDto.serializer(), """{"id":7,"sharedWithType":3,"token":"abc"}""").toModel().code)
        assertNull(json.decodeFromString(ShareDto.serializer(), """{"id":7,"sharedWithType":3,"code":null}""").toModel().code)
    }
}
