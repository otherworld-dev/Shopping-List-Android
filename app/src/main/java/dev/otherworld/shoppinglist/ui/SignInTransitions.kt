package dev.otherworld.shoppinglist.ui

enum class NavigationAction { TO_HOME, TO_LOGIN }

/**
 * Where to go when the login or the guest lists change. Signed in means a Nextcloud login or at
 * least one guest list; logging in always leaves the login screen, even in guest mode.
 */
class SignInTransitions {
    private var wasLoggedIn: Boolean? = null
    private var wasSignedIn: Boolean? = null

    fun next(loggedIn: Boolean, hasGuests: Boolean): NavigationAction? {
        val signedIn = loggedIn || hasGuests
        val previousLogin = wasLoggedIn
        val previousSignedIn = wasSignedIn
        wasLoggedIn = loggedIn
        wasSignedIn = signedIn
        return when {
            previousLogin == null -> null
            !previousLogin && loggedIn -> NavigationAction.TO_HOME
            previousSignedIn == true && !signedIn -> NavigationAction.TO_LOGIN
            previousSignedIn == false && signedIn -> NavigationAction.TO_HOME
            else -> null
        }
    }
}
