package com.outsystems.plugins.inappbrowser.osinappbrowserlib

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.Serializable

@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This API requires a prior call to OSIABEvents.registerReceiver(context) to work correctly with process isolation on Android 9+."
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class RequiresEventBridgeRegistration

sealed class OSIABEvents : Serializable {
    abstract val browserId: String

    data class BrowserPageLoaded(override val browserId: String) : OSIABEvents()
    data class BrowserFinished(override val browserId: String) : OSIABEvents()
    data class BrowserPageNavigationCompleted(override val browserId: String, val url: String?) : OSIABEvents()
    data class OSIABCustomTabsEvent(
        override val browserId: String,
        val action: String,
        @Transient val context: Context? = null
    ) : OSIABEvents()
    
    data class OSIABWebViewEvent(
        override val browserId: String
    ) : OSIABEvents()

    companion object {
        // RMET-5394 debug logging tag: measures the gap between broadcastEvent() (sent
        // from the isolated, never-frozen process) and onReceive() (received in the
        // main process) - the most direct evidence of the main process being frozen.
        private const val LOG_TAG = "OSIABEvents"

        const val EXTRA_BROWSER_ID = "com.outsystems.plugins.inappbrowser.osinappbrowserlib.EXTRA_BROWSER_ID"
        const val ACTION_IAB_EVENT = "com.outsystems.plugins.inappbrowser.osinappbrowserlib.ACTION_IAB_EVENT"
        const val ACTION_CLOSE_WEBVIEW = "com.outsystems.plugins.inappbrowser.osinappbrowserlib.ACTION_CLOSE_WEBVIEW"
        const val EXTRA_EVENT_DATA = "com.outsystems.plugins.inappbrowser.osinappbrowserlib.EXTRA_EVENT_DATA"

        // Buffer capacity is required because BroadcastReceiver.onReceive() is synchronous.
        // We must use tryEmit() which would drop events without buffer space.
        private val _events = MutableSharedFlow<OSIABEvents>(extraBufferCapacity = 64)
        val events = _events.asSharedFlow()

        private var receiver: BroadcastReceiver? = null
        private var receiverRefCount = 0

        /**
         * Registers a BroadcastReceiver to listen for events from the isolated WebView process.
         * This must be called before opening a WebView on Android 9+ to ensure events are received.
         */
        @Synchronized
        fun registerReceiver(context: Context) {
            receiverRefCount++
            if (receiver != null) return

            val appContext = context.applicationContext
            receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == ACTION_IAB_EVENT) {
                        val event = IntentCompat.getSerializableExtra(
                            intent,
                            EXTRA_EVENT_DATA,
                            OSIABEvents::class.java
                        )
                        if (event != null) {
                            Log.d(LOG_TAG, "received ${event::class.simpleName} browserId=${event.browserId}")
                            _events.tryEmit(event)
                        } else {
                            Log.d(LOG_TAG, "received ACTION_IAB_EVENT with null/undecodable payload")
                        }
                    }
                }
            }

            val filter = IntentFilter(ACTION_IAB_EVENT)
            ContextCompat.registerReceiver(
                appContext,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            Log.d(LOG_TAG, "registerReceiver: registered (refCount=$receiverRefCount)")
        }

        /**
         * Unregisters the BroadcastReceiver. Should be called when the browser is closed.
         * The receiver is only truly unregistered when all registered 'users' have unregistered.
         */
        @Synchronized
        fun unregisterReceiver(context: Context) {
            if (receiverRefCount > 0) {
                receiverRefCount--
            }
            
            if (receiverRefCount == 0) {
                receiver?.let {
                    try {
                        context.applicationContext.unregisterReceiver(it)
                        Log.d(LOG_TAG, "unregisterReceiver: unregistered")
                    } catch (e: Exception) {
                        // Receiver may not be registered, ignore
                    }
                    receiver = null
                }
            }
        }

        suspend fun postEvent(event: OSIABEvents) {
            _events.emit(event)
        }

        /**
         * Broadcasts an event from the isolated WebView process to the main process.
         * Only data-only events should be broadcast (BrowserPageLoaded, BrowserFinished, etc.).
         */
        fun broadcastEvent(context: Context, event: OSIABEvents) {
            Log.d(LOG_TAG, "broadcastEvent: sending ${event::class.simpleName} browserId=${event.browserId}")
            val intent = Intent(ACTION_IAB_EVENT).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_EVENT_DATA, event)
            }
            context.sendBroadcast(intent)
        }
    }

}
