package com.outsystems.plugins.inappbrowser.osinappbrowserlib.routeradapters

import android.content.Context
import android.content.Intent
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABEvents
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABRouter
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers.OSIABFlowHelperInterface
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABWebViewOptions
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.views.OSIABWebViewActivity
import kotlinx.coroutines.CoroutineScope

class OSIABWebViewRouterAdapter(
    private val context: Context,
    private val lifecycleScope: CoroutineScope,
    private val options: OSIABWebViewOptions,
    private val flowHelper: OSIABFlowHelperInterface,
    private val onBrowserPageLoaded: () -> Unit,
    private val onBrowserFinished: () -> Unit
) : OSIABRouter<Boolean> {

    companion object {
        const val WEB_VIEW_URL_EXTRA = "WEB_VIEW_URL_EXTRA"
        const val WEB_VIEW_OPTIONS_EXTRA = "WEB_VIEW_OPTIONS_EXTRA"
    }

    /**
     * Handles opening the passed `url` in the WebView.
     * @param url URL to be opened.
     * @param completionHandler The callback with the result of opening the url.
     */
    override fun handleOpen(url: String, completionHandler: (Boolean) -> Unit) {
        try {
            context.startActivity(
                Intent(
                    context, OSIABWebViewActivity::class.java
                ).apply {
                    putExtra(WEB_VIEW_URL_EXTRA, url)
                    putExtra(WEB_VIEW_OPTIONS_EXTRA, options)
                }
            )
            completionHandler(true)

            // Collect the browser events
            flowHelper.listenToEvents(lifecycleScope) { event ->
                when (event) {
                    is OSIABEvents.BrowserPageLoaded -> { onBrowserPageLoaded() }
                    is OSIABEvents.BrowserFinished -> { onBrowserFinished() }
                }
            }

        } catch (e: Exception) {
            completionHandler(false)
        }
    }
}