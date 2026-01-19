package com.outsystems.plugins.inappbrowser.osinappbrowserlib

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.views.OSIABWebViewActivity
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
        val context: Context
    ) : OSIABEvents()
    data class OSIABWebViewEvent(
        override val browserId: String,
        val activity: OSIABWebViewActivity? = null
    ) : OSIABEvents()

    companion object {
        const val EXTRA_BROWSER_ID = "com.outsystems.plugins.inappbrowser.osinappbrowserlib.EXTRA_BROWSER_ID"
        const val ACTION_IAB_EVENT = "com.outsystems.plugins.inappbrowser.osinappbrowserlib.ACTION_IAB_EVENT"
        const val EXTRA_EVENT_DATA = "com.outsystems.plugins.inappbrowser.osinappbrowserlib.EXTRA_EVENT_DATA"

        private val _events = MutableSharedFlow<OSIABEvents>(extraBufferCapacity = 64)
        val events = _events.asSharedFlow()

        private var receiver: BroadcastReceiver? = null

        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        fun registerReceiver(context: Context) {
            if (receiver != null) return

            val appContext = context.applicationContext
            receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == ACTION_IAB_EVENT) {
                        @Suppress("DEPRECATION")
                        val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getSerializableExtra(EXTRA_EVENT_DATA, OSIABEvents::class.java)
                        } else {
                            intent.getSerializableExtra(EXTRA_EVENT_DATA) as? OSIABEvents
                        }
                        event?.let {
                            _events.tryEmit(it)
                        }
                    }
                }
            }

            val filter = IntentFilter(ACTION_IAB_EVENT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }
        }

        suspend fun postEvent(event: OSIABEvents) {
            _events.emit(event)
        }

        fun broadcastEvent(context: Context, event: OSIABEvents) {
            val intent = Intent(ACTION_IAB_EVENT).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_EVENT_DATA, event)
            }
            context.sendBroadcast(intent)
        }
    }

}
