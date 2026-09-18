package com.outsystems.plugins.inappbrowser.osinappbrowserlib.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class OSIABWebViewOptions(
    @SerializedName("showURL") val showURL: Boolean = true,
    @SerializedName("showToolbar") val showToolbar: Boolean = true,
    @SerializedName("clearCache") val clearCache: Boolean = true,
    @SerializedName("clearSessionCache") val clearSessionCache: Boolean = true,
    @SerializedName("mediaPlaybackRequiresUserAction") val mediaPlaybackRequiresUserAction: Boolean = false,
    @SerializedName("closeButtonText") val closeButtonText: String = "Close",
    @SerializedName("toolbarPosition") val toolbarPosition: OSIABToolbarPosition = OSIABToolbarPosition.TOP,
    @SerializedName("leftToRight") val leftToRight: Boolean = false,
    @SerializedName("showNavigationButtons") val showNavigationButtons: Boolean = true,
    @SerializedName("allowZoom") val allowZoom: Boolean = true,
    @SerializedName("hardwareBack") val hardwareBack: Boolean = true,
    @SerializedName("pauseMedia") val pauseMedia: Boolean = true,
    @SerializedName("customUserAgent") val customUserAgent: String? = null,
    @SerializedName("isIsolated") val isIsolated: Boolean = true,
    // RMET-5394: regex patterns checked against each finished page load; a match closes the
    // browser natively (no dependency on MainActivity's WebView JS being able to react).
    @SerializedName("successUrlPatterns") val successUrlPatterns: List<String>? = null
) : OSIABOptions, Serializable
