package dev.otherworld.shoppinglist.data.repo

import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.local.AreaDao
import dev.otherworld.shoppinglist.data.local.ItemDao
import dev.otherworld.shoppinglist.data.local.ListDao
import dev.otherworld.shoppinglist.data.local.MutationDao
import dev.otherworld.shoppinglist.data.local.MutationEntity
import dev.otherworld.shoppinglist.data.remote.OcsService
import dev.otherworld.shoppinglist.data.sync.MutationEntities
import dev.otherworld.shoppinglist.data.sync.MutationTypes
import dev.otherworld.shoppinglist.data.sync.PinPayload
import dev.otherworld.shoppinglist.data.sync.SyncEngine
import dev.otherworld.shoppinglist.data.sync.TempIds
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for list pin/unpin functionality:
 * - Optimistic database updates
 * - Mutation queueing for server sync
 */
class ListRepositoryPinTest {
    private lateinit var repository: ListRepository
    private val mockService: OcsService = mockk()
    private val mockDb: AppDatabase = mockk()
    private val mockListDao: ListDao = mockk()
    private val mockItemDao: ItemDao = mockk()
    private val mockAreaDao: AreaDao = mockk()
    private val mockMutationDao: MutationDao = mockk()
    private val mockSync: SyncEngine = mockk()
    private val mockTempIds: TempIds = mockk()
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        coEvery { mockDb.listDao() } returns mockListDao
        coEvery { mockDb.itemDao() } returns mockItemDao
        coEvery { mockDb.areaDao() } returns mockAreaDao
        coEvery { mockDb.mutationDao() } returns mockMutationDao
        coEvery { mockSync.requestSync() } just Runs
        coEvery { mockListDao.updateIsPinned(any(), any()) } just Runs
        coEvery { mockMutationDao.insert(any()) } returns 1L

        repository = ListRepository(
            service = mockService,
            db = mockDb,
            sync = mockSync,
            tempIds = mockTempIds,
            json = json,
        )
    }

    @Test
    fun `pinList updates database optimistically`() = runTest {
        // When
        repository.pinList(123L)

        // Then
        coVerify { mockListDao.updateIsPinned(123L, true) }
    }

    @Test
    fun `pinList queues mutation for sync`() = runTest {
        // Given
        val mutationSlot = slot<MutationEntity>()

        // When
        repository.pinList(123L)

        // Then
        coVerify { mockMutationDao.insert(capture(mutationSlot)) }

        val mutation = mutationSlot.captured
        assertEquals(MutationEntities.LIST, mutation.entity)
        assertEquals(123L, mutation.targetId)
        assertEquals(123L, mutation.listId)
        assertEquals(MutationTypes.UPDATE_PREFERENCES, mutation.type)

        // Verify payload contains isPinned=true
        val payload = json.decodeFromString<PinPayload>(mutation.payload)
        assertTrue(payload.isPinned)
    }

    @Test
    fun `pinList requests sync`() = runTest {
        // When
        repository.pinList(123L)

        // Then
        coVerify { mockSync.requestSync() }
    }

    @Test
    fun `unpinList updates database optimistically`() = runTest {
        // When
        repository.unpinList(456L)

        // Then
        coVerify { mockListDao.updateIsPinned(456L, false) }
    }

    @Test
    fun `unpinList queues mutation for sync`() = runTest {
        // Given
        val mutationSlot = slot<MutationEntity>()

        // When
        repository.unpinList(456L)

        // Then
        coVerify { mockMutationDao.insert(capture(mutationSlot)) }

        val mutation = mutationSlot.captured
        assertEquals(MutationEntities.LIST, mutation.entity)
        assertEquals(456L, mutation.targetId)
        assertEquals(456L, mutation.listId)
        assertEquals(MutationTypes.UPDATE_PREFERENCES, mutation.type)

        // Verify payload contains isPinned=false
        val payload = json.decodeFromString<PinPayload>(mutation.payload)
        assertEquals(false, payload.isPinned)
    }

    @Test
    fun `unpinList requests sync`() = runTest {
        // When
        repository.unpinList(456L)

        // Then
        coVerify { mockSync.requestSync() }
    }

    @Test
    fun `pinList executes both operations in sequence`() = runTest {
        // When
        repository.pinList(789L)

        // Then - verify both database update and mutation insert were called
        coVerify(exactly = 1) { mockListDao.updateIsPinned(789L, true) }
        coVerify(exactly = 1) { mockMutationDao.insert(any()) }
        coVerify(exactly = 1) { mockSync.requestSync() }
    }

    @Test
    fun `unpinList executes both operations in sequence`() = runTest {
        // When
        repository.unpinList(999L)

        // Then - verify both database update and mutation insert were called
        coVerify(exactly = 1) { mockListDao.updateIsPinned(999L, false) }
        coVerify(exactly = 1) { mockMutationDao.insert(any()) }
        coVerify(exactly = 1) { mockSync.requestSync() }
    }
}
