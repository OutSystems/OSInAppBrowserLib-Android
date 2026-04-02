package com.outsystems.plugins.inappbrowser.osinappbrowserlib.routeradapters

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABEvents
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.RequiresEventBridgeRegistration
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers.OSIABFlowHelperInterface
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABWebViewOptions
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.views.OSIABWebViewActivity
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.views.OSIABWebViewActivitySharing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

class OSIABWebViewRouterAdapter(
    context: Context,
    lifecycleScope: CoroutineScope,
    options: OSIABWebViewOptions,
    flowHelper: OSIABFlowHelperInterface,
    onBrowserPageLoaded: () -> Unit,
    onBrowserFinished: () -> Unit,
    private val onBrowserPageNavigationCompleted: (String?) -> Unit,
    private val customHeaders: Map<String, String>? = null
) : OSIABBaseRouterAdapter<OSIABWebViewOptions, Boolean>(
    context = context,
    lifecycleScope = lifecycleScope,
    options = options,
    flowHelper = flowHelper,
    onBrowserPageLoaded = onBrowserPageLoaded,
    onBrowserFinished = onBrowserFinished,
) {
    private val browserId = UUID.randomUUID().toString()

    companion object {
        const val WEB_VIEW_URL_EXTRA = "WEB_VIEW_URL_EXTRA"
        const val WEB_VIEW_OPTIONS_EXTRA = "WEB_VIEW_OPTIONS_EXTRA"
        const val CUSTOM_HEADERS_EXTRA = "CUSTOM_HEADERS_EXTRA"
    }

    private var isFinished = false

    private fun finalizeBrowser() {
        if (!isFinished) {
            isFinished = true
            onBrowserFinished()
            OSIABEvents.unregisterReceiver(context)
        }
    }

    /**
     * Closes the WebView by sending a broadcast to the separate process.
     * The WebView activity will receive this and call finish() on itself.
     */
    @OptIn(RequiresEventBridgeRegistration::class)
    override fun close(completionHandler: (Boolean) -> Unit) {
        if (isFinished) {
            completionHandler(true)
            return
        }

        // Listen for the BrowserFinished event to confirm close
        var closeJob: Job? = null
        closeJob = flowHelper.listenToEvents(browserId, lifecycleScope) { event ->
            if (event is OSIABEvents.BrowserFinished) {
                finalizeBrowser()
                completionHandler(true)
                closeJob?.cancel()
            }
        }

        // Send close broadcast to the WebView process
        val closeIntent = Intent(OSIABEvents.ACTION_CLOSE_WEBVIEW).apply {
            setPackage(context.packageName)
            putExtra(OSIABEvents.EXTRA_BROWSER_ID, browserId)
        }
        context.sendBroadcast(closeIntent)
    }

    /**
     * Handles opening the passed `url` in the WebView.
     * @param url URL to be opened.
     * @param completionHandler The callback with the result of opening the url.
     */
    @OptIn(RequiresEventBridgeRegistration::class)
    override fun handleOpen(url: String, completionHandler: (Boolean) -> Unit) {
        lifecycleScope.launch {
            var eventsJob: Job? = null
            try {
                // Collect the browser events
                OSIABEvents.registerReceiver(context)
                eventsJob = flowHelper.listenToEvents(browserId, lifecycleScope) { event ->
                    when (event) {
                        is OSIABEvents.OSIABWebViewEvent -> {
                            completionHandler(true)
                        }
                        is OSIABEvents.BrowserPageLoaded -> {
                            onBrowserPageLoaded()
                        }
                        is OSIABEvents.BrowserFinished -> {
                            finalizeBrowser()
                            eventsJob?.cancel()
                        }
                        is OSIABEvents.BrowserPageNavigationCompleted -> {
                            onBrowserPageNavigationCompleted(event.url)
                        }
                        else -> {}
                    }
                }

                val activityClass = if (options.isIsolated) {
                    OSIABWebViewActivity::class.java
                } else {
                    OSIABWebViewActivitySharing::class.java
                }

                context.startActivity(
                    Intent(
                        context, activityClass
                    ).apply {
                        putExtra(OSIABEvents.EXTRA_BROWSER_ID, browserId)
                        putExtra(WEB_VIEW_URL_EXTRA, url)
                        putExtra(WEB_VIEW_OPTIONS_EXTRA, options)
                        putExtra(CUSTOM_HEADERS_EXTRA, Bundle().apply {
                            customHeaders?.forEach { (key, value) ->
                                putString(key, value)
                            }
                        })
                    }
                )

            } catch (e: Exception) {
                eventsJob?.cancel()
                OSIABEvents.unregisterReceiver(context)
                completionHandler(false)
            }
        }
    }
}
