@file:OptIn(com.outsystems.plugins.inappbrowser.osinappbrowserlib.RequiresEventBridgeRegistration::class)

package com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABEvents
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.views.OSIABCustomTabsControllerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "OSIABSession"

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
        flowHelper: OSIABFlowHelperInterface,
        customTabsSessionCallback: (CustomTabsSession?) -> Unit
    ) {
        CustomTabsClient.bindCustomTabsService(
            context,
            packageName,
            object : CustomTabsServiceConnection() {
                override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
                    client.warmup(0L)
                    customTabsSessionCallback(
                        client.newSession(CustomTabsCallbackImpl(browserId, lifecycleScope, flowHelper))
                    )
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    context.unbindService(this)
                    customTabsSessionCallback(null)
                }
            }
        )
    }

    private inner class CustomTabsCallbackImpl(
        private val browserId: String,
        private val lifecycleScope: CoroutineScope,
        flowHelper: OSIABFlowHelperInterface,
    ) : CustomTabsCallback() {

        init {
            var browserEventsJob: Job? = null
            browserEventsJob = flowHelper.listenToEvents(browserId, lifecycleScope) { event ->
                if (event is OSIABEvents.OSIABCustomTabsEvent
                    && event.action == OSIABCustomTabsControllerActivity.EVENT_CUSTOM_TABS_DESTROYED) {
                    browserEventsJob?.cancel()
                }
            }
        }

        override fun onNavigationEvent(navigationEvent: Int, extras: Bundle?) {
            super.onNavigationEvent(navigationEvent, extras)
            Log.d(TAG, "onNavigationEvent: code=$navigationEvent (${navEventName(navigationEvent)})")
            if (navigationEvent == NAVIGATION_FINISHED) {
                lifecycleScope.launch {
                    OSIABEvents.postEvent(OSIABEvents.BrowserPageLoaded(browserId))
                }
            }
        }

        private fun navEventName(code: Int): String = when (code) {
            NAVIGATION_STARTED -> "NAVIGATION_STARTED"
            NAVIGATION_FINISHED -> "NAVIGATION_FINISHED"
            NAVIGATION_FAILED -> "NAVIGATION_FAILED"
            NAVIGATION_ABORTED -> "NAVIGATION_ABORTED"
            TAB_SHOWN -> "TAB_SHOWN"
            TAB_HIDDEN -> "TAB_HIDDEN"
            else -> "UNKNOWN"
        }
    }

    override suspend fun generateNewCustomTabsSession(
        browserId: String,
        context: Context,
        lifecycleScope: CoroutineScope,
        flowHelper: OSIABFlowHelperInterface,
        customTabsSessionCallback: (CustomTabsSession?) -> Unit
    ) {
        val packageName = getDefaultCustomTabsPackageName(context)
        packageName?.let {
            initializeCustomTabsSession(
                browserId,
                context,
                it,
                lifecycleScope,
                flowHelper,
                customTabsSessionCallback
            )
        } ?: customTabsSessionCallback(null)
    }
}
