package com.outsystems.plugins.inappbrowser.osinappbrowserlib

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.routeradapters.OSIABExternalBrowserRouterAdapter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@RunWith(AndroidJUnit4::class)
class OSIABExternalBrowserRouterAdapterTests {
    private val activityName = "TestActivity"
    private val packageName = "com.outsystems.plugins.inappbrowser.osinappbrowserlib"
    private val url = "https://www.outsystems.com/"

    @Test
    fun test_handleOpen_withInvalidURL_returnsFalse() {
        val context = mockContext(useValidURL = false, ableToOpenURL = false)
        val sut = OSIABExternalBrowserRouterAdapter(context)
        sut.handleOpen(url) { assertFalse(it) }
    }

    @Test
    fun test_handleOpen_withValidButNotAbleToOpenIt_returnsFalse() {
        val context = mockContext(useValidURL = true, ableToOpenURL = false)
        val sut = OSIABExternalBrowserRouterAdapter(context)
        sut.handleOpen(url) { assertFalse(it) }
    }

    @Test
    fun test_handleOpen_withValidAndAbleToOpenIt_returnsTrue() {
        val context = mockContext(useValidURL = true, ableToOpenURL = true)
        val sut = OSIABExternalBrowserRouterAdapter(context)
        sut.handleOpen(url) { assertTrue(it) }
    }

    private fun mockContext(useValidURL: Boolean, ableToOpenURL: Boolean = false): Context {
        val context = mock(Context::class.java)
        val packageManager = mock(android.content.pm.PackageManager::class.java)

        val resolveInfo = if (useValidURL) {
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

        if (!ableToOpenURL) {
            doThrow(RuntimeException("Unable to open URL")).`when`(context).startActivity(any(Intent::class.java))
        }

        return context
    }

}
