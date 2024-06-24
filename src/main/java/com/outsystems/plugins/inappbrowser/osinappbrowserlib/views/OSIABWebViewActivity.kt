package com.outsystems.plugins.inappbrowser.osinappbrowserlib.views


import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.StyleRes
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.isVisible
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.R
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABToolbarPosition
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABWebViewOptions

class OSIABWebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var closeButton: TextView
    private lateinit var backButton: ImageButton
    private lateinit var forwardButton: ImageButton
    private lateinit var urlText: TextView
    private lateinit var toolbar: Toolbar
    private lateinit var bottomToolbar: Toolbar
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


        toolbar = findViewById(R.id.toolbar)
        bottomToolbar = findViewById(R.id.bottom_toolbar)

        if (options.showToolbar) createToolbar(
            options.toolbarPosition,
            options.showNavigationButtons,
            options.leftToRight,
            options.showURL,
            urlToOpen,
            options.closeButtonText.ifBlank { "Close" }
        )

        //we'll always have the top toolbar, because of the Close button
        toolbar.isVisible = options.showToolbar

        bottomToolbar.isVisible =
            options.showToolbar && options.toolbarPosition != OSIABToolbarPosition.TOP

        // clear cache if necessary
        possiblyClearCacheOrSessionCookies()
        // enable third party cookies
        enableThirdPartyCookies()

        setupWebView()
        if (urlToOpen != null) {
            webView.loadUrl(urlToOpen)
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
     * Helper function to update navigation button states
     */
    private fun updateNavigationButtons() {
        updateNavigationButton(backButton, webView.canGoBack())
        updateNavigationButton(forwardButton, webView.canGoForward())
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

        // setup WebViewClient and WebChromeClient
        webView.webViewClient = customWebViewClient()
        webView.webChromeClient = customWebChromeClient()
    }

    /**
     * Use WebViewClient to handle events on the WebView
     */
    private fun customWebViewClient(): WebViewClient {

        val webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // store cookies after page finishes loading
                storeCookies()
                updateNavigationButtons()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val urlString = request?.url.toString()
                return when {
                    // handle tel: links opening the appropriate app
                    urlString.startsWith("tel:") -> {
                        launchIntent(Intent.ACTION_DIAL, urlString)
                    }
                    // handle sms: and mailto: links opening the appropriate app
                    urlString.startsWith("sms:") || urlString.startsWith("mailto:") -> {
                        launchIntent(Intent.ACTION_SENDTO, urlString)
                    }
                    // handle geo: links opening the appropriate app
                    urlString.startsWith("geo:") -> {
                        launchIntent(Intent.ACTION_VIEW, urlString)
                    }
                    // handle Google Play Store links opening the appropriate app
                    urlString.startsWith("https://play.google.com/store") || urlString.startsWith("market:") -> {
                        launchIntent(Intent.ACTION_VIEW, urlString, true)
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

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                // show the default WebView error page
                super.onReceivedError(view, request, error)
            }

            /**
             * Responsible for handling and launching intents based on a URL.
             * @param intentAction Action for the intent
             * @param urlString URL to be processed
             * @param isGooglePlayStore to determine if the URL is a Google Play Store link
             */
            private fun launchIntent(
                intentAction: String,
                urlString: String,
                isGooglePlayStore: Boolean = false
            ): Boolean {
                val intent = Intent(intentAction).apply {
                    data = Uri.parse(urlString)
                    if (isGooglePlayStore) {
                        setPackage("com.android.vending")
                    }
                }
                startActivity(intent)
                return true
            }
        }
        return webViewClient
    }

    /**
     * Use WebChromeClient to handle JS events
     */
    private fun customWebChromeClient(): WebChromeClient {

        val webChromeClient = object : WebChromeClient() {

            // override any methods necessary

        }
        return webChromeClient
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

    /**
     * Creates toolbar for the web view
     * @param toolbarPosition the toolbar position on screen
     * @param showNavigationButtons true, to show the back and forward buttons
     * @param isLeftRight true, to set the navigation buttons on the left
     * @param showURL true, to show the opened url
     * @param urlToOpen the url the webview opens
     * @param closeButtonText the text for the close button
     */
    private fun createToolbar(
        toolbarPosition: OSIABToolbarPosition,
        showNavigationButtons: Boolean,
        isLeftRight: Boolean,
        showURL: Boolean,
        urlToOpen: String?,
        closeButtonText: String
    ) {
        var content: RelativeLayout = toolbar.findViewById(R.id.toolbar_content)

        closeButton = createCloseButton(closeButtonText, isLeftRight)
        content.addView(closeButton)

        if (toolbarPosition == OSIABToolbarPosition.BOTTOM) {
            content = bottomToolbar.findViewById(R.id.bottom_toolbar_content)
        }

        // adds (or not) navigation buttons
        if (showNavigationButtons) {
            val nav = createNavigationButtons(isLeftRight)
            content.addView(nav)
        }

        // if url text is visible
        if (showURL) {
            urlText = createUrlText(urlToOpen.orEmpty(), isLeftRight)
            content.addView(urlText)
        }
    }

    /**
     * Creates a RelativeLayout.LayoutParams instance with the common settings (height, width and
     * alignment)
     * @return new instance of RelativeLayout.LayoutParams
     */
    private fun createCommonLayout(): RelativeLayout.LayoutParams {
        val custom = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        custom.addRule(RelativeLayout.CENTER_VERTICAL)
        return custom
    }

    /**
     * Creates the custom navigation buttons
     * @param isLeftRight defines their placement, inside the toolbar
     * if <code>true</code>, start of the toolbar, else at the end
     * @return a RelativeLayout with the navigation buttons
     */
    private fun createNavigationButtons(isLeftRight: Boolean): RelativeLayout {
        //we wrap the navigation buttons in a relative layout, so they're easier to manipulate
        val nav: RelativeLayout = RelativeLayout(this).apply {
            layoutParams = createCommonLayout().apply {
                if (isLeftRight) addRule(RelativeLayout.ALIGN_PARENT_START)
                else addRule(RelativeLayout.ALIGN_PARENT_END)

            }
            id = R.id.navigation_buttons
            setPaddingRelative(0, 0, 0, 0)
        }

        backButton = createImageButton(R.style.NavigationButton_Back, createCommonLayout())
        backButton.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()

            }
        }

        nav.addView(backButton)
        forwardButton =
            createImageButton(R.style.NavigationButton_Forward, createCommonLayout().apply {
                addRule(RelativeLayout.END_OF, R.id.back_button)
            })
        forwardButton.setOnClickListener {
            if (webView.canGoForward()) {
                webView.goForward()

            }
        }
        nav.addView(forwardButton)
        return nav
    }

    /**
     * Creates the close button, with the specified text and placement
     * @param withText the text in question
     * @param isLeftRight button's placement, if true, on the right side of the toolbar
     * @return a new TextView button
     */
    private fun createCloseButton(withText: String, isLeftRight: Boolean): TextView {
        val params = createCommonLayout().apply {
            if (isLeftRight) addRule(RelativeLayout.ALIGN_PARENT_END)
            else addRule(RelativeLayout.ALIGN_PARENT_START)
        }
        val textView = createTextView(withText, R.style.CloseButton, params)
        textView.setOnClickListener {
            webView.destroy()
            finish()
        }
        return textView
    }

    private fun createUrlText(url: String, isLeftRight: Boolean): TextView {
        val params = createCommonLayout().apply {
            if (isLeftRight) {
                addRule(RelativeLayout.END_OF, R.id.navigation_buttons)
                addRule(RelativeLayout.START_OF, R.id.close_button)
            } else {
                addRule(RelativeLayout.END_OF, R.id.close_button)
                addRule(RelativeLayout.START_OF, R.id.navigation_buttons)
            }
        }
        return createTextView(url, R.style.URLBar, params)
    }

    private fun createTextView(
        withText: String,
        @StyleRes style: Int,
        params: RelativeLayout.LayoutParams
    ): TextView {
        return TextView(ContextThemeWrapper(this, style)).apply {
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            text = withText
            layoutParams = params
        }
    }

    private fun createImageButton(
        @StyleRes style: Int,
        params: RelativeLayout.LayoutParams
    ): ImageButton {
        return ImageButton(ContextThemeWrapper(this, style)).apply {
            layoutParams = params
            isEnabled = false
        }
    }

    // Helper function to apply styles based on enabled/disabled state
    private fun updateNavigationButton(button: ImageButton, isEnabled: Boolean) {
        button.isEnabled = isEnabled
        if (isEnabled) {
            button.alpha = 1.0f
        } else {
            button.alpha = 0.3f
        }
    }
}