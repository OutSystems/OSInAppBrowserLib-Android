package com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers

import android.content.Context
import androidx.browser.customtabs.CustomTabsSession
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABEvents

interface OSIABCustomTabsSessionHelperInterface {

    /**
     * Generates a new CustomTabsSession instance
     * @param context Context to use when initializing the CustomTabsSession
     * @param onEventReceived Callback to send the session events (e.g. navigation finished)
     * @param customTabsSessionCallback Callback to send the session instance (null if failed)
     */
    suspend fun generateNewCustomTabsSession(
        context: Context,
        onEventReceived: (OSIABEvents) -> Unit,
        customTabsSessionCallback: (CustomTabsSession?) -> Unit
    )
}