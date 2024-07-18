package com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers

import android.content.ComponentName
import android.content.Context
import androidx.browser.customtabs.CustomTabsSession
import kotlinx.coroutines.CoroutineScope

class OSIABCustomTabsSessionHelperMock: OSIABCustomTabsSessionHelperInterface {
    private val componentName = "OSIABTestComponent"

    override suspend fun generateNewCustomTabsSession(
        browserId: String,
        context: Context,
        lifecycleScope: CoroutineScope,
        flowHelper: OSIABFlowHelperInterface,
        customTabsSessionCallback: (CustomTabsSession?) -> Unit
    ) {
        customTabsSessionCallback(CustomTabsSession.createMockSessionForTesting(ComponentName(context, componentName)))
    }
}