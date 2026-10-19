package dev.otherworld.shoppinglist.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class StartListTest {

    @Test
    fun `nothing saved reopens the list used last, as before`() {
        assertEquals(StartList.LastUsed, StartList.fromStorage(openLastList = true, chosenListId = null))
    }

    @Test
    fun `the old switch turned off still shows all lists`() {
        assertEquals(StartList.AllLists, StartList.fromStorage(openLastList = false, chosenListId = null))
    }

    @Test
    fun `a chosen list is read back`() {
        assertEquals(StartList.Chosen(12L), StartList.fromStorage(openLastList = true, chosenListId = 12L))
    }

    @Test
    fun `a chosen list follows its temp id to the real one`() {
        assertEquals(StartList.Chosen(42L), StartList.Chosen(-3L).remapped(oldId = -3L, newId = 42L))
    }

    @Test
    fun `another list's new id leaves the choice alone`() {
        assertEquals(StartList.Chosen(5L), StartList.Chosen(5L).remapped(oldId = -3L, newId = 42L))
    }

    @Test
    fun `a chosen list that's gone falls back to all lists`() {
        assertEquals(StartList.AllLists, StartList.Chosen(5L).forgetting(5L))
    }

    @Test
    fun `another list going leaves the choice alone`() {
        assertEquals(StartList.Chosen(5L), StartList.Chosen(5L).forgetting(6L))
    }

    @Test
    fun `a list going doesn't change the last-used setting`() {
        assertEquals(StartList.LastUsed, StartList.LastUsed.forgetting(5L))
    }
}
