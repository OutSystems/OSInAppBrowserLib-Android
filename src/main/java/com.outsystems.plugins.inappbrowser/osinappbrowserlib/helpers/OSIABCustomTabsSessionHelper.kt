package com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class OSIABCustomTabsSessionHelper: OSIABCustomTabsSessionHelperInterface {
    private fun getDefaultCustomTabsPackageName(context: Context): String? {
        val activityIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
        val resolvedActivityList = context.packageManager.queryIntentActivities(activityIntent, PackageManager.MATCH_ALL)
        return CustomTabsClient.getPackageName(
            context,
            resolvedActivityList.map { it.activityInfo.packageName },
            false
        )
    }

    private fun initializeCustomTabsSession(
        browserId: String,
        context: Context,
        packageName: String,
        lifecycleScope: CoroutineScope,
        customTabsSessionCallback: (CustomTabsSession?) -> Unit
    ) {
        CustomTabsClient.bindCustomTabsService(
            context,
            packageName,
            object : CustomTabsServiceConnection() {
                override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
                    client.warmup(0L)
                    customTabsSessionCallback(
                        client.newSession(CustomTabsCallbackImpl(browserId, lifecycleScope))
                    )
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    customTabsSessionCallback(null)
                }
            }
        )
    }

    private inner class CustomTabsCallbackImpl(
        private val browserId: String,
        private val lifecycleScope: CoroutineScope
    ) : CustomTabsCallback() {
        override fun onNavigationEvent(navigationEvent: Int, extras: Bundle?) {
            super.onNavigationEvent(navigationEvent, extras)
            val browserEvent = when (navigationEvent) {
                NAVIGATION_FINISHED -> OSIABEvents.BrowserPageLoaded(browserId)
                TAB_HIDDEN -> OSIABEvents.BrowserFinished(browserId)
                else -> return
            }
            lifecycleScope.launch {
                OSIABEvents.postEvent(browserEvent)
            }
        }
    }

    override suspend fun generateNewCustomTabsSession(
        browserId: String,
        context: Context,
        lifecycleScope: CoroutineScope,
        customTabsSessionCallback: (CustomTabsSession?) -> Unit
    ) {
        val packageName = getDefaultCustomTabsPackageName(context)
        packageName?.let {
            initializeCustomTabsSession(
                browserId,
                context,
                it,
                lifecycleScope,
                customTabsSessionCallback
            )
        } ?: customTabsSessionCallback(null)
    }
}