@file:OptIn(com.outsystems.plugins.inappbrowser.osinappbrowserlib.RequiresEventBridgeRegistration::class)

package com.outsystems.plugins.inappbrowser.osinappbrowserlib.routeradapters

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsSession
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABEvents
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
    private var isFinished = false

    override fun close(completionHandler: (Boolean) -> Unit) {
        if (isFinished) {
            completionHandler(true)
            return
        }
        var closeEventJob: Job? = null

        closeEventJob = flowHelper.listenToEvents(browserId, lifecycleScope) { event ->
            if(event is OSIABEvents.OSIABCustomTabsEvent
                && event.action == OSIABCustomTabsControllerActivity.EVENT_CUSTOM_TABS_DESTROYED) {
                completionHandler(true)
                closeEventJob?.cancel()
            }
        }

        startCustomTabsControllerActivity(doClose = true)
    }

    private fun resolveStartAnimationRes(animation: OSIABAnimation): Pair<Int, Int> = when (animation) {
        OSIABAnimation.FADE_IN -> android.R.anim.fade_in to android.R.anim.fade_out
        OSIABAnimation.FADE_OUT -> android.R.anim.fade_out to android.R.anim.fade_in
        OSIABAnimation.SLIDE_IN_LEFT -> android.R.anim.slide_in_left to android.R.anim.slide_out_right
        OSIABAnimation.SLIDE_OUT_RIGHT -> android.R.anim.slide_out_right to android.R.anim.slide_in_left
    }

    private fun resolveExitAnimationRes(animation: OSIABAnimation): Pair<Int, Int> = when (animation) {
        OSIABAnimation.FADE_IN -> android.R.anim.fade_out to android.R.anim.fade_in
        OSIABAnimation.FADE_OUT -> android.R.anim.fade_in to android.R.anim.fade_out
        OSIABAnimation.SLIDE_IN_LEFT -> android.R.anim.slide_out_right to android.R.anim.slide_in_left
        OSIABAnimation.SLIDE_OUT_RIGHT -> android.R.anim.slide_in_left to android.R.anim.slide_out_right
    }

    private fun buildCustomTabsIntent(customTabsSession: CustomTabsSession?): CustomTabsIntent {
        val builder = CustomTabsIntent.Builder(customTabsSession)

        builder.setShowTitle(options.showTitle)
        builder.setUrlBarHidingEnabled(options.hideToolbarOnScroll)

        val (startEnter, startExit) = resolveStartAnimationRes(options.startAnimation)
        builder.setStartAnimations(context, startEnter, startExit)

        val (exitEnter, exitExit) = resolveExitAnimationRes(options.exitAnimation)
        builder.setExitAnimations(context, exitEnter, exitExit)

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
            } catch (e: Exception) {
                completionHandler(false)
            }
        }
    }

    private fun openCustomTabsIntent(session: CustomTabsSession, uri: Uri, completionHandler: (Boolean) -> Unit) {
        val customTabsIntent = buildCustomTabsIntent(session)
        customTabsIntent.intent.data = uri

        var eventsJob: Job? = null
        eventsJob = flowHelper.listenToEvents(browserId, lifecycleScope) { event ->
            when (event) {
                is OSIABEvents.OSIABCustomTabsEvent -> {
                    if(event.action == OSIABCustomTabsControllerActivity.EVENT_CUSTOM_TABS_DESTROYED) {
                        isFinished = true
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
                else -> {}
            }
        }

        try {
            val (startEnter, startExit) = resolveStartAnimationRes(options.startAnimation)
            startCustomTabsControllerActivity(
                customTabsIntent = customTabsIntent.intent,
                startEnterAnimRes = startEnter,
                startExitAnimRes = startExit
            )
            completionHandler(true)
        } catch (e: Exception) {
            eventsJob?.cancel()
            completionHandler(false)
        }
    }

    private fun startCustomTabsControllerActivity(
        doClose: Boolean = false,
        customTabsIntent: Intent? = null,
        startEnterAnimRes: Int = 0,
        startExitAnimRes: Int = 0
    ) {
        val intent = Intent(context, OSIABCustomTabsControllerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(OSIABEvents.EXTRA_BROWSER_ID, browserId)
        }

        if(doClose) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            intent.putExtra(OSIABCustomTabsControllerActivity.ACTION_CLOSE_CUSTOM_TABS, true)
        }

        if (customTabsIntent != null) {
            intent.putExtra(OSIABCustomTabsControllerActivity.EXTRA_CUSTOM_TABS_INTENT, customTabsIntent)
            intent.putExtra(OSIABCustomTabsControllerActivity.EXTRA_START_ENTER_ANIM_RES, startEnterAnimRes)
            intent.putExtra(OSIABCustomTabsControllerActivity.EXTRA_START_EXIT_ANIM_RES, startExitAnimRes)
        }

        context.startActivity(intent)
    }
}
