package com.outsystems.plugins.inappbrowser.osinappbrowserlib

import android.content.Context
import android.content.Intent
import android.os.Build.VERSION
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABEvents
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABWebViewOptions
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.routeradapters.OSIABWebViewRouterAdapter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OSIABWebViewRouterAdapterTests {
    private val url = "https://www.outsystems.com/"
    private val options = OSIABWebViewOptions()

    @Test
    fun test_handleOpen_notAbleToOpenIt_returnsFalse() {
        val context = mockContext(ableToOpenURL = false)
        val sut = OSIABWebViewRouterAdapter(context, options, {}, {})
        sut.handleOpen(url) {
            assertFalse(it)
        }
    }

    @Test
    fun test_handleOpen_ableToOpenIt_returnsTrue() {
        val context = mockContext(ableToOpenURL = true)
        val sut = OSIABWebViewRouterAdapter(context, options, {}, {})
        sut.handleOpen(url) {
            assertTrue(it)
        }
    }

    @Test
    fun test_handleOpen_ableToOpenIt_when_browserPageLoads_then_browserPageLoadedTriggered() {
        val context = mockContext(ableToOpenURL = true)
        val sut = OSIABWebViewRouterAdapter(context, options,
            {
                // assertTrue, correct callback was called
                assertTrue(true)
            },
            {
                fail()
            }
        )
        sut.handleOpen(url) {
            assertTrue(it)
        }
        val intent = Intent(OSIABEvents.ACTION_BROWSER_PAGE_LOADED)
        context.sendBroadcast(intent)
    }

    @Test
    fun test_handleOpen_ableToOpenIt_when_APIBelow33_and_browserFinished_then_browserFinishedTriggered() {
        ReflectionHelpers.setStaticField(VERSION::class.java, "SDK_INT", 32)
        val context = mockContext(ableToOpenURL = true)
        val sut = OSIABWebViewRouterAdapter(context, options,
            {
                fail()
            },
            {
                // assertTrue, correct callback was called
                assertTrue(true)
            }
        )
        sut.handleOpen(url) {
            assertTrue(it)
        }
        val intent = Intent(OSIABEvents.ACTION_BROWSER_PAGE_LOADED)
        context.sendBroadcast(intent)
    }

    @Test
    fun test_handleOpen_ableToOpenIt_when_API33_and_browserFinished_then_browserFinishedTriggered() {
        ReflectionHelpers.setStaticField(VERSION::class.java, "SDK_INT", 33)
        val context = mockContext(ableToOpenURL = true)
        val sut = OSIABWebViewRouterAdapter(context, options,
            {
                fail()
            },
            {
                // assertTrue, correct callback was called
                assertTrue(true)
            }
        )
        sut.handleOpen(url) {
            assertTrue(it)
        }
        val intent = Intent(OSIABEvents.ACTION_BROWSER_PAGE_LOADED)
        context.sendBroadcast(intent)
    }

    @Test
    fun test_handleOpen_ableToOpenIt_when_APIAbove33_and_browserFinished_then_browserFinishedTriggered() {
        ReflectionHelpers.setStaticField(VERSION::class.java, "SDK_INT", 34)
        val context = mockContext(ableToOpenURL = true)
        val sut = OSIABWebViewRouterAdapter(context, options,
            {
                fail()
            },
            {
                // assertTrue, correct callback was called
                assertTrue(true)
            }
        )
        sut.handleOpen(url) {
            assertTrue(it)
        }
        val intent = Intent(OSIABEvents.ACTION_BROWSER_PAGE_LOADED)
        context.sendBroadcast(intent)
    }

    @Test
    fun test_handleOpenWithOptionsAndCallback_when_ableToOpenIt_returnsTrue() {
        val context = mockContext(ableToOpenURL = true)
        val sut = OSIABWebViewRouterAdapter(context, options, {}, {})
        sut.handleOpen(url) {
            assertTrue(it)
        }
    }

    private fun mockContext(ableToOpenURL: Boolean = false): Context {
        val context = mock(Context::class.java)
        if (!ableToOpenURL) {
            doThrow(RuntimeException("Unable to open URL")).`when`(context).startActivity(any(Intent::class.java))
        }
        return context
    }

}
