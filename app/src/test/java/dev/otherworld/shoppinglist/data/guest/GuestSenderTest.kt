package dev.otherworld.shoppinglist.data.guest

import dev.otherworld.shoppinglist.data.local.GuestIdKind
import dev.otherworld.shoppinglist.data.local.GuestShareEntity
import dev.otherworld.shoppinglist.data.local.GuestShareState
import dev.otherworld.shoppinglist.data.local.MutationEntity
import dev.otherworld.shoppinglist.data.remote.dto.CreateItemRequest
import dev.otherworld.shoppinglist.data.remote.dto.ItemDto
import dev.otherworld.shoppinglist.data.remote.dto.ReorderRequest
import dev.otherworld.shoppinglist.data.remote.dto.UpdateItemRequest
import dev.otherworld.shoppinglist.data.sync.CheckPayload
import dev.otherworld.shoppinglist.data.sync.ItemCreatePayload
import dev.otherworld.shoppinglist.data.sync.ItemUpdatePayload
import dev.otherworld.shoppinglist.data.sync.MutationEntities
import dev.otherworld.shoppinglist.data.sync.MutationTypes
import dev.otherworld.shoppinglist.data.sync.ReorderPayload
import dev.otherworld.shoppinglist.data.sync.SyncErrorAction
import dev.otherworld.shoppinglist.data.sync.SyncErrorPolicy
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import dev.otherworld.shoppinglist.domain.text.SmartInput
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException

class GuestSenderTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val share = GuestShareEntity(
        id = 4, server = "https://example.com", token = "tok", permission = 1, passwordProtected = false,
        title = "Holiday", state = GuestShareState.OK, lastRefreshedAt = 0, droppedChanges = 0,
    )
    private val api = FakePublicApi()
    private val ids = FakeIds()
    private val sender = GuestSender(api, GuestApi(api, FakePasswords(), json), ids, json, SmartInput())
    private val listId = 1_099_511_627_777L

    private fun mutation(type: String, targetId: Long, payload: String) = MutationEntity(
        seq = 1, entity = MutationEntities.ITEM, type = type, targetId = targetId, listId = listId, payload = payload,
    )

    @Test
    fun `a new item is created and gets a local id`() = runTest {
        val m = mutation(MutationTypes.CREATE, -5, json.encodeToString(ItemCreatePayload.serializer(), ItemCreatePayload("Milk", quantity = "2")))
        val result = sender.send(m, share, emptyList()) as GuestSendResult.Created
        assertEquals(ids.localId(4, GuestIdKind.ITEM, 500), result.localId)
        assertEquals(listOf("createItem ${PublicUrls.items("https://example.com", "tok")}"), api.calls)
        assertEquals(CreateItemRequest("Milk", "2"), api.bodies.single())
        assertFalse(result.tickPending)
    }

    @Test
    fun `a ticked paste is created, and its tick is left to send next`() = runTest {
        val m = mutation(MutationTypes.CREATE, -5, json.encodeToString(ItemCreatePayload.serializer(), ItemCreatePayload("Milk", checked = true)))
        val result = sender.send(m, share, emptyList()) as GuestSendResult.Created
        assertEquals(listOf("createItem ${PublicUrls.items("https://example.com", "tok")}"), api.calls)
        assertTrue(result.tickPending)
        assertEquals(ids.localId(4, GuestIdKind.ITEM, 500), result.localId)
    }

    @Test
    fun `a ticked create is one create and no tick, so a failed tick can't make a second item`() = runTest {
        val m = mutation(MutationTypes.CREATE, -5, json.encodeToString(ItemCreatePayload.serializer(), ItemCreatePayload("Milk", checked = true)))
        sender.send(m, share, emptyList())
        assertEquals(1, api.calls.count { it.startsWith("createItem ") })
        assertEquals(0, api.calls.count { it.startsWith("checkItem ") })
    }

    @Test
    fun `an area picked on the phone is sent as the friend's area id`() = runTest {
        val area = ids.localId(4, GuestIdKind.AREA, 30)
        val m = mutation(MutationTypes.CREATE, -5, json.encodeToString(ItemCreatePayload.serializer(), ItemCreatePayload("Milk", shopAreaId = area)))
        sender.send(m, share, emptyList())
        assertEquals(CreateItemRequest("Milk", shopAreaId = 30), api.bodies.single())
    }

    @Test
    fun `an item added before the areas arrived is detected against the friend's areas`() = runTest {
        api.areas = listOf(dev.otherworld.shoppinglist.data.remote.dto.ShopAreaDto(id = 30, name = "Dairy", keywords = listOf("milk")))
        val m = mutation(MutationTypes.CREATE, -5, json.encodeToString(ItemCreatePayload.serializer(), ItemCreatePayload("Milk", detectArea = true)))
        val result = sender.send(m, share, emptyList()) as GuestSendResult.Created
        assertEquals(CreateItemRequest("Milk", shopAreaId = 30), api.bodies.single())
        assertEquals(ids.localId(4, GuestIdKind.AREA, 30), result.detectedAreaId)
    }

    @Test
    fun `edits, ticks, deletes and reorders use the friend's ids`() = runTest {
        val item = ids.localId(4, GuestIdKind.ITEM, 12)
        val area = ids.localId(4, GuestIdKind.AREA, 30)
        sender.send(mutation(MutationTypes.UPDATE, item, json.encodeToString(ItemUpdatePayload.serializer(), ItemUpdatePayload(name = "Oat milk", shopAreaId = area))), share, emptyList())
        sender.send(mutation(MutationTypes.CHECK, item, json.encodeToString(CheckPayload.serializer(), CheckPayload(true))), share, emptyList())
        sender.send(mutation(MutationTypes.REORDER, listId, json.encodeToString(ReorderPayload.serializer(), ReorderPayload(listOf(item, -9)))), share, emptyList())
        sender.send(mutation(MutationTypes.DELETE, item, "{}"), share, emptyList())
        assertEquals(
            listOf(
                "updateItem ${PublicUrls.item("https://example.com", "tok", 12)}",
                "checkItem ${PublicUrls.check("https://example.com", "tok", 12)}",
                "reorder ${PublicUrls.reorder("https://example.com", "tok")}",
                "deleteItem ${PublicUrls.item("https://example.com", "tok", 12)}",
            ),
            api.calls,
        )
        assertEquals(UpdateItemRequest(name = "Oat milk", shopAreaId = 30), api.bodies[0])
        assertEquals(ReorderRequest(listOf(12)), api.bodies[2])
    }

    @Test
    fun `an item the owner deleted drops just that change`() = runTest {
        val item = ids.localId(4, GuestIdKind.ITEM, 12)
        api.failNext += "checkItem" to httpError(404, """{"message":"Not found"}""")
        val e = runCatching { sender.send(mutation(MutationTypes.CHECK, item, """{"checked":true}"""), share, emptyList()) }.exceptionOrNull()
        assertTrue(e is HttpException && e.code() == 404)
    }

    // The server answers a change to an item the owner deleted with a 500, not a 404.
    @Test
    fun `a server error for an item the owner deleted drops just that change`() = runTest {
        val item = ids.localId(4, GuestIdKind.ITEM, 12)
        api.items = listOf(ItemDto(id = 13, listId = 9))
        val changes = listOf(
            "updateItem" to mutation(MutationTypes.UPDATE, item, """{"name":"Oat milk"}"""),
            "checkItem" to mutation(MutationTypes.CHECK, item, """{"checked":true}"""),
            "deleteItem" to mutation(MutationTypes.DELETE, item, "{}"),
        )
        for ((op, m) in changes) {
            api.failNext += op to httpError(500, "[]")
            assertEquals(GuestSendResult.Done, sender.send(m, share, emptyList()))
        }
        assertEquals(3, api.calls.count { it == "items ${PublicUrls.items("https://example.com", "tok")}" })
    }

    @Test
    fun `a server error for an item that's still there stays transient`() = runTest {
        val item = ids.localId(4, GuestIdKind.ITEM, 12)
        api.items = listOf(ItemDto(id = 12, listId = 9))
        val original = httpError(500, "[]")
        api.failNext += "checkItem" to original
        val e = runCatching { sender.send(mutation(MutationTypes.CHECK, item, """{"checked":true}"""), share, emptyList()) }.exceptionOrNull()
        assertSame(original, e)
        assertEquals(SyncErrorAction.TRANSIENT, SyncErrorPolicy.classify(e))
    }

    @Test
    fun `a server error stays transient when the check for the item fails too`() = runTest {
        val item = ids.localId(4, GuestIdKind.ITEM, 12)
        val original = httpError(503, "[]")
        api.failNext += "deleteItem" to original
        api.failNext += "items" to httpError(500, "[]")
        val e = runCatching { sender.send(mutation(MutationTypes.DELETE, item, "{}"), share, emptyList()) }.exceptionOrNull()
        assertSame(original, e)
        assertEquals(SyncErrorAction.TRANSIENT, SyncErrorPolicy.classify(e))
    }

    @Test
    fun `a deleted link drops everything`() = runTest {
        val item = ids.localId(4, GuestIdKind.ITEM, 12)
        api.failNext += "checkItem" to httpError(404, """{"message":"Not found"}""")
        api.failNext += "show" to httpError(404, """{"message":"Not found"}""")
        val e = runCatching { sender.send(mutation(MutationTypes.CHECK, item, """{"checked":true}"""), share, emptyList()) }.exceptionOrNull()
        assertEquals(4L, (e as GuestLinkDeadException).shareId)
    }

    @Test
    fun `a 404 that isn't the app's own is server trouble, not a deleted link`() = runTest {
        val item = ids.localId(4, GuestIdKind.ITEM, 12)
        api.failNext += "checkItem" to httpError(404, "[]")
        val e = runCatching { sender.send(mutation(MutationTypes.CHECK, item, """{"checked":true}"""), share, emptyList()) }.exceptionOrNull()
        assertEquals(4L, (e as GuestServerTroubleException).shareId)
        assertTrue(api.calls.none { it.startsWith("show ") })
    }

    @Test
    fun `a link made view only says so`() = runTest {
        val item = ids.localId(4, GuestIdKind.ITEM, 12)
        api.failNext += "deleteItem" to httpError(403, """{"message":"Read-only access"}""")
        val e = runCatching { sender.send(mutation(MutationTypes.DELETE, item, "{}"), share, emptyList()) }.exceptionOrNull()
        assertEquals(4L, (e as GuestReadOnlyException).shareId)
    }

    @Test
    fun `list, area and bulk changes are never sent for a guest list`() = runTest {
        val rename = MutationEntity(seq = 1, entity = MutationEntities.LIST, type = MutationTypes.RENAME, targetId = listId, listId = listId, payload = """{"title":"x"}""")
        val clear = mutation(MutationTypes.CLEAR_CHECKED, listId, "{}")
        assertEquals(GuestSendResult.Done, sender.send(rename, share, emptyList<ShopAreaModel>()))
        assertEquals(GuestSendResult.Done, sender.send(clear, share, emptyList()))
        assertEquals(emptyList<String>(), api.calls)
    }
}
