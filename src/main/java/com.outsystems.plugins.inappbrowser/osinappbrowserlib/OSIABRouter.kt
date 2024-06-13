package com.outsystems.plugins.inappbrowser.osinappbrowserlib

import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABCustomTabsOptions

interface OSIABRouter<OptionsType, ReturnType> {
    /**
     * Handles opening the passed `url`.
     * @param url URL to be opened.
     * @param options Customization options to apply to the browser.
     * @param completionHandler The callback with the result of opening the url.
     */
    fun handleOpen(url: String, options: OptionsType? = null, completionHandler: (ReturnType) -> Unit)
}