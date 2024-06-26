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
    @SerializedName("customUserAgent") val customUserAgent: String?
) : Serializable {
    constructor() : this(
        true,
        true,
        true,
        true,
        false,
        "Close",
        OSIABToolbarPosition.TOP,
        false,
        true,
        true,
        true,
        true,
        null
    )
}
