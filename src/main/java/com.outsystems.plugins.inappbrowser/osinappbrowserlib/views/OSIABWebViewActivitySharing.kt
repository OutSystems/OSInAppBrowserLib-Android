package com.outsystems.plugins.inappbrowser.osinappbrowserlib.views

/**
 * A non-isolated version of OSIABWebViewActivity that runs in the main app process.
 * This is used when the user opts-out of storage isolation to share cookies/localStorage.
 */
class OSIABWebViewActivitySharing : OSIABWebViewActivity()
