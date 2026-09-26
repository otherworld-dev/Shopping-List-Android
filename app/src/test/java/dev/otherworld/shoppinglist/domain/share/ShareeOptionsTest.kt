package dev.otherworld.shoppinglist.domain.share

import dev.otherworld.shoppinglist.data.remote.dto.ShareesResponse
import dev.otherworld.shoppinglist.domain.model.ShareType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** The share search, as the web app's `ShareDialog.vue` builds its options (1.9.0). */
class ShareeOptionsTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private fun decode(body: String) = json.decodeFromString(ShareesResponse.serializer(), body)

    // Shaped like files_sharing's /sharees answer: exact matches kept apart from the rest.
    private val response = decode(
        """
        {
          "exact": {
            "users": [{"label": "Anna Smith", "value": {"shareType": 0, "shareWith": "anna"}}],
            "groups": [{"label": "annas", "value": {"shareType": 1, "shareWith": "annas"}}],
            "remotes": [], "emails": []
          },
          "users": [
            {"label": "Annabel Jones", "value": {"shareType": 0, "shareWith": "annabel"}},
            {"label": "Adam Morgan", "value": {"shareType": 0, "shareWith": "adam"}}
          ],
          "groups": [{"label": "Family", "value": {"shareType": 1, "shareWith": "family"}}],
          "remotes": [], "lookup": [], "lookupEnabled": false
        }
        """,
    )

    @Test
    fun `lists users before groups with exact matches first in each`() {
        assertEquals(
            listOf("anna", "annabel", "adam", "annas", "family"),
            shareeOptions(response, selfId = "someone-else").map { it.shareWith },
        )
    }

    @Test
    fun `carries the label and share type`() {
        val options = shareeOptions(response, selfId = "someone-else")
        assertEquals(ShareeOption("Anna Smith", "anna", ShareType.USER), options.first())
        assertEquals(ShareeOption("Family", "family", ShareType.GROUP), options.last())
    }

    @Test
    fun `leaves out the signed-in user whatever the case`() {
        assertEquals(
            listOf("anna", "annabel", "annas", "family"),
            shareeOptions(response, selfId = "Adam").map { it.shareWith },
        )
    }

    @Test
    fun `lists someone once when they are both an exact and a partial match`() {
        val repeated = decode(
            """
            {"exact": {"users": [{"label": "Anna", "value": {"shareType": 0, "shareWith": "anna"}}]},
             "users": [{"label": "Anna", "value": {"shareType": 0, "shareWith": "anna"}}]}
            """,
        )
        assertEquals(listOf("anna"), shareeOptions(repeated, selfId = "adam").map { it.shareWith })
    }

    @Test
    fun `reads an answer with nothing found`() {
        val empty = decode("""{"exact": {"users": [], "groups": []}, "users": [], "groups": []}""")
        assertEquals(emptyList<ShareeOption>(), shareeOptions(empty, selfId = "adam"))
    }
}
