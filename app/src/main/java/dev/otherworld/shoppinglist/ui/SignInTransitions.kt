package dev.otherworld.shoppinglist.ui

enum class NavigationAction { TO_HOME, TO_LOGIN }

/**
 * Where to go when the login, the guest lists or local mode change. Signed in means a Nextcloud
 * login, at least one guest list, or having chosen to use the app without an account; logging
 * in or choosing local mode always leaves the login screen, even in guest mode.
 */
class SignInTransitions {
    private var wasLoggedIn: Boolean? = null
    private var wasSignedIn: Boolean? = null
    private var wasLocalMode: Boolean? = null

    fun next(loggedIn: Boolean, hasGuests: Boolean, localMode: Boolean = false): NavigationAction? {
        val signedIn = loggedIn || hasGuests || localMode
        val previousLogin = wasLoggedIn
        val previousSignedIn = wasSignedIn
        val previousLocalMode = wasLocalMode
        wasLoggedIn = loggedIn
        wasSignedIn = signedIn
        wasLocalMode = localMode
        return when {
            previousLogin == null -> null
            !previousLogin && loggedIn -> NavigationAction.TO_HOME
            previousLocalMode == false && localMode -> NavigationAction.TO_HOME
            previousSignedIn == true && !signedIn -> NavigationAction.TO_LOGIN
            previousSignedIn == false && signedIn -> NavigationAction.TO_HOME
            else -> null
        }
    }
}
