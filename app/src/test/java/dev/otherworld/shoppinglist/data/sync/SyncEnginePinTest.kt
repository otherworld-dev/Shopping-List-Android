package dev.otherworld.shoppinglist.data.sync

import dev.otherworld.shoppinglist.data.local.AppDatabase
import dev.otherworld.shoppinglist.data.local.AreaDao
import dev.otherworld.shoppinglist.data.local.ItemDao
import dev.otherworld.shoppinglist.data.local.ListDao
import dev.otherworld.shoppinglist.data.local.MutationDao
import dev.otherworld.shoppinglist.data.local.MutationEntity
import dev.otherworld.shoppinglist.data.remote.OcsService
import dev.otherworld.shoppinglist.data.remote.dto.UpdatePreferencesRequest
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test

/**
 * Tests that SyncEngine correctly processes UPDATE_PREFERENCES mutations for pin/unpin.
 */
class SyncEnginePinTest {
    private lateinit var syncEngine: SyncEngine
    private val mockService: OcsService = mockk()
    private val mockDb: AppDatabase = mockk()
    private val mockListDao: ListDao = mockk()
    private val mockItemDao: ItemDao = mockk()
    private val mockAreaDao: AreaDao = mockk()
    private val mockMutationDao: MutationDao = mockk()
    private val mockConnectivity: ConnectivityObserver = mockk()
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        coEvery { mockDb.listDao() } returns mockListDao
        coEvery { mockDb.itemDao() } returns mockItemDao
        coEvery { mockDb.areaDao() } returns mockAreaDao
        coEvery { mockDb.mutationDao() } returns mockMutationDao
        coEvery { mockConnectivity.isOnline } returns MutableStateFlow(true)

        syncEngine = SyncEngine(
            service = mockService,
            db = mockDb,
            connectivity = mockConnectivity,
            json = json,
        )
    }

    @Test
    fun `UPDATE_PREFERENCES mutation calls updatePreferences endpoint with isPinned true`() = runTest {
        // Given
        val mutation = MutationEntity(
            seq = 1,
            entity = MutationEntities.LIST,
            type = MutationTypes.UPDATE_PREFERENCES,
            targetId = 123L,
            listId = 123L,
            payload = json.encodeToString(PinPayload.serializer(), PinPayload(true)),
        )

        coEvery { mockService.updatePreferences(any(), any()) } just Runs
        coEvery { mockMutationDao.deleteBySeq(any()) } just Runs
        coEvery { mockMutationDao.oldest() } returns mutation andThen null

        // When
        syncEngine.drain()

        // Then
        coVerify {
            mockService.updatePreferences(
                id = 123L,
                body = UpdatePreferencesRequest(isPinned = true)
            )
        }
    }

    @Test
    fun `UPDATE_PREFERENCES mutation calls updatePreferences endpoint with isPinned false`() = runTest {
        // Given
        val mutation = MutationEntity(
            seq = 2,
            entity = MutationEntities.LIST,
            type = MutationTypes.UPDATE_PREFERENCES,
            targetId = 456L,
            listId = 456L,
            payload = json.encodeToString(PinPayload.serializer(), PinPayload(false)),
        )

        coEvery { mockService.updatePreferences(any(), any()) } just Runs
        coEvery { mockMutationDao.deleteBySeq(any()) } just Runs
        coEvery { mockMutationDao.oldest() } returns mutation andThen null

        // When
        syncEngine.drain()

        // Then
        coVerify {
            mockService.updatePreferences(
                id = 456L,
                body = UpdatePreferencesRequest(isPinned = false)
            )
        }
    }

    @Test
    fun `UPDATE_PREFERENCES mutation is deleted after successful sync`() = runTest {
        // Given
        val mutation = MutationEntity(
            seq = 3,
            entity = MutationEntities.LIST,
            type = MutationTypes.UPDATE_PREFERENCES,
            targetId = 789L,
            listId = 789L,
            payload = json.encodeToString(PinPayload.serializer(), PinPayload(true)),
        )

        coEvery { mockService.updatePreferences(any(), any()) } just Runs
        coEvery { mockMutationDao.deleteBySeq(any()) } just Runs
        coEvery { mockMutationDao.oldest() } returns mutation andThen null

        // When
        syncEngine.drain()

        // Then
        coVerify { mockMutationDao.deleteBySeq(3) }
    }

    @Test
    fun `multiple UPDATE_PREFERENCES mutations are processed in sequence`() = runTest {
        // Given
        val mutation1 = MutationEntity(
            seq = 1,
            entity = MutationEntities.LIST,
            type = MutationTypes.UPDATE_PREFERENCES,
            targetId = 100L,
            listId = 100L,
            payload = json.encodeToString(PinPayload.serializer(), PinPayload(true)),
        )

        val mutation2 = MutationEntity(
            seq = 2,
            entity = MutationEntities.LIST,
            type = MutationTypes.UPDATE_PREFERENCES,
            targetId = 200L,
            listId = 200L,
            payload = json.encodeToString(PinPayload.serializer(), PinPayload(false)),
        )

        coEvery { mockService.updatePreferences(any(), any()) } just Runs
        coEvery { mockMutationDao.deleteBySeq(any()) } just Runs
        coEvery { mockMutationDao.oldest() } returns mutation1 andThen mutation2 andThen null

        // When
        syncEngine.drain()

        // Then
        coVerify(exactly = 1) {
            mockService.updatePreferences(100L, UpdatePreferencesRequest(isPinned = true))
        }
        coVerify(exactly = 1) {
            mockService.updatePreferences(200L, UpdatePreferencesRequest(isPinned = false))
        }
        coVerify { mockMutationDao.deleteBySeq(1) }
        coVerify { mockMutationDao.deleteBySeq(2) }
    }
}
