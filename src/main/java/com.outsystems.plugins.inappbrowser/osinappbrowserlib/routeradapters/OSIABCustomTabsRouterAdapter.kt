package com.outsystems.plugins.inappbrowser.osinappbrowserlib.routeradapters

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsSession
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABEvents
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.canOpenURL
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers.OSIABCustomTabsSessionHelper
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers.OSIABCustomTabsSessionHelperInterface
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers.OSIABFlowHelperInterface
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABAnimation
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABCustomTabsOptions
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABViewStyle
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.views.OSIABCustomTabsControllerActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

class OSIABCustomTabsRouterAdapter(
    context: Context,
    lifecycleScope: CoroutineScope,
    options: OSIABCustomTabsOptions,
    flowHelper: OSIABFlowHelperInterface,
    onBrowserPageLoaded: () -> Unit,
    onBrowserFinished: () -> Unit,
    private val customTabsSessionHelper: OSIABCustomTabsSessionHelperInterface = OSIABCustomTabsSessionHelper(),
) : OSIABBaseRouterAdapter<OSIABCustomTabsOptions, Boolean>(
    context = context,
    lifecycleScope = lifecycleScope,
    options = options,
    flowHelper = flowHelper,
    onBrowserPageLoaded = onBrowserPageLoaded,
    onBrowserFinished = onBrowserFinished
) {

    private val browserId = UUID.randomUUID().toString()

    // for the browserPageLoaded event, which we only want to trigger on the first URL loaded in the CustomTabs instance
    private var isFirstLoad = true

    override fun close(completionHandler: (Boolean) -> Unit) {
        var closeEventJob: Job? = null

        closeEventJob = flowHelper.listenToEvents(browserId, lifecycleScope) { event ->
            if(event is OSIABEvents.OSIABCustomTabsEvent) {
                when(event.action) {
                    OSIABCustomTabsControllerActivity.ACTION_CUSTOM_TABS_READY -> {
                        completionHandler(false)
                    }
                    OSIABCustomTabsControllerActivity.ACTION_CUSTOM_TABS_DESTROYED -> {
                        completionHandler(true)
                    }
                    else -> {
                        return@listenToEvents
                    }
                }
                closeEventJob?.cancel()
            }
        }

        startCustomTabsControllerActivity(true)
    }

    private fun buildCustomTabsIntent(customTabsSession: CustomTabsSession?): CustomTabsIntent {
        val builder = CustomTabsIntent.Builder(customTabsSession)

        builder.setShowTitle(options.showTitle)
        builder.setUrlBarHidingEnabled(options.hideToolbarOnScroll)

        when (options.startAnimation) {
            OSIABAnimation.FADE_IN -> builder.setStartAnimations(
                context,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )

            OSIABAnimation.FADE_OUT -> builder.setStartAnimations(
                context,
                android.R.anim.fade_out,
                android.R.anim.fade_in
            )

            OSIABAnimation.SLIDE_IN_LEFT -> builder.setStartAnimations(
                context,
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )

            OSIABAnimation.SLIDE_OUT_RIGHT -> builder.setStartAnimations(
                context,
                android.R.anim.slide_out_right,
                android.R.anim.slide_in_left
            )
        }

        when (options.exitAnimation) {
            OSIABAnimation.FADE_IN -> builder.setExitAnimations(
                context,
                android.R.anim.fade_out,
                android.R.anim.fade_in
            )

            OSIABAnimation.FADE_OUT -> builder.setExitAnimations(
                context,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )

            OSIABAnimation.SLIDE_IN_LEFT -> builder.setExitAnimations(
                context,
                android.R.anim.slide_out_right,
                android.R.anim.slide_in_left
            )

            OSIABAnimation.SLIDE_OUT_RIGHT -> builder.setExitAnimations(
                context,
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )
        }

        if (options.viewStyle == OSIABViewStyle.BOTTOM_SHEET) {
            options.bottomSheetOptions?.let { bottomSheetOptions ->
                val height = bottomSheetOptions.height.coerceAtLeast(1)
                if (bottomSheetOptions.isFixed) {
                    builder.setInitialActivityHeightPx(
                        height,
                        CustomTabsIntent.ACTIVITY_HEIGHT_FIXED
                    )
                } else {
                    builder.setInitialActivityHeightPx(height)
                }
            }
        }

        builder.setBackgroundInteractionEnabled(true)

        return builder.build()
    }

    override fun handleOpen(url: String, completionHandler: (Boolean) -> Unit) {
        lifecycleScope.launch {
            try {
                val uri = Uri.parse(url)
                if (!context.canOpenURL(uri)) {
                    completionHandler(false)
                    return@launch
                }

                customTabsSessionHelper.generateNewCustomTabsSession(
                    browserId,
                    context,
                    lifecycleScope,
                    flowHelper,
                    customTabsSessionCallback = {
                        if(it == null) {
                            completionHandler(false)
                            return@generateNewCustomTabsSession
                        }

                        openCustomTabsIntent(it, uri, completionHandler)
                    }
                )

                startCustomTabsControllerActivity()
            } catch (e: Exception) {
                completionHandler(false)
            }
        }
    }

    private fun openCustomTabsIntent(session: CustomTabsSession, uri: Uri, completionHandler: (Boolean) -> Unit) {
        val customTabsIntent = buildCustomTabsIntent(session)
        var eventsJob: Job? = null
        eventsJob = flowHelper.listenToEvents(browserId, lifecycleScope) { event ->
            when (event) {
                is OSIABEvents.OSIABCustomTabsEvent -> {
                    if(isFirstLoad && event.action == OSIABCustomTabsControllerActivity.ACTION_CUSTOM_TABS_READY) {
                        try {
                            customTabsIntent.launchUrl(event.context, uri)
                            completionHandler(true)
                        } catch (e: Exception) {
                            completionHandler(false)
                        }
                    }
                    else if(event.action == OSIABCustomTabsControllerActivity.ACTION_CUSTOM_TABS_DESTROYED) {
                        onBrowserFinished()
                        eventsJob?.cancel()
                    }
                }
                is OSIABEvents.BrowserPageLoaded -> {
                    if (isFirstLoad) {
                        onBrowserPageLoaded()
                        isFirstLoad = false
                    }
                }
                is OSIABEvents.BrowserFinished -> {
                    onBrowserFinished()
                    eventsJob?.cancel()
                }
                else -> {}
            }
        }
    }

    private fun startCustomTabsControllerActivity(doClose: Boolean = false) {
        val intent = Intent(context, OSIABCustomTabsControllerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(OSIABEvents.EXTRA_BROWSER_ID, browserId)
        }

        if(doClose) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            intent.putExtra(OSIABCustomTabsControllerActivity.ACTION_CLOSE_CUSTOM_TABS, true)
        }

        context.startActivity(intent)
    }
}
