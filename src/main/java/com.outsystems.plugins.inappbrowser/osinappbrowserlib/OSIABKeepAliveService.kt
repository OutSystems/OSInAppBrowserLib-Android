package com.outsystems.plugins.inappbrowser.osinappbrowserlib

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

/**
 * Runs in the main app process. While the isolated WebView activity is bound to it,
 * the main process is not eligible for OS app freezing, so it keeps processing
 * browser events while the WebView is in the foreground.
 */
class OSIABKeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder = Binder()
}
