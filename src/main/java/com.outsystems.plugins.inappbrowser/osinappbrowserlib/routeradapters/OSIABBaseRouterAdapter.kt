package com.outsystems.plugins.inappbrowser.osinappbrowserlib.routeradapters

import android.content.Context
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABClosable
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABRouter
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers.OSIABFlowHelperInterface
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABOptions
import kotlinx.coroutines.CoroutineScope

abstract class OSIABBaseRouterAdapter<OptionsType : OSIABOptions, ReturnType>(
    protected val context: Context,
    protected val lifecycleScope: CoroutineScope,
    protected val options: OptionsType,
    protected val flowHelper: OSIABFlowHelperInterface,
    protected val onBrowserPageLoaded: () -> Unit,
    protected val onBrowserFinished: () -> Unit,
    protected val onBrowserPageNavigationCompleted: (String?) -> Unit
) : OSIABRouter<ReturnType>, OSIABClosable {
    abstract override fun close(completionHandler: (Boolean) -> Unit)
    abstract override fun handleOpen(url: String, completionHandler: (ReturnType) -> Unit)
}
