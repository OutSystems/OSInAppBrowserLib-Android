package com.outsystems.plugins.inappbrowser.osinappbrowserlib.views

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.R
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABWebViewOptions

class OSIABWebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var closeButton: Button
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var urlText: TextView
    private lateinit var toolbar: Toolbar
    private lateinit var options: OSIABWebViewOptions
    private lateinit var appName: String

    companion object {
        const val WEB_VIEW_URL_EXTRA = "WEB_VIEW_URL_EXTRA"
        const val WEB_VIEW_OPTIONS_EXTRA = "WEB_VIEW_OPTIONS_EXTRA"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appName = applicationInfo.loadLabel(packageManager).toString()

        // get parameters from intent extras
        val urlToOpen = intent.extras?.getString(WEB_VIEW_URL_EXTRA)
        options = intent.extras?.getSerializable(WEB_VIEW_OPTIONS_EXTRA) as OSIABWebViewOptions

        setContentView(R.layout.activity_web_view)

        //get elements in screen
        webView = findViewById(R.id.webview)
        closeButton = findViewById(R.id.close_button)
        backButton = findViewById(R.id.back_button)
        forwardButton = findViewById(R.id.forward_button)
        urlText = findViewById(R.id.url_text)
        toolbar = findViewById(R.id.toolbar)

        // setup elements in screen
        closeButton.text = options.closeButtonText.ifBlank { "Close" }
        urlText.text = urlToOpen
        toolbar.isVisible = options.showToolbar

        // clear cache if necessary
        possiblyClearCacheOrSessionCookies()
        // enable third party cookies
        enableThirdPartyCookies()

        setupWebView()
        if (urlToOpen != null) {
            webView.loadUrl(urlToOpen)
        }

        closeButton.setOnClickListener {
            webView.destroy()
            finish()
        }

        backButton.setOnClickListener {
            if (webView.canGoBack()) webView.goBack()
        }

        forwardButton.setOnClickListener {
            if (webView.canGoForward()) webView.goForward()
        }

    }

    override fun onPause() {
        super.onPause()
        if (options.pauseMedia) {
            webView.onPause()
        }
    }

    override fun onResume() {
        super.onResume()
        if (options.pauseMedia) {
            webView.onResume()
        }
    }

    /**
     * Responsible for setting up the WebView that shows the URL.
     * It also deals with URLs that are opened withing the WebView.
     */
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true

        // get webView settings that come from options
        webView.settings.builtInZoomControls = options.allowZoom
        webView.settings.mediaPlaybackRequiresUserGesture = options.mediaPlaybackRequiresUserAction

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // store cookies after page finishes loading
                storeCookies()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val urlString = request?.url.toString()
                return when {
                    // handle tel: links opening the appropriate app
                    urlString.startsWith("tel:") -> {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse(urlString)
                        }
                        startActivity(intent)
                        true
                    }
                    // handle sms: links opening the appropriate app
                    urlString.startsWith("sms:") -> {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse(urlString)
                        }
                        startActivity(intent)
                        true
                    }
                    // handle geo: links opening the appropriate app
                    urlString.startsWith("geo:") -> {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(urlString)
                        }
                        startActivity(intent)
                        true
                    }
                    // handle mailto: links opening the appropriate app
                    urlString.startsWith("mailto:") -> {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse(urlString)
                        }
                        startActivity(intent)
                        true
                    }
                    // handle Google Play store links opening the appropriate app
                    urlString.startsWith("https://play.google.com/store") || urlString.startsWith("market:") -> {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(urlString)
                            setPackage("com.android.vending")
                        }
                        startActivity(intent)
                        true
                    }
                    // handle every http and https link by loading it in the WebView
                    urlString.startsWith("http:") || urlString.startsWith("https:") -> {
                        view?.loadUrl(urlString)
                        urlText.text = urlString
                        true
                    }
                    else -> false
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                // show the default WebView error page
                super.onReceivedError(view, request, error)
            }
        }

        // use WebChromeClient to handle JS events
        webView.webChromeClient = object : WebChromeClient() {

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@OSIABWebViewActivity)
                    .setTitle(appName)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                        result?.confirm()
                        dialog.dismiss()
                    }
                    .setOnDismissListener { dialog ->
                        result?.cancel()
                        dialog.dismiss()
                    }
                    .create()
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                AlertDialog.Builder(this@OSIABWebViewActivity)
                    .setTitle(appName)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                        result?.confirm()
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        result?.cancel()
                        dialog.dismiss()
                    }
                    .setOnDismissListener { dialog ->
                        result?.cancel()
                        dialog.dismiss()
                    }
                    .create()
                    .show()
                return true
            }

            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                val input = EditText(this@OSIABWebViewActivity)
                input.setText(defaultValue)

                AlertDialog.Builder(this@OSIABWebViewActivity)
                    .setTitle(appName)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                        result?.confirm(input.text.toString())
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        result?.cancel()
                        dialog.dismiss()
                    }
                    .setOnDismissListener { dialog ->
                        result?.cancel()
                        dialog.dismiss()
                    }
                    .create()
                    .show()
                return true
            }
        }

    }

    /**
     * Handle the back button press
     */
    override fun onBackPressed() {
        if (options.hardwareBack && webView.canGoBack()) {
            webView.goBack()
        } else {
            webView.destroy()
            super.onBackPressedDispatcher.onBackPressed()
        }
    }

    /**
     * Clears the WebView cache and removes all cookies if 'clearCache' parameter is 'true'.
     * If not, then if 'clearSessionCache' is true, removes the session cookies.
     */
    private fun possiblyClearCacheOrSessionCookies() {
        if (options.clearCache) {
            webView.clearCache(true)
            CookieManager.getInstance().removeAllCookies(null)
        } else if (options.clearSessionCache) {
            CookieManager.getInstance().removeSessionCookies(null)
        }
    }

    /**
     * Enables third party cookies using the CookieManager
     */
    private fun enableThirdPartyCookies() {
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    }

    /**
     * Stores cookies using the CookieManager
     */
    private fun storeCookies() {
        CookieManager.getInstance().flush()
    }

}