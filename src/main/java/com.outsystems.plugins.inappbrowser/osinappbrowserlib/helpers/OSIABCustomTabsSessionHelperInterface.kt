package com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers

import android.content.Context
import androidx.browser.customtabs.CustomTabsSession
import kotlinx.coroutines.CoroutineScope

interface OSIABCustomTabsSessionHelperInterface {

    /**
     * Generates a new CustomTabsSession instance
     * @param browserId Identifier for the browser instance to emit events to
     * @param context Context to use when initializing the CustomTabsSession
     * @param lifecycleScope Coroutine scope to use to post browser events
     * @param flowHelper Flow helper to listen to browser events
     * @param customTabsSessionCallback Callback to send the session instance (null if failed)
     */
    suspend fun generateNewCustomTabsSession(
        browserId: String,
        context: Context,
        lifecycleScope: CoroutineScope,
        flowHelper: OSIABFlowHelperInterface,
        customTabsSessionCallback: (CustomTabsSession?) -> Unit
    )
}