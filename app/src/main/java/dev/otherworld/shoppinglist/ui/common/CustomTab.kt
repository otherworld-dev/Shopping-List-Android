package dev.otherworld.shoppinglist.ui.common

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

/** Opens [url] in a Chrome Custom Tab, falling back to the system browser. */
fun openCustomTab(context: Context, url: String) {
    val intent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
    runCatching { intent.launchUrl(context, Uri.parse(url)) }
}
