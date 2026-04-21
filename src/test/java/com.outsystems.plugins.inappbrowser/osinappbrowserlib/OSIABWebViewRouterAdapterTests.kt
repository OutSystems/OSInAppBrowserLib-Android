package com.outsystems.plugins.inappbrowser.osinappbrowserlib

import android.content.Context
import android.content.Intent
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers.OSIABFlowHelperMock
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABWebViewOptions
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.routeradapters.OSIABWebViewRouterAdapter
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.views.OSIABWebViewActivity
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.views.OSIABWebViewActivitySharing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class OSIABWebViewRouterAdapterTests {

    private val url = "https://www.outsystems.com/"
    private val options = OSIABWebViewOptions()

    @Test
    fun test_handleOpen_notAbleToOpenIt_returnsFalse() {
        runTest(StandardTestDispatcher()) {
            val context = mockContext(ableToOpenURL = false)
            val sut = OSIABWebViewRouterAdapter(
                context = context,
                lifecycleScope = this,
                options = options,
                flowHelper = OSIABFlowHelperMock(),
                onBrowserPageLoaded = {}, // do nothing
                onBrowserFinished = {}, // do nothing
                onBrowserPageNavigationCompleted = {} // do nothing
            )

            sut.handleOpen(url) {
                assertFalse(it)
            }
        }
    }

    @Test
    fun test_handleOpen_ableToOpenIt_returnsTrue_and_when_browserPageLoads_then_browserPageLoadedTriggered() =
        runTest(StandardTestDispatcher()) {
            val context = mockContext(ableToOpenURL = true)
            val sut = OSIABWebViewRouterAdapter(
                context = context,
                lifecycleScope = this,
                options = options,
                flowHelper = OSIABFlowHelperMock(),
                onBrowserPageLoaded = {
                    assertTrue(true) // onBrowserPageLoaded was called
                },
                onBrowserFinished = {
                    fail()
                },
                onBrowserPageNavigationCompleted = {}
            )
            sut.handleOpen(url) {
                assertTrue(it)
            }
        }

    @Test
    fun test_handleOpen_ableToOpenIt_returnsTrue_and_when_browserFinished_then_browserFinishedTriggered() =
        runTest(StandardTestDispatcher()) {
            val context = mockContext(ableToOpenURL = true)
            val flowHelperMock = OSIABFlowHelperMock().apply { events = listOf(OSIABEvents.BrowserFinished("")) }
            val sut = OSIABWebViewRouterAdapter(
                context = context,
                lifecycleScope = this,
                options = options,
                flowHelper = flowHelperMock,
                onBrowserPageLoaded = {
                    fail()
                },
                onBrowserFinished = {
                    assertTrue(true) // onBrowserFinished was called
                },
                onBrowserPageNavigationCompleted = {}
            )
            sut.handleOpen(url) {
                assertTrue(it)
            }
        }

    @Test
    fun test_handleOpen_withValidURL_launchesWebView_when_browserPageNavigationCompleted_then_browserPageNavigationCompletedTriggered() {
        runTest(StandardTestDispatcher()) {
            var pageNavigationCalled = false
            val context = mockContext(ableToOpenURL = true)
            val flowHelperMock = OSIABFlowHelperMock().apply {
                events = listOf(
                    OSIABEvents.BrowserPageNavigationCompleted("", "https://test"),
                    OSIABEvents.OSIABWebViewEvent("")
                )
            }
            val sut = OSIABWebViewRouterAdapter(
                context = context,
                lifecycleScope = this,
                flowHelper = flowHelperMock,
                options = options,
                onBrowserPageLoaded = {
                    fail()
                },
                onBrowserFinished = {
                    fail()
                },
                onBrowserPageNavigationCompleted = { url ->
                    pageNavigationCalled = true
                    assertEquals(url, "https://test")
                }
            )

            sut.handleOpen(url) { success ->
                assertTrue(success)
                assertTrue(pageNavigationCalled)
            }
        }
    }

    @Test
    @Config(sdk = [28])
    fun test_handleOpen_withIsolationEnabledOnSupportedAndroid_launchesIsolatedActivity() {
        runTest(StandardTestDispatcher()) {
            val context = mockContext(ableToOpenURL = true)
            val sut = makeSUT(context, options, this)

            sut.handleOpen(url) {}
            advanceUntilIdle()

            assertStartedActivity(context, OSIABWebViewActivity::class.java.name)
        }
    }

    @Test
    @Config(sdk = [27])
    fun test_handleOpen_withIsolationEnabledBelowAndroid9_launchesSharingActivity() {
        runTest(StandardTestDispatcher()) {
            val context = mockContext(ableToOpenURL = true)
            val sut = makeSUT(context, options, this)

            sut.handleOpen(url) {}
            advanceUntilIdle()

            assertStartedActivity(context, OSIABWebViewActivitySharing::class.java.name)
        }
    }

    @Test
    @Config(sdk = [28])
    fun test_handleOpen_withIsolationDisabledOnSupportedAndroid_launchesSharingActivity() {
        runTest(StandardTestDispatcher()) {
            val context = mockContext(ableToOpenURL = true)
            val sut = makeSUT(context, options.copy(isIsolated = false), this)

            sut.handleOpen(url) {}
            advanceUntilIdle()

            assertStartedActivity(context, OSIABWebViewActivitySharing::class.java.name)
        }
    }

    private fun makeSUT(
        context: Context,
        options: OSIABWebViewOptions,
        lifecycleScope: CoroutineScope
    ) = OSIABWebViewRouterAdapter(
        context = context,
        lifecycleScope = lifecycleScope,
        options = options,
        flowHelper = OSIABFlowHelperMock(),
        onBrowserPageLoaded = {},
        onBrowserFinished = {},
        onBrowserPageNavigationCompleted = {}
    )

    private fun assertStartedActivity(context: Context, expectedClassName: String) {
        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(context).startActivity(intentCaptor.capture())
        assertEquals(expectedClassName, intentCaptor.value.component?.className)
    }

    private fun mockContext(ableToOpenURL: Boolean = false): Context {
        val context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(context)
        if (!ableToOpenURL) {
            doThrow(RuntimeException("Unable to open URL")).`when`(context).startActivity(any(Intent::class.java))
        }
        return context
    }

}
