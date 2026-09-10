package dev.otherworld.shoppinglist.data.repo

import androidx.room.withTransaction
import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.local.AreaDao
import dev.otherworld.shoppinglist.data.local.AreaEntity
import dev.otherworld.shoppinglist.data.local.ItemDao
import dev.otherworld.shoppinglist.data.local.MutationDao
import dev.otherworld.shoppinglist.data.remote.OcsBody
import dev.otherworld.shoppinglist.data.remote.OcsMeta
import dev.otherworld.shoppinglist.data.remote.OcsResponse
import dev.otherworld.shoppinglist.data.remote.OcsService
import dev.otherworld.shoppinglist.data.remote.dto.ShopAreaDto
import dev.otherworld.shoppinglist.data.sync.SyncEngine
import dev.otherworld.shoppinglist.data.sync.TempIds
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test

/**
 * Tests the area refresh behavior in ItemRepository to ensure areas are upserted BEFORE
 * any orphans are deleted, preventing an empty areas list from being emitted mid-refresh.
 *
 * **Bug:** The old pattern (deleteByList then upsertAll) caused Room to emit an empty areas
 * list between the delete and insert, breaking auto-detection for items added during refresh.
 *
 * **Fix:** Upsert first, then selectively delete orphans (areas removed on server).
 */
class ItemRepositoryAreaRefreshTest {

    private lateinit var repository: ItemRepository
    private val mockService: OcsService = mockk()
    private val mockDb: AppDatabase = mockk()
    private val mockAreaDao: AreaDao = mockk()
    private val mockItemDao: ItemDao = mockk()
    private val mockMutationDao: MutationDao = mockk()
    private val mockSync: SyncEngine = mockk()
    private val mockTempIds: TempIds = mockk()
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        coEvery { mockDb.areaDao() } returns mockAreaDao
        coEvery { mockDb.itemDao() } returns mockItemDao
        coEvery { mockDb.mutationDao() } returns mockMutationDao
        coEvery { mockDb.withTransaction<Unit>(any()) } coAnswers {
            // Execute the transaction block immediately
            val block = arg<suspend () -> Unit>(0)
            block()
        }
        coEvery { mockSync.requestSync() } just Runs

