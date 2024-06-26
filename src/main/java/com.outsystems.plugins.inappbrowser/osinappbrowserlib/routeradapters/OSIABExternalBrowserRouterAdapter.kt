package com.outsystems.plugins.inappbrowser.osinappbrowserlib.routeradapters

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Browser.EXTRA_APPLICATION_ID
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABRouter
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.canOpenURL

class OSIABExternalBrowserRouterAdapter(private val context: Context) : OSIABRouter<Boolean> {
    override fun handleOpen(url: String, completionHandler: (Boolean) -> Unit) {
        try {
            val uri = Uri.parse(url)
            if (!context.canOpenURL(uri)) {
                completionHandler(false)
                return
            }
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.putExtra(EXTRA_APPLICATION_ID, context.packageName)
            context.startActivity(intent)
            completionHandler(true)
        } catch (e: Exception) {
            completionHandler(false)
        }
    }
}