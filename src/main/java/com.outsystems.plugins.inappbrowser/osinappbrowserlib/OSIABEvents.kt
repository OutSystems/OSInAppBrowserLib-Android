package com.outsystems.plugins.inappbrowser.osinappbrowserlib

import kotlinx.coroutines.flow.MutableSharedFlow

sealed class OSIABEvents {
    object BrowserPageLoaded : OSIABEvents()
    object BrowserFinished : OSIABEvents()

    companion object {
        var browserEvents = MutableSharedFlow<OSIABEvents>()
            private set
    }

}