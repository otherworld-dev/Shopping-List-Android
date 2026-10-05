package dev.otherworld.shoppinglist.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadOfferTest {

    @Test
    fun `logging in with lists on the phone offers the upload`() {
        assertTrue(offersUpload(wasLoggedIn = false, loggedIn = true, localLists = 2))
    }

    @Test
    fun `an account already there at start-up doesn't`() {
        assertFalse(offersUpload(wasLoggedIn = null, loggedIn = true, localLists = 2))
    }

    @Test
    fun `logging in with nothing on the phone doesn't`() {
        assertFalse(offersUpload(wasLoggedIn = false, loggedIn = true, localLists = 0))
    }

    @Test
    fun `logging out doesn't`() {
        assertFalse(offersUpload(wasLoggedIn = true, loggedIn = false, localLists = 2))
    }
}
