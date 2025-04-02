package com.outsystems.plugins.inappbrowser.osinappbrowserlib

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers.OSIABCustomTabsSessionHelperMock
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers.OSIABFlowHelperMock
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABCustomTabsOptions
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.routeradapters.OSIABCustomTabsRouterAdapter
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OSIABCustomTabsRouterAdapterTests {
    private val activityName = "OSIABTestActivity"
    private val packageName = "com.outsystems.plugins.inappbrowser.osinappbrowserlib"

    private val uri = Uri.parse("https://www.outsystems.com/")
    private val options = OSIABCustomTabsOptions()

    @Test
    fun test_handleOpen_withValidURL_launchesCustomTab() {
        runTest(StandardTestDispatcher()) {
            val context = mockContext(useValidURL = true, ableToOpenURL = true)
            val sut = OSIABCustomTabsRouterAdapter(
                context = context,
                lifecycleScope = this,
                flowHelper = OSIABFlowHelperMock(),
                options = options,
                onBrowserPageLoaded = {},
                onBrowserFinished = {},
                customTabsSessionHelper = OSIABCustomTabsSessionHelperMock(),
            )

            sut.handleOpen(uri.toString()) { success ->
                assertTrue(success)
            }
        }
    }


    @Test
    fun test_handleOpen_withInvalidURL_returnsFalse() {
        runTest(StandardTestDispatcher()) {
            val context = mockContext(useValidURL = false, ableToOpenURL = false)
            val sut = OSIABCustomTabsRouterAdapter(
                context = context,
                lifecycleScope = this,
                flowHelper = OSIABFlowHelperMock(),
                options = options,
                onBrowserPageLoaded = {},
                onBrowserFinished = {},
                customTabsSessionHelper = OSIABCustomTabsSessionHelperMock(),
            )

            sut.handleOpen("invalid_url") { success ->
                assertFalse(success)
            }
        }
    }


    @Test
    fun test_handleOpen_withValidURLButException_returnsFalse() {
        runTest(StandardTestDispatcher()) {
            val context = mock(Context::class.java)
            val packageManager = mock(PackageManager::class.java)

            val sut = OSIABCustomTabsRouterAdapter(
                context = context,
                lifecycleScope = this,
                flowHelper = OSIABFlowHelperMock(),
                options = options,
                onBrowserPageLoaded = {},
                onBrowserFinished = {},
                customTabsSessionHelper = OSIABCustomTabsSessionHelperMock(),
            )

            `when`(packageManager.resolveActivity(any(Intent::class.java), anyInt())).thenReturn(
                ResolveInfo()
            )

            doThrow(RuntimeException("Exception")).`when`(context)
                .startActivity(any(Intent::class.java))

            sut.handleOpen(uri.toString()) { success ->
                assertFalse(success)
            }
        }
    }

    @Test
    fun test_handleOpen_withValidURL_launchesCustomTab_when_browserPageLoaded_then_browserPageLoadedTriggered() {
        runTest(StandardTestDispatcher()) {
            val context = mockContext(useValidURL = true, ableToOpenURL = true)
            val flowHelperMock = OSIABFlowHelperMock().apply { event = OSIABEvents.BrowserPageLoaded("") }
            val sut = OSIABCustomTabsRouterAdapter(
                context = context,
                lifecycleScope = this,
                flowHelper = flowHelperMock,
                options = options,
                onBrowserPageLoaded = {
                    assertTrue(true) // onBrowserPageLoaded was called
                },
                onBrowserFinished = {
                    fail()
                },
                customTabsSessionHelper = OSIABCustomTabsSessionHelperMock(),
            )

            sut.handleOpen(uri.toString()) { success ->
                assertTrue(success)
            }
        }
    }

    @Test
    fun test_handleOpen_withValidURL_launchesCustomTab_when_browserFinished_then_browserFinishedTriggered() {
        runTest(StandardTestDispatcher()) {
            val context = mockContext(useValidURL = true, ableToOpenURL = true)
            val flowHelperMock = OSIABFlowHelperMock().apply { event = OSIABEvents.BrowserFinished("") }
            val sut = OSIABCustomTabsRouterAdapter(
                context = context,
                lifecycleScope = this,
                flowHelper = flowHelperMock,
                options = options,
                onBrowserPageLoaded = {
                    fail()
                },
                onBrowserFinished = {
                    assertTrue(true) // onBrowserFinished was called
                },
                customTabsSessionHelper = OSIABCustomTabsSessionHelperMock(),
            )

            sut.handleOpen(uri.toString()) { success ->
                assertTrue(success)
            }
        }
    }

    private fun mockContext(useValidURL: Boolean, ableToOpenURL: Boolean = false): Context {
        val context = mock(Context::class.java)
        val packageManager = mock(PackageManager::class.java)

        val resolveInfo = if (useValidURL && ableToOpenURL) {
            val resolveInfo = ResolveInfo()
            val activityInfo = ActivityInfo()
            val applicationInfo = ApplicationInfo()

            applicationInfo.packageName = packageName

            activityInfo.applicationInfo = applicationInfo
            activityInfo.packageName = packageName
            activityInfo.name = activityName

            resolveInfo.activityInfo = activityInfo
            resolveInfo
        } else null

        `when`(context.packageManager).thenReturn(packageManager)
        `when`(packageManager.resolveActivity(any(Intent::class.java), anyInt())).thenReturn(resolveInfo)

        return context
    }
}
