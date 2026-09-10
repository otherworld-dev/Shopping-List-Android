package dev.otherworld.shoppinglist.ui.lists

import dev.otherworld.shoppinglist.data.auth.CredentialStore
import dev.otherworld.shoppinglist.data.repo.ListRepository
import dev.otherworld.shoppinglist.data.sync.ConnectivityObserver
import dev.otherworld.shoppinglist.data.sync.RealtimeController
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Tests that ListsViewModel correctly delegates pin/unpin calls to the repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListsViewModelPinTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ListsViewModel
    private val mockRepository: ListRepository = mockk(relaxed = true)
    private val mockCredentialStore: CredentialStore = mockk()
    private val mockConnectivity: ConnectivityObserver = mockk()
    private val mockRealtime: RealtimeController = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Setup mocks
        every { mockCredentialStore.current() } returns null
        every { mockRealtime.events } returns MutableSharedFlow()
        every { mockRepository.observeLists() } returns flowOf(emptyList())

        viewModel = ListsViewModel(
            repository = mockRepository,
            credentialStore = mockCredentialStore,
            connectivity = mockConnectivity,
            realtime = mockRealtime,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pinList calls repository pinList`() = runTest {
        // When
        viewModel.pinList(123L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { mockRepository.pinList(123L) }
    }

    @Test
    fun `unpinList calls repository unpinList`() = runTest {
        // When
        viewModel.unpinList(456L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { mockRepository.unpinList(456L) }
    }

    @Test
    fun `pinList multiple times calls repository each time`() = runTest {
        // When
        viewModel.pinList(100L)
        viewModel.pinList(200L)
        viewModel.pinList(300L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { mockRepository.pinList(100L) }
        coVerify { mockRepository.pinList(200L) }
        coVerify { mockRepository.pinList(300L) }
    }

    @Test
    fun `unpinList multiple times calls repository each time`() = runTest {
        // When
        viewModel.unpinList(100L)
        viewModel.unpinList(200L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { mockRepository.unpinList(100L) }
        coVerify { mockRepository.unpinList(200L) }
    }

    @Test
    fun `pinList and unpinList on same list calls repository correctly`() = runTest {
        // When
        viewModel.pinList(999L)
        viewModel.unpinList(999L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify(exactly = 1) { mockRepository.pinList(999L) }
        coVerify(exactly = 1) { mockRepository.unpinList(999L) }
    }
}
