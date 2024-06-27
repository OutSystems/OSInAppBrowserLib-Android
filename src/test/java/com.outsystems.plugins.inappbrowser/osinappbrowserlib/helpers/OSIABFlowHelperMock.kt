package com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers

import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABEvents
import kotlinx.coroutines.CoroutineScope

class OSIABFlowHelperMock: OSIABFlowHelperInterface {

    var event: OSIABEvents = OSIABEvents.BrowserPageLoaded
    override fun listenToEvents(scope: CoroutineScope, onEventReceived: (OSIABEvents) -> Unit) {
        onEventReceived(event)
    }
}