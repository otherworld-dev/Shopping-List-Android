package dev.otherworld.shoppinglist.data.prefs

/** What a fresh start opens on this phone (issues #16 and #20). */
sealed interface StartList {
    /** The lists screen. */
    data object AllLists : StartList

    /** The list opened last, the default. */
    data object LastUsed : StartList

    /** One list the user picked under Settings. */
    data class Chosen(val listId: Long) : StartList

    companion object {
        /** A chosen list wins; otherwise the old "open the last list" switch, so turning it off still counts. */
        fun fromStorage(openLastList: Boolean, chosenListId: Long?): StartList = when {
            chosenListId != null -> Chosen(chosenListId)
            openLastList -> LastUsed
            else -> AllLists
        }
    }
}

/** The same choice once a list made offline or on the phone has its server id. */
fun StartList.remapped(oldId: Long, newId: Long): StartList =
    if (this is StartList.Chosen && listId == oldId) StartList.Chosen(newId) else this

/** The same choice once [listId] is gone: a chosen list that went shows all lists, rather than some other list. */
fun StartList.forgetting(listId: Long): StartList =
    if (this is StartList.Chosen && this.listId == listId) StartList.AllLists else this
