package com.outsystems.plugins.inappbrowser.osinappbrowserlib.routeradapters

import android.content.Context
import android.content.Intent
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABEvents
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers.OSIABFlowHelperInterface
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABWebViewOptions
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.views.OSIABWebViewActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.UUID

class OSIABWebViewRouterAdapter(
    context: Context,
    lifecycleScope: CoroutineScope,
    options: OSIABWebViewOptions,
    flowHelper: OSIABFlowHelperInterface,
    onBrowserPageLoaded: () -> Unit,
    onBrowserFinished: () -> Unit,
    onBrowserPageNavigationCompleted: (String?) -> Unit
) : OSIABBaseRouterAdapter<OSIABWebViewOptions, Boolean>(
    context = context,
    lifecycleScope = lifecycleScope,
    options = options,
    flowHelper = flowHelper,
    onBrowserPageLoaded = onBrowserPageLoaded,
    onBrowserFinished = onBrowserFinished,
    onBrowserPageNavigationCompleted = onBrowserPageNavigationCompleted
) {
    private val browserId = UUID.randomUUID().toString()

    companion object {
        const val WEB_VIEW_URL_EXTRA = "WEB_VIEW_URL_EXTRA"
        const val WEB_VIEW_OPTIONS_EXTRA = "WEB_VIEW_OPTIONS_EXTRA"
    }

    private var webViewActivityRef: WeakReference<OSIABWebViewActivity>? = null

    private fun setWebViewActivity(activity: OSIABWebViewActivity?) {
        webViewActivityRef = if (activity == null) {
            null
        } else {
            WeakReference(activity)
        }
    }

    private fun getWebViewActivity(): OSIABWebViewActivity? {
        return webViewActivityRef?.get()
    }

    override fun close(completionHandler: (Boolean) -> Unit) {
        getWebViewActivity().let { activity ->
            if(activity == null) {
                completionHandler(false)
            }
            else {
                activity.finish()
                setWebViewActivity(null)
                onBrowserFinished()
                completionHandler(true)
            }
        }
    }

    /**
     * Handles opening the passed `url` in the WebView.
     * @param url URL to be opened.
     * @param completionHandler The callback with the result of opening the url.
     */
    override fun handleOpen(url: String, completionHandler: (Boolean) -> Unit) {
        lifecycleScope.launch {
            try {
                // Collect the browser events
                var eventsJob: Job? = null
                eventsJob = flowHelper.listenToEvents(browserId, lifecycleScope) { event ->
                    when (event) {
                        is OSIABEvents.OSIABWebViewEvent -> {
                            setWebViewActivity(event.activity)
                            completionHandler(true)
                        }
                        is OSIABEvents.BrowserPageLoaded -> {
                            onBrowserPageLoaded()
                        }
                        is OSIABEvents.BrowserFinished -> {
                            setWebViewActivity(null)
                            onBrowserFinished()
                            eventsJob?.cancel()
                        }
                        is OSIABEvents.BrowserPageNavigationCompleted -> {
                            onBrowserPageNavigationCompleted(event.url)
                        }
                        else -> {}
                    }
                }

                context.startActivity(
                    Intent(
                        context, OSIABWebViewActivity::class.java
                    ).apply {
                        putExtra(OSIABEvents.EXTRA_BROWSER_ID, browserId)
                        putExtra(WEB_VIEW_URL_EXTRA, url)
                        putExtra(WEB_VIEW_OPTIONS_EXTRA, options)
                    }
                )

            } catch (e: Exception) {
                completionHandler(false)
            }
        }
    }
}
