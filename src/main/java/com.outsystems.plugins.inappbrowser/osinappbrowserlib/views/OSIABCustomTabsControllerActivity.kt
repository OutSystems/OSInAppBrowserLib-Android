package com.outsystems.plugins.inappbrowser.osinappbrowserlib.views

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABEvents
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABEvents.OSIABCustomTabsEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch


class OSIABCustomTabsControllerActivity: AppCompatActivity() {
    companion object {
        const val EVENT_CUSTOM_TABS_DESTROYED = "com.outsystems.plugins.inappbrowser.osinappbrowserlib.EVENT_CUSTOM_TABS_DESTROYED"
        const val EVENT_CUSTOM_TABS_READY = "com.outsystems.plugins.inappbrowser.osinappbrowserlib.EVENT_CUSTOM_TABS_READY"
        const val EVENT_CUSTOM_TABS_PAUSED = "com.outsystems.plugins.inappbrowser.osinappbrowserlib.EVENT_CUSTOM_TABS_PAUSED"
        const val EVENT_CUSTOM_TABS_RESUMED = "com.outsystems.plugins.inappbrowser.osinappbrowserlib.EVENT_CUSTOM_TABS_RESUMED"
        const val ACTION_CLOSE_CUSTOM_TABS = "com.outsystems.plugins.inappbrowser.osinappbrowserlib.ACTION_CLOSE_CUSTOM_TABS"
    }

    private fun setup(intent: Intent) {
        window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

        if (intent.getBooleanExtra(ACTION_CLOSE_CUSTOM_TABS, false)) {
            finish()
        }
        else {
            intent.getStringExtra(OSIABEvents.EXTRA_BROWSER_ID)?.let { browserId ->
                sendCustomTabsEvent(lifecycleScope, browserId, EVENT_CUSTOM_TABS_READY)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setup(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setup(intent)
    }

    override fun onPause() {
        super.onPause()
        intent.getStringExtra(OSIABEvents.EXTRA_BROWSER_ID)?.let { browserId ->
            sendCustomTabsEvent(lifecycleScope, browserId, EVENT_CUSTOM_TABS_PAUSED)
        }
    }

    override fun onResume() {
        super.onResume()
        intent.getStringExtra(OSIABEvents.EXTRA_BROWSER_ID)?.let { browserId ->
            sendCustomTabsEvent(lifecycleScope, browserId, EVENT_CUSTOM_TABS_RESUMED)
        }
    }

    override fun onDestroy() {
        intent.getStringExtra(OSIABEvents.EXTRA_BROWSER_ID)?.let { browserId ->
            val customScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val deferred = CompletableDeferred<Unit>()
            sendCustomTabsEvent(customScope, browserId, EVENT_CUSTOM_TABS_DESTROYED, deferred)
        }
        super.onDestroy()
    }

    private fun sendCustomTabsEvent(
        scope: CoroutineScope,
        browserId: String,
        action: String,
        deferred: CompletableDeferred<Unit>? = null
    ) {
        scope.launch {
            OSIABEvents.postEvent(
                OSIABCustomTabsEvent(
                    browserId = browserId,
                    action = action,
                    context = this@OSIABCustomTabsControllerActivity
                )
            )

            deferred?.complete(Unit)
        }

        deferred?.invokeOnCompletion {
            scope.cancel()
        }
    }
}