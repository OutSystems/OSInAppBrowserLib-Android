package com.outsystems.plugins.inappbrowser.osinappbrowserlib.routeradapters

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABRouter
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABEvents
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABWebViewOptions
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.views.OSIABWebViewActivity

class OSIABWebViewRouterAdapter(
    private val context: Context,
    private val options: OSIABWebViewOptions,
    private val onBrowserPageLoaded: () -> Unit,
    private val onBrowserFinished: () -> Unit
) : OSIABRouter<Boolean> {

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                OSIABEvents.ACTION_BROWSER_PAGE_LOADED -> onBrowserPageLoaded()
                OSIABEvents.ACTION_BROWSER_FINISHED -> onBrowserFinished()
            }
        }
    }

    companion object {
        const val WEB_VIEW_URL_EXTRA = "WEB_VIEW_URL_EXTRA"
        const val WEB_VIEW_OPTIONS_EXTRA = "WEB_VIEW_OPTIONS_EXTRA"
    }

    init {
        val intentFilter = IntentFilter().apply {
            addAction(OSIABEvents.ACTION_BROWSER_PAGE_LOADED)
            addAction(OSIABEvents.ACTION_BROWSER_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(broadcastReceiver, intentFilter)
        }
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
        } catch (e: Exception) {
            completionHandler(false)
            notifyBrowserPageLoaded()
        }
    }

    private fun notifyBrowserPageLoaded() {
        onBrowserPageLoaded()
    }

}