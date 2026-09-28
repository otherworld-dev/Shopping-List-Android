package dev.otherworld.shoppinglist.data.sync

import dev.otherworld.shoppinglist.data.local.ItemEntity
import dev.otherworld.shoppinglist.data.local.ListEntity
import dev.otherworld.shoppinglist.data.local.MutationEntity
import kotlinx.serialization.json.Json

/**
 * The queue rows that send a phone-only list to the account: the list, then each item in its
 * order with its tick, then the pin. Everything keeps its temp id, so the sync engine creates
 * and remaps it exactly like a list made offline. Areas are detected when each item is sent,
 * in case the new list comes with some.
 */
internal fun planUpload(list: ListEntity, items: List<ItemEntity>, json: Json): List<MutationEntity> = buildList {
    add(
        MutationEntity(
            entity = MutationEntities.LIST,
            type = MutationTypes.CREATE,
            targetId = list.id,
            listId = list.id,
            payload = json.encodeToString(TitlePayload.serializer(), TitlePayload(list.title)),
        ),
    )
    items.sortedWith(compareBy({ it.sortOrder }, { it.id })).forEach { item ->
        add(
            MutationEntity(
                entity = MutationEntities.ITEM,
                type = MutationTypes.CREATE,
                targetId = item.id,
                listId = list.id,
                payload = json.encodeToString(
                    ItemCreatePayload.serializer(),
                    ItemCreatePayload(item.name, item.quantity, item.unit, checked = item.checked, detectArea = true),
                ),
            ),
        )
    }
    if (list.isPinned) {
        add(
            MutationEntity(
                entity = MutationEntities.LIST,
                type = MutationTypes.UPDATE_PREFERENCES,
                targetId = list.id,
                listId = list.id,
                payload = json.encodeToString(PinPayload.serializer(), PinPayload(true)),
            ),
        )
    }
}
