package com.outsystems.plugins.inappbrowser.osinappbrowserlib.views

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABEvents
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABEvents.OSIABCustomTabsEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch


class OSIABCustomTabsControllerActivity: AppCompatActivity() {
    companion object {
        private const val TAG = "OSIABCT"
        const val EVENT_CUSTOM_TABS_DESTROYED = "com.outsystems.plugins.inappbrowser.osinappbrowserlib.EVENT_CUSTOM_TABS_DESTROYED"
        const val ACTION_CLOSE_CUSTOM_TABS = "com.outsystems.plugins.inappbrowser.osinappbrowserlib.ACTION_CLOSE_CUSTOM_TABS"
        const val EXTRA_CUSTOM_TABS_INTENT = "com.outsystems.plugins.inappbrowser.osinappbrowserlib.EXTRA_CUSTOM_TABS_INTENT"
        const val EXTRA_START_ENTER_ANIM_RES = "com.outsystems.plugins.inappbrowser.osinappbrowserlib.EXTRA_START_ENTER_ANIM_RES"
        const val EXTRA_START_EXIT_ANIM_RES = "com.outsystems.plugins.inappbrowser.osinappbrowserlib.EXTRA_START_EXIT_ANIM_RES"
    }

    private var customTabsLauncher: ActivityResultLauncher<Intent>? = null
    private var hasLaunchedCustomTabs = false


    private fun setup(intent: Intent) {
        Log.d(TAG, "setup: hasLaunchedCustomTabs=$hasLaunchedCustomTabs, doClose=${intent.getBooleanExtra(ACTION_CLOSE_CUSTOM_TABS, false)}")
        window.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

        if (intent.getBooleanExtra(ACTION_CLOSE_CUSTOM_TABS, false)) {
            Log.d(TAG, "setup: close action -> finish()")
            finish()
            return
        }

        if (!hasLaunchedCustomTabs) {
            val customTabsIntent = IntentCompat.getParcelableExtra(intent, EXTRA_CUSTOM_TABS_INTENT, Intent::class.java)
            if (customTabsIntent != null) {
                hasLaunchedCustomTabs = true
                val enterAnimRes = intent.getIntExtra(EXTRA_START_ENTER_ANIM_RES, 0)
                val exitAnimRes = intent.getIntExtra(EXTRA_START_EXIT_ANIM_RES, 0)
                val options = if (enterAnimRes != 0 && exitAnimRes != 0) {
                    ActivityOptionsCompat.makeCustomAnimation(this, enterAnimRes, exitAnimRes)
                } else {
                    null
                }
                Log.d(TAG, "setup: launching CCT (enter=$enterAnimRes, exit=$exitAnimRes)")
                customTabsLauncher?.launch(customTabsIntent, options)
            } else {
                Log.d(TAG, "setup: no CCT intent extra found")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        customTabsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d(TAG, "launcher result received -> finish()")
            finish()
        }
        setup(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent")
        setup(intent)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy -> emitting EVENT_CUSTOM_TABS_DESTROYED")
        intent.getStringExtra(OSIABEvents.EXTRA_BROWSER_ID)?.let { browserId ->
            val customScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val deferred = CompletableDeferred<Unit>()
            sendCustomTabsEvent(customScope, browserId, EVENT_CUSTOM_TABS_DESTROYED, deferred)
        }
        super.onDestroy()
    }

    private fun sendCustomTabsEvent(
        scope: CoroutineScope,
        browserId: String,
        action: String,
        deferred: CompletableDeferred<Unit>? = null
    ) {
        scope.launch {
            OSIABEvents.postEvent(
                OSIABCustomTabsEvent(
                    browserId = browserId,
                    action = action,
                    context = this@OSIABCustomTabsControllerActivity
                )
            )

            deferred?.complete(Unit)
        }

        deferred?.invokeOnCompletion {
            scope.cancel()
        }
    }
}