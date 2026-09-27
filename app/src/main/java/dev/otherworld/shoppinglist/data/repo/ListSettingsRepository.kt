package dev.otherworld.shoppinglist.data.repo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.local.MutationEntity
import dev.otherworld.shoppinglist.data.remote.OcsService
import dev.otherworld.shoppinglist.data.remote.dto.supportsListOrder
import dev.otherworld.shoppinglist.data.sync.MutationEntities
import dev.otherworld.shoppinglist.data.sync.MutationTypes
import dev.otherworld.shoppinglist.data.sync.SettingsPayload
import dev.otherworld.shoppinglist.data.sync.SyncEngine
import dev.otherworld.shoppinglist.domain.sort.ListSortMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How this user sorts their lists, kept on the server so it matches the web app, and cached
 * here so the lists screen sorts straight away, offline too. Changes are queued like a pin.
 */
@Singleton
class ListSettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val service: OcsService,
    db: AppDatabase,
    private val sync: SyncEngine,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences("list_settings", Context.MODE_PRIVATE)
    private val mutationDao = db.mutationDao()

    private val _listSort = MutableStateFlow(ListSortMode.fromStorage(prefs.getString(KEY_SORT, null)))
    val listSort: StateFlow<ListSortMode> = _listSort.asStateFlow()

    private val _supported = MutableStateFlow(prefs.getBoolean(KEY_SUPPORTED, false))
    val listOrderSupported: StateFlow<Boolean> = _supported.asStateFlow()

    /** Whether the server keeps list orders, and this user's sort, unless a change of theirs is still queued. */
    suspend fun refresh() {
        val supported = service.capabilities().ocs.data.capabilities.supportsListOrder()
        _supported.value = supported
        prefs.edit().putBoolean(KEY_SUPPORTED, supported).apply()
        if (!supported || mutationDao.countByType(MutationTypes.UPDATE_SETTINGS) > 0) return
        remember(ListSortMode.fromStorage(service.getSettings().ocs.data.listSort))
    }

    suspend fun setListSort(mode: ListSortMode) {
        remember(mode)
        mutationDao.insert(
            MutationEntity(
                entity = MutationEntities.LIST,
                type = MutationTypes.UPDATE_SETTINGS,
                targetId = 0,
                listId = 0,
                payload = json.encodeToString(SettingsPayload.serializer(), SettingsPayload(mode.storageValue)),
            ),
        )
        sync.requestSync()
    }

    /** On logout, so the next account starts from the defaults. */
    fun clear() {
        prefs.edit().clear().apply()
        _listSort.value = ListSortMode.UPDATED
        _supported.value = false
    }

    private fun remember(mode: ListSortMode) {
        _listSort.value = mode
        prefs.edit().putString(KEY_SORT, mode.storageValue).apply()
    }

    private companion object {
        const val KEY_SORT = "list_sort"
        const val KEY_SUPPORTED = "list_order_supported"
    }
}
