package com.outsystems.plugins.inappbrowser.osinappbrowserlib

import android.content.Context
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.views.OSIABWebViewActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class OSIABEvents {
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
        val activity: OSIABWebViewActivity
    ) : OSIABEvents()

    companion object {
        const val EXTRA_BROWSER_ID = "com.outsystems.plugins.inappbrowser.osinappbrowserlib.EXTRA_BROWSER_ID"

        private val _events = MutableSharedFlow<OSIABEvents>()
        val events = _events.asSharedFlow()

        suspend fun postEvent(event: OSIABEvents) {
            _events.emit(event)
        }
    }

}
