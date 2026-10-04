package dev.otherworld.shoppinglist.data.repo

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

// The "Show my name on items" switch: shown at once, sent straight to the server.
class OptimisticSettingTest {

    private val saved = mutableListOf<Boolean>()
    private val setting = OptimisticSetting(initial = false) { saved += it }

    @Test
    fun `a change shows at once and is kept once the server has it`() = runTest {
        setting.set(true) { }
        assertTrue(setting.value.value)
        assertEquals(listOf(true), saved)
    }

    @Test
    fun `a change the server never got goes back, and says why`() = runTest {
        val failure = runCatching { setting.set(true) { throw IOException("offline") } }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertFalse(setting.value.value)
        assertEquals(emptyList<Boolean>(), saved)
    }

    @Test
    fun `asking for what it already is sends nothing`() = runTest {
        var sent = false
        setting.set(false) { sent = true }
        assertFalse(sent)
    }

    // The bug: a refresh that read the old value before the change landed must not undo it.
    @Test
    fun `a refresh that started before a change doesn't undo it`() = runTest {
        val token = setting.beginRefresh()
        setting.set(true) { }
        setting.applyRefresh(server = false, token = token)
        assertTrue(setting.value.value)
    }

    @Test
    fun `a refresh with nothing changed meanwhile takes the server's value`() = runTest {
        val token = setting.beginRefresh()
        setting.applyRefresh(server = true, token = token)
        assertTrue(setting.value.value)
        assertEquals(listOf(true), saved)
    }

    // A failed change mustn't put back its old value over a newer one.
    @Test
    fun `a failed change leaves a newer value alone`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val first = launch { runCatching { setting.set(true) { gate.await(); throw IOException("offline") } } }
        testScheduler.runCurrent()
        val token = setting.beginRefresh()
        setting.applyRefresh(server = true, token = token)
        gate.complete(Unit)
        first.join()
        assertTrue(setting.value.value)
    }
}