        repository = ItemRepository(
            service = mockService,
            db = mockDb,
            sync = mockSync,
            tempIds = mockTempIds,
            json = json,
        )
    }

    /**
     * **Core test:** Verifies that areas are upserted BEFORE any deletes happen,
     * eliminating the empty-list emission window.
     */
    @Test
    fun `refresh upserts areas before deleting orphans`() = runTest {
        val listId = 1L

        // Server response: 2 areas (one is new)
        val serverAreas = listOf(
            ShopAreaDto(1L, listId, "Dairy", 0, "#FF5722", listOf("milk", "cheese")),
            ShopAreaDto(3L, listId, "Bakery", 2, "#FFC107", listOf("bread")), // New area
        )
        coEvery { mockService.getAreas(listId) } returns OcsResponse(
            OcsBody(OcsMeta(),serverAreas)
        )

        // Current local areas: 3 areas (area 2 will become orphan)
        val localAreas = listOf(
            AreaEntity(1L, listId, "Dairy", 0, "#FF5722", listOf("milk", "cheese")),
            AreaEntity(2L, listId, "Produce", 1, "#4CAF50", listOf("apple")), // Orphan
            AreaEntity(3L, listId, "Bakery", 2, "#FFC107", listOf("bread")),
        )
        coEvery { mockAreaDao.getByList(listId) } returns localAreas

        // No pending area mutations
        coEvery { mockMutationDao.pendingAreaCount(listId) } returns 0

        // Mock successful upsert and delete
        coEvery { mockAreaDao.upsertAll(any()) } just Runs
        coEvery { mockAreaDao.deleteById(any()) } just Runs

        // Mock items call (not testing items here, but it's called in parallel)
        coEvery { mockService.getItems(listId) } returns OcsResponse(
            OcsBody(OcsMeta(),emptyList())
        )
        coEvery { mockItemDao.getByList(listId) } returns emptyList()
        coEvery { mockItemDao.deleteByIds(any()) } just Runs
        coEvery { mockMutationDao.pendingItemIds(listId) } returns emptyList()

        // Execute refresh
        repository.refresh(listId)

        // **Critical assertion:** Verify call order
        coVerifyOrder {
            // 1. Upsert ALL server areas first (no delete yet)
            mockAreaDao.upsertAll(match { entities ->
                entities.size == 2 &&
                entities.any { it.id == 1L && it.name == "Dairy" } &&
                entities.any { it.id == 3L && it.name == "Bakery" }
            })

            // 2. Read current areas (includes newly upserted + orphans)
            mockAreaDao.getByList(listId)

            // 3. Delete only the orphan (area 2)
            mockAreaDao.deleteById(2L)
        }

        // Verify orphan was deleted
        coVerify(exactly = 1) { mockAreaDao.deleteById(2L) }

        // Verify non-orphans were NOT deleted
        coVerify(exactly = 0) { mockAreaDao.deleteById(1L) }
        coVerify(exactly = 0) { mockAreaDao.deleteById(3L) }
    }

    /**
     * **Edge case:** No orphans to delete — only upsert happens.
     */
    @Test
    fun `refresh with no orphans only upserts`() = runTest {
        val listId = 1L

        val serverAreas = listOf(
            ShopAreaDto(1L, listId, "Dairy", 0, null, listOf("milk")),
        )
        coEvery { mockService.getAreas(listId) } returns OcsResponse(
            OcsBody(OcsMeta(),serverAreas)
        )

        val localAreas = listOf(
            AreaEntity(1L, listId, "Dairy", 0, null, listOf("milk")),
        )
        coEvery { mockAreaDao.getByList(listId) } returns localAreas
        coEvery { mockMutationDao.pendingAreaCount(listId) } returns 0
        coEvery { mockAreaDao.upsertAll(any()) } just Runs

        coEvery { mockService.getItems(listId) } returns OcsResponse(
            OcsBody(OcsMeta(),emptyList())
        )
        coEvery { mockItemDao.getByList(listId) } returns emptyList()
        coEvery { mockItemDao.deleteByIds(any()) } just Runs
        coEvery { mockMutationDao.pendingItemIds(listId) } returns emptyList()

        repository.refresh(listId)

        // Upsert happened
        coVerify(exactly = 1) { mockAreaDao.upsertAll(any()) }

        // No deletes (no orphans)
        coVerify(exactly = 0) { mockAreaDao.deleteById(any()) }
    }

    /**
     * **Regression prevention:** Verifies deleteByList is NOT called.
     *
     * The old buggy pattern used deleteByList(), which cleared all areas at once.
     * This test ensures we never regress to that pattern.
     */
    @Test
    fun `refresh never calls deleteByList`() = runTest {
        val listId = 1L

        val serverAreas = listOf(
            ShopAreaDto(1L, listId, "Dairy", 0, null, listOf("milk")),
        )
        coEvery { mockService.getAreas(listId) } returns OcsResponse(
            OcsBody(OcsMeta(),serverAreas)
        )
        coEvery { mockAreaDao.getByList(listId) } returns emptyList()
        coEvery { mockMutationDao.pendingAreaCount(listId) } returns 0
        coEvery { mockAreaDao.upsertAll(any()) } just Runs

        coEvery { mockService.getItems(listId) } returns OcsResponse(
            OcsBody(OcsMeta(),emptyList())
        )
        coEvery { mockItemDao.getByList(listId) } returns emptyList()
        coEvery { mockItemDao.deleteByIds(any()) } just Runs
        coEvery { mockMutationDao.pendingItemIds(listId) } returns emptyList()

        // Mock deleteByList to track if it's called
        coEvery { mockAreaDao.deleteByList(any()) } just Runs

        repository.refresh(listId)

        // **Critical:** deleteByList should NEVER be called
        coVerify(exactly = 0) { mockAreaDao.deleteByList(any()) }
    }

    /**
     * **Concurrency safety:** Pending mutations block area refresh.
     */
    @Test
    fun `refresh skips areas when pending mutations exist`() = runTest {
        val listId = 1L

        // Pending area mutation (e.g., reorder in progress)
        coEvery { mockMutationDao.pendingAreaCount(listId) } returns 1

        coEvery { mockService.getItems(listId) } returns OcsResponse(
            OcsBody(OcsMeta(),emptyList())
        )
        coEvery { mockItemDao.getByList(listId) } returns emptyList()
        coEvery { mockItemDao.deleteByIds(any()) } just Runs
        coEvery { mockMutationDao.pendingItemIds(listId) } returns emptyList()

        repository.refresh(listId)

        // Area refresh should be skipped entirely
        coVerify(exactly = 0) { mockService.getAreas(any()) }
        coVerify(exactly = 0) { mockAreaDao.upsertAll(any()) }
        coVerify(exactly = 0) { mockAreaDao.deleteById(any()) }
    }
}
