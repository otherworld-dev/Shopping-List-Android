package dev.otherworld.shoppinglist.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignInTransitionsTest {

    @Test
    fun `the first state never navigates`() {
        assertNull(SignInTransitions().next(loggedIn = true, hasGuests = false))
    }

    @Test
    fun `logging in goes to the lists, logging out with no guest lists goes to login`() {
        val t = SignInTransitions()
        t.next(loggedIn = false, hasGuests = false)
        assertEquals(NavigationAction.TO_HOME, t.next(loggedIn = true, hasGuests = false))
        assertEquals(NavigationAction.TO_LOGIN, t.next(loggedIn = false, hasGuests = false))
    }

    @Test
    fun `logging out with guest lists stays on the lists`() {
        val t = SignInTransitions()
        t.next(loggedIn = true, hasGuests = true)
        assertNull(t.next(loggedIn = false, hasGuests = true))
    }

    @Test
    fun `leaving the last guest list with no login goes to login`() {
        val t = SignInTransitions()
        t.next(loggedIn = false, hasGuests = true)
        assertEquals(NavigationAction.TO_LOGIN, t.next(loggedIn = false, hasGuests = false))
    }

    @Test
    fun `the first guest list goes to the lists`() {
        val t = SignInTransitions()
        t.next(loggedIn = false, hasGuests = false)
        assertEquals(NavigationAction.TO_HOME, t.next(loggedIn = false, hasGuests = true))
    }

    @Test
    fun `logging in from guest mode goes to the lists`() {
        val t = SignInTransitions()
        t.next(loggedIn = false, hasGuests = true)
        assertEquals(NavigationAction.TO_HOME, t.next(loggedIn = true, hasGuests = true))
    }

    @Test
    fun `choosing to use the app without an account goes to the lists`() {
        val t = SignInTransitions()
        t.next(loggedIn = false, hasGuests = false, localMode = false)
        assertEquals(NavigationAction.TO_HOME, t.next(loggedIn = false, hasGuests = false, localMode = true))
    }

    @Test
    fun `logging in from local mode goes to the lists`() {
        val t = SignInTransitions()
        t.next(loggedIn = false, hasGuests = false, localMode = true)
        assertEquals(NavigationAction.TO_HOME, t.next(loggedIn = true, hasGuests = false, localMode = true))
    }

    @Test
    fun `logging out in local mode stays on the lists`() {
        val t = SignInTransitions()
        t.next(loggedIn = true, hasGuests = false, localMode = true)
        assertNull(t.next(loggedIn = false, hasGuests = false, localMode = true))
    }

    @Test
    fun `choosing local mode from guest mode goes to the lists`() {
        val t = SignInTransitions()
        t.next(loggedIn = false, hasGuests = true, localMode = false)
        assertEquals(NavigationAction.TO_HOME, t.next(loggedIn = false, hasGuests = true, localMode = true))
    }
}
