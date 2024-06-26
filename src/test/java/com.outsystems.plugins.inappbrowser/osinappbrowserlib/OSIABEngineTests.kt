package com.outsystems.plugins.inappbrowser.osinappbrowserlib

import com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers.OSIABRouterSpy
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABWebViewOptions
import org.junit.Test

import org.junit.Assert.*

class OSIABEngineTests {
    private val url = "https://www.outsystems.com/"
    private lateinit var externalBrowserRouterSpy : OSIABRouterSpy<Unit>
    private lateinit var webViewRouterSpy : OSIABRouterSpy<OSIABWebViewOptions>

    @Test
    fun test_open_externalBrowserWithoutIssues_doesOpenBrowser() {
        makeSUT(true).openExternalBrowser(externalBrowserRouterSpy, url) { result ->
            assertTrue(result)
        }
    }

    @Test
    fun test_open_externalBrowserWithIssues_doesNotOpenBrowser() {
        makeSUT(false).openExternalBrowser(externalBrowserRouterSpy, url) { result ->
            assertFalse(result)
        }
    }

    @Test
    fun test_open_webViewWithoutIssues_doesOpenWebView() {
        makeSUT(true).openWebView(webViewRouterSpy, url) { result ->
            assertTrue(result)
        }
    }

    @Test
    fun test_open_webViewWithIssues_doesNotOpenWebView() {
        makeSUT(false).openWebView(webViewRouterSpy, url) { result ->
            assertFalse(result)
        }
    }

    @Test
    fun test_open_webViewWithOptionsAndCallbackWithoutIssues_doesOpenWebView() {
        makeSUT(true).openWebView(webViewRouterSpy, url) { result ->
            assertTrue(result)
        }
    }

    private fun makeSUT(shouldOpenBrowser: Boolean): OSIABEngine {
        externalBrowserRouterSpy = OSIABRouterSpy(shouldOpenBrowser)
        webViewRouterSpy = OSIABRouterSpy(shouldOpenBrowser)
        return OSIABEngine()
    }
}