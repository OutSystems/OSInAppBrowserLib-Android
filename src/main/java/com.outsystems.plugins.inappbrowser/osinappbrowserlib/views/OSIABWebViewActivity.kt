package com.outsystems.plugins.inappbrowser.osinappbrowserlib.views

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.graphics.Bitmap
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABEvents
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABEvents.OSIABWebViewEvent
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.R
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers.OSIABPdfHelper
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABToolbarPosition
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABWebViewOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class OSIABWebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var closeButton: TextView
    private lateinit var startNavigationButton: ImageButton
    private lateinit var endNavigationButton: ImageButton
    private lateinit var urlText: TextView
    private lateinit var toolbar: Toolbar
    private lateinit var bottomToolbar: Toolbar
    private lateinit var errorView: View
    private lateinit var reloadButton: Button
    private lateinit var loadingView: View
    private lateinit var options: OSIABWebViewOptions
    private lateinit var appName: String
    private lateinit var browserId: String

    // for the browserPageLoaded event, which we only want to trigger on the first URL loaded in the WebView
    private var isFirstLoad = true

    // for the error screen
    private var currentUrl: String? = null
    private var hasLoadError: Boolean = false

    // permissions
    private var currentPermissionRequest: PermissionRequest? = null

    // geolocation permissions
    private var geolocationCallback: GeolocationPermissions.Callback? = null
    private var geolocationOrigin: String? = null
    private var wasGeolocationPermissionDenied = false

    // used in onShowFileChooser when taking photos or videos
    private var currentPhotoFile: File? = null
    private var currentPhotoUri: Uri? = null
    private var currentVideoFile: File? = null
    private var currentVideoUri: Uri? = null

    // for file chooser
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = when {
                result.resultCode != Activity.RESULT_OK -> null
                result.data?.data != null -> WebChromeClient.FileChooserParams.parseResult(
                    result.resultCode,
                    result.data
                ) // file was selected from gallery or file manager, some OEMs also return the video here (e.g. Google)

                // we need to check currentPhotoFile.length() > 0 to make sure a photo was actually taken
                currentPhotoUri != null && currentPhotoFile != null && currentPhotoFile!!.length() > 0 ->
                    arrayOf(currentPhotoUri) // photo capture, since URI is not in data
                // we need to check currentVideoFile.length() > 0 to make sure a video was actually taken
                currentVideoUri != null && currentVideoFile != null && currentVideoFile!!.length() > 0 ->
                    arrayOf(currentVideoUri) // fallback for video capture, if video URI is not in data (e.g. Samsung devices)
                else -> null
            }
            filePathCallback?.onReceiveValue(uris)
            filePathCallback = null
            currentPhotoFile = null
            currentPhotoUri = null
            currentVideoFile = null
            currentVideoUri = null
        }

    // for back navigation
    private lateinit var onBackPressedCallback: OnBackPressedCallback

    private val PDF_VIEWER_URL_PREFIX = "file:///android_asset/pdfjs/web/viewer.html?file="
    // the original URL of the PDF file, used to display it correctly in the view
    // and to send the correct URL in the browserPageNavigationCompleted event
    private var originalUrl: String? = null

    companion object {
        const val WEB_VIEW_URL_EXTRA = "WEB_VIEW_URL_EXTRA"
        const val WEB_VIEW_OPTIONS_EXTRA = "WEB_VIEW_OPTIONS_EXTRA"
        const val CUSTOM_HEADERS_EXTRA = "CUSTOM_HEADERS_EXTRA"
        const val DISABLED_ALPHA = 0.3f
        const val ENABLED_ALPHA = 1.0f
        const val REQUEST_STANDARD_PERMISSION = 622
        const val REQUEST_LOCATION_PERMISSION = 623
        const val REQUEST_CAMERA_PERMISSION = 624
        const val LOG_TAG = "OSIABWebViewActivity"
        val errorsToHandle = listOf(
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_UNSUPPORTED_SCHEME,
            WebViewClient.ERROR_BAD_URL
        )

        private fun createTempFile(context: Context, prefix: String, suffix: String): File {
            val storageDir = context.cacheDir
            val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.getDefault())
            val timeStamp = LocalDateTime.now().format(formatter)
            return File.createTempFile("${prefix}${timeStamp}_", suffix, storageDir)
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!webView.canGoBack()) return
                hideErrorScreen()
                webView.goBack()
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        browserId = intent.getStringExtra(OSIABEvents.EXTRA_BROWSER_ID) ?: ""

        sendWebViewEvent(OSIABWebViewEvent(browserId, this@OSIABWebViewActivity))

        appName = applicationInfo.loadLabel(packageManager).toString()

        // get parameters from intent extras
        val urlToOpen = intent.extras?.getString(WEB_VIEW_URL_EXTRA)
        options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.extras?.getSerializable(
                WEB_VIEW_OPTIONS_EXTRA,
                OSIABWebViewOptions::class.java
            ) ?: OSIABWebViewOptions()
        } else {
            intent.extras?.getSerializable(WEB_VIEW_OPTIONS_EXTRA) as OSIABWebViewOptions
        }

        val customHeaders: Map<String, String>? = intent.getBundleExtra(CUSTOM_HEADERS_EXTRA)?.let { bundle ->
            bundle.keySet().associateWith { bundle.getString(it).orEmpty() }
        }

        setContentView(R.layout.activity_web_view)

        //get elements in screen
        webView = findViewById(R.id.webview)
        errorView = findViewById(R.id.error_layout)
        reloadButton = createReloadButton()
        loadingView = findViewById(R.id.loading_layout)
        toolbar = findViewById(R.id.toolbar)
        bottomToolbar = findViewById(R.id.bottom_toolbar)

        closeButton = findViewById(R.id.close_button)
        closeButton.text = options.closeButtonText.ifBlank { "Close" }
        closeButton.setOnClickListener {
            finish()
        }

        if (options.showToolbar)
            updateToolbar(
                options.toolbarPosition,
                options.showNavigationButtons,
                options.leftToRight,
                options.showURL,
                urlToOpen.orEmpty()
            )

        //we'll always have the top toolbar, because of the Close button
        toolbar.isVisible = options.showToolbar
        bottomToolbar.isVisible =
            options.showToolbar && options.toolbarPosition == OSIABToolbarPosition.BOTTOM
        // clear cache if necessary
        possiblyClearCacheOrSessionCookies()
        // enable third party cookies
        enableThirdPartyCookies()

        setupWebView()
        if (urlToOpen != null) {
            handleLoadUrl(urlToOpen, customHeaders)
            showLoadingScreen()
        }

        enableEdgeToEdge()
    }

    override fun onPause() {
        super.onPause()
        if (options.pauseMedia) {
            webView.onPause()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            sendWebViewEvent(OSIABEvents.BrowserFinished(browserId))
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (options.pauseMedia) {
            webView.onResume()
        }
    }

    private fun handleLoadUrl(url: String, additionalHttpHeaders: Map<String, String>? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            if (OSIABPdfHelper.isContentTypeApplicationPdf(url)) {
                val pdfFile = try { OSIABPdfHelper.downloadPdfToCache(this@OSIABWebViewActivity, url) } catch (_: IOException) { null }
                if (pdfFile != null) {
                    withContext(Dispatchers.Main) {
                        webView.stopLoading()
                        originalUrl = url
                        val pdfJsUrl =
                            PDF_VIEWER_URL_PREFIX + Uri.encode("file://${pdfFile.absolutePath}")
                        webView.loadUrl(pdfJsUrl)
                    }
                    return@launch
                }
            }

            withContext(Dispatchers.Main) {
                webView.loadUrl(url, additionalHttpHeaders ?: emptyMap())
            }
        }
    }


    /**
     * Helper function to update navigation button states
     */
    private fun updateNavigationButtons() {
        var startEnabled = webView.canGoBack()
        var endEnabled = webView.canGoForward()

        if (options.leftToRight) {
            startEnabled = webView.canGoForward()
            endEnabled = webView.canGoBack()
        }
        updateNavigationButton(
            startNavigationButton,
            startEnabled
        )
        updateNavigationButton(
            endNavigationButton,
            endEnabled
        )
    }

    /**
     * Responsible for setting up the WebView that shows the URL.
     * It also deals with URLs that are opened withing the WebView.
     */
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            databaseEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            allowFileAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true

            if (!options.customUserAgent.isNullOrEmpty())
                userAgentString = options.customUserAgent

            // get webView settings that come from options
            builtInZoomControls = options.allowZoom
            mediaPlaybackRequiresUserGesture = options.mediaPlaybackRequiresUserAction
        }

        // setup WebViewClient and WebChromeClient
        webView.webViewClient =
            customWebViewClient(
                options.showNavigationButtons && options.showToolbar,
                options.showURL && options.showToolbar
            )
        webView.webChromeClient = customWebChromeClient()
    }

    /**
     * Use WebViewClient to handle events on the WebView
     */
    private fun customWebViewClient(
        hasNavigationButtons: Boolean,
        showURL: Boolean
    ): WebViewClient {
        return OSIABWebViewClient(hasNavigationButtons, showURL)
    }

    /**
     * Use WebChromeClient to handle JS events
     */
    private fun customWebChromeClient(): WebChromeClient {
        return OSIABWebChromeClient()
    }

    /**
     * Handle permission requests
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_STANDARD_PERMISSION -> {
                val granted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                currentPermissionRequest?.let {
                    if (granted) {
                        it.grant(it.resources)
                    } else {
                        it.deny()
                    }
                }
                currentPermissionRequest = null
            }
            REQUEST_LOCATION_PERMISSION -> {
                // only one of these needs to be granted: ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION
                val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
                wasGeolocationPermissionDenied = !granted
                geolocationCallback?.invoke(geolocationOrigin, granted, false)
                geolocationCallback = null
                geolocationOrigin = null
            }
            REQUEST_CAMERA_PERMISSION -> {
                // permission granted, launch the file chooser
                // permission grant is determined in launchFileChooser
                try {
                    filePathCallback?.let {
                        (webView.webChromeClient as? OSIABWebChromeClient)?.retryFileChooser()
                    }
                } catch (e: Exception) {
                    Log.d(LOG_TAG, "Error launching file chooser. Exception: ${e.message}")
                    (webView.webChromeClient as? OSIABWebChromeClient)?.cancelFileChooser()
                }
            }
        }
    }

    /*
     * Inner class with implementation for WebViewClient
     */
    private inner class OSIABWebViewClient(
        val hasNavigationButtons: Boolean,
        val showURL: Boolean,
    ) : WebViewClient() {

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            hideLoadingScreen()
            if (!hasLoadError) {
                hideErrorScreen()
            }
        }

        var lastPageFinishedUrl: String? = null

        override fun onPageFinished(view: WebView?, url: String?) {
            if (url != null && url == lastPageFinishedUrl && url.startsWith(PDF_VIEWER_URL_PREFIX)) {
                // If the url is the same as the last finished URL and it is a PDF viewer URL,
                // we do not want to trigger the page finished event again.
                // This prevents the event from being sent multiple times
                // since PDF.js triggers onPageFinished multiple times during PDF rendering.
                return
            }
            lastPageFinishedUrl = url

            val resolvedUrl = when {
                url == null -> null
                url.startsWith(PDF_VIEWER_URL_PREFIX) && originalUrl != null -> originalUrl
                else -> url
            }

            if (isFirstLoad && !hasLoadError) {
                sendWebViewEvent(OSIABEvents.BrowserPageLoaded(browserId))
                isFirstLoad = false
            } else if (!hasLoadError) {
                sendWebViewEvent(OSIABEvents.BrowserPageNavigationCompleted(browserId, resolvedUrl))
            }

            if (url?.startsWith(PDF_VIEWER_URL_PREFIX) == true && options.clearCache) {
                webView.evaluateJavascript(
                    "localStorage.clear(); sessionStorage.clear();", null
                )
            }

            // set back to false so that the next successful load
            // if the load fails, onReceivedError takes care of setting it back to true
            hasLoadError = false

            // store cookies after page finishes loading
            storeCookies()
            if (hasNavigationButtons) updateNavigationButtons()
            if (showURL) urlText.text = resolvedUrl
            currentUrl = url
            super.onPageFinished(view, url)
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
                    launchIntent(urlString = urlString)
                }
                // handle intent: urls
                urlString.startsWith("intent:") -> {
                    launchIntent(urlString = urlString, isIntentUri = true)
                }
                // handle Google Play Store links opening the appropriate app
                urlString.startsWith("https://play.google.com/store") || urlString.startsWith("market:") -> {
                    launchIntent(urlString = urlString, isGooglePlayStore = true)
                }
                // handle every http and https link by loading it in the WebView
                urlString.startsWith("http:") || urlString.startsWith("https:") -> {
                    handleLoadUrl(urlString)
                    if (showURL) urlText.text = urlString
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
            // let all errors first be handled by the WebView default error handling mechanism
            super.onReceivedError(view, request, error)

            // We only want to show the error screen for some errors (e.g. no internet)
            // e.g. we don't want to show it for an error where an image fails to load.
            // Also, we only want to show the error screen for errors in loading the main page,
            // a webpage may have errors loading resources but the page itself may still load.
            error?.let {
                if (errorsToHandle.contains(it.errorCode) && request?.isForMainFrame == true) {
                    hasLoadError = true
                    showErrorScreen()
                }
            }
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            // to implement predictive back navigation
            // we only want to have the callback enabled if the WebView can go back to previous page
            // and if the hardwareBack option is enabled
            // if not, we want the system to handle the back press, which will enable the
            // predictive back animation and simply close the WebView
            onBackPressedCallback.isEnabled = webView.canGoBack() && options.hardwareBack
        }

        /**
         * Responsible for handling and launching intents based on a URL.
         * @param intentAction Action for the intent
         * @param urlString URL to be processed
         * @param isGooglePlayStore to determine if the URL is a Google Play Store link
         * @param isIntentUri to determine if urlString is an intent URL
         */
        private fun launchIntent(
            intentAction: String = Intent.ACTION_VIEW,
            urlString: String,
            isGooglePlayStore: Boolean = false,
            isIntentUri: Boolean = false
        ): Boolean {
            try {
                val intent: Intent?
                if (isIntentUri) {
                    intent = Intent.parseUri(urlString, Intent.URI_INTENT_SCHEME)
                } else {
                    intent = Intent(intentAction).apply {
                        data = Uri.parse(urlString)
                        if (isGooglePlayStore) {
                            setPackage("com.android.vending")
                        }
                    }
                }
                startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.d(LOG_TAG, "Failed to launch intent in WebView")
                return false
            }
        }
    }

    /*
     * Inner class with implementation for WebChromeClient
     */
    private inner class OSIABWebChromeClient : WebChromeClient() {

        // for handling uploads (photo, video, gallery, files)
        private var acceptTypes: String = ""
        private var captureEnabled: Boolean = false

        // handle standard permissions (e.g. audio, camera)
        override fun onPermissionRequest(request: PermissionRequest?) {
            request?.let {
                handlePermissionRequest(it)
            }
        }

        // specifically handle geolocation permission
        override fun onGeolocationPermissionsShowPrompt(
            origin: String?,
            callback: GeolocationPermissions.Callback?
        ) {
            if (origin != null && callback != null) {
                handleGeolocationPermission(origin, callback)
            }
        }

        // handle opening the file chooser within the WebView
        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            this@OSIABWebViewActivity.filePathCallback = filePathCallback
            acceptTypes = fileChooserParams.acceptTypes.joinToString()
            captureEnabled = fileChooserParams.isCaptureEnabled

            // if camera permission is declared in manifest but is not granted, request it
            if (hasCameraPermissionDeclared() && !isCameraPermissionGranted()) {
                ActivityCompat.requestPermissions(
                    this@OSIABWebViewActivity,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA_PERMISSION
                )
                // don’t launch chooser yet, wait for permission result
                return true
            }

            try {
                launchFileChooser(acceptTypes, captureEnabled)
                return true
            } catch (npe: NullPointerException) {
                Log.e(
                    LOG_TAG,
                    "Attempted to launch but intent is null; fileChooserParams=$fileChooserParams",
                    npe
                )
                cancelFileChooser()
                return false
            } catch (e: Exception) {
                Log.d(LOG_TAG, "Error launching file chooser. Exception: ${e.message}")
                cancelFileChooser()
                return false
            }
        }

        fun cancelFileChooser() {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            acceptTypes = ""
            captureEnabled = false
        }

        fun retryFileChooser() {
            try {
                launchFileChooser(acceptTypes, captureEnabled)
            } catch (e: Exception) {
                e.printStackTrace()
                cancelFileChooser()
            }
            acceptTypes = ""
            captureEnabled = false
        }

        private fun launchFileChooser(acceptTypes: String = "", isCaptureEnabled: Boolean = false) {
            val intentList = buildPhotoVideoIntents(acceptTypes)
            val permissionNotDeclaredOrGranted = hasCameraPermissionDeclared().not() || isCameraPermissionGranted()

            if (isCaptureEnabled && permissionNotDeclaredOrGranted) {
                // if capture is enabled, we only show the camera and video options
                launchCameraChooser(intentList)
            } else if (!isCaptureEnabled) {
                // if capture is not enabled, we show the full chooser
                launchFullChooser(intentList, acceptTypes, permissionNotDeclaredOrGranted)
            } else {
                // capture is enabled but permission declared and not granted,
                // as our only option is to capture, we cannot proceed
                cancelFileChooser()
                return
            }
        }

        private fun buildPhotoVideoIntents(acceptTypes: String): MutableList<Intent> {
            val intentList = mutableListOf<Intent>()
            val permissionNotDeclaredOrGranted = hasCameraPermissionDeclared().not() || isCameraPermissionGranted()

            if (permissionNotDeclaredOrGranted) {
                if (acceptTypes.contains("image") || acceptTypes.isEmpty()) {
                    currentPhotoFile = createTempFile(this@OSIABWebViewActivity, "IMG_", ".jpg").also { file ->
                        currentPhotoUri = FileProvider.getUriForFile(
                            this@OSIABWebViewActivity,
                            "${this@OSIABWebViewActivity.packageName}.fileprovider",
                            file
                        )
                    }
                    val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
                    }
                    intentList.add(takePictureIntent)
                }
                if (acceptTypes.contains("video") || acceptTypes.isEmpty()) {
                    currentVideoFile = createTempFile(this@OSIABWebViewActivity, "VID_", ".mp4").also { file ->
                        currentVideoFile = file
                        currentVideoUri = FileProvider.getUriForFile(
                            this@OSIABWebViewActivity,
                            "${this@OSIABWebViewActivity.packageName}.fileprovider",
                            file
                        )
                    }
                    val takeVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_OUTPUT, currentVideoUri)
                    }
                    intentList.add(takeVideoIntent)
                }
            }
            return intentList
        }

        private fun launchCameraChooser(intentList: List<Intent>) {
            val chooser = if (intentList.size == 1) {
                intentList[0]
            } else {
                Intent(Intent.ACTION_CHOOSER).apply {
                    putExtra(Intent.EXTRA_INTENT, intentList[0])
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, intentList.drop(1).toTypedArray())
                }
            }
            fileChooserLauncher.launch(chooser)
        }

        private fun launchFullChooser(intentList: List<Intent>, acceptTypes: String, permissionNotDeclaredOrGranted: Boolean) {
            val contentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = when {
                    acceptTypes.contains("video") -> "video/*"
                    acceptTypes.contains("image") -> "image/*"
                    else -> "*/*"
                }
            }
            val chooser = Intent(Intent.ACTION_CHOOSER).apply {
                putExtra(Intent.EXTRA_INTENT, contentIntent)
                if (permissionNotDeclaredOrGranted && intentList.isNotEmpty()) {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, intentList.toTypedArray())
                }
            }
            fileChooserLauncher.launch(chooser)
        }

        private fun isCameraPermissionGranted(): Boolean {
            return ContextCompat.checkSelfPermission(
                this@OSIABWebViewActivity, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        }

        private fun hasCameraPermissionDeclared(): Boolean {
            // The CAMERA permission does not need to be requested unless it is declared in AndroidManifest.xml
            // If it's declared, camera intents will throw SecurityException if permission is not granted
            try {
                val packageManager = this@OSIABWebViewActivity.packageManager
                val permissionsInPackage = packageManager.getPackageInfo(
                    this@OSIABWebViewActivity.packageName,
                    PackageManager.GET_PERMISSIONS
                ).requestedPermissions ?: arrayOf()
                for (permission in permissionsInPackage) {
                    if (permission == Manifest.permission.CAMERA) {
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.d(LOG_TAG, e.message.toString())
            }
            return false
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

     */
    private fun updateToolbar(
        toolbarPosition: OSIABToolbarPosition,
        showNavigationButtons: Boolean,
        isLeftRight: Boolean,
        showURL: Boolean,
        url: String
    ) {
        var content: ConstraintLayout = toolbar.findViewById(R.id.toolbar_content)
        val navigationView: ConstraintLayout = content.findViewById(R.id.navigation_view)

        val nav: LinearLayout = findViewById(R.id.navigation_buttons)
        urlText = content.findViewById(R.id.url_text)

        if (toolbarPosition == OSIABToolbarPosition.BOTTOM) {
            content.removeView(navigationView)
            content = bottomToolbar.findViewById(R.id.bottom_toolbar_content)
            content.addView(navigationView)

            // changing where the url text begins
            val set = ConstraintSet()
            set.clone(content)
            set.connect(
                R.id.navigation_view,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START,
                0
            )
            set.applyTo(content)

        }

        if (!showNavigationButtons) {
            navigationView.removeView(nav)
        } else defineNavigationButtons(isLeftRight, content)
        
        if (!showURL) navigationView.removeView(urlText)
        else defineURLView(url, showNavigationButtons, navigationView, toolbarPosition, isLeftRight)

        if (isLeftRight) {
            bottomToolbar.layoutDirection = View.LAYOUT_DIRECTION_RTL
            toolbar.layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
    }

    /**
     * Sets the URL content and position
     * @param url String to set as the URL element text
     * @param showNavigationButtons to use when determining the position of the URL text
     * @param navigationView ConstraintLayout view representing the navigation view (nav buttons and URL)
     * @param toolbarPosition the toolbar position on screen, to determine the position of the URL
     * @param isLeftRight to use when determining the position of the URL text
     */
    private fun defineURLView(
        url: String,
        showNavigationButtons: Boolean,
        navigationView: ConstraintLayout,
        toolbarPosition: OSIABToolbarPosition,
        isLeftRight: Boolean
    ) {
        urlText.text = url
        if (!showNavigationButtons) {
            val set = ConstraintSet()
            set.clone(navigationView)
            set.connect(
                urlText.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END
            )
            if (toolbarPosition == OSIABToolbarPosition.TOP)
                set.clear(urlText.id, ConstraintSet.START)
            set.applyTo(navigationView)

            if (toolbarPosition == OSIABToolbarPosition.TOP) urlText.gravity = Gravity.START
        } else if (toolbarPosition == OSIABToolbarPosition.BOTTOM)
            urlText.gravity = if (isLeftRight) Gravity.END else Gravity.START
    }

    /**
     * Updates the navigation button variables and
     * defines their onClick listener
     * @param isLeftRight defines their placement, inside the toolbar
     * if <code>true</code>, start of the toolbar, else at the end
     * @param parent the view that contains the buttons
     */
    private fun defineNavigationButtons(isLeftRight: Boolean, parent: View) {
        startNavigationButton = parent.findViewById(R.id.start_button)
        endNavigationButton = parent.findViewById(R.id.end_button)
        startNavigationButton.setOnClickListener {
            if (!isLeftRight) backClick() else forwardClick()
        }

        endNavigationButton.setOnClickListener {
            if (!isLeftRight) forwardClick() else backClick()
        }
    }

    private fun forwardClick() {
        if (webView.canGoForward()) {
            webView.goForward()
        }
    }

    private fun backClick() {
        if (webView.canGoBack()) {
            webView.goBack()
        }
    }

    /**
     * Helper function to apply styles based on enabled/disabled state
     * @param button the button that will be enabled / disabled
     * @param isEnabled whether to enabled or disable the button
     */
    private fun updateNavigationButton(button: ImageButton, isEnabled: Boolean) {
        button.isEnabled = isEnabled
        button.alpha = if (isEnabled) ENABLED_ALPHA else DISABLED_ALPHA
    }

    /**
     * Helper function to create the reload button
     * @return the Button after it has been created
     */
    private fun createReloadButton(): Button {
        return findViewById<Button?>(R.id.reload_button).apply {
            setOnClickListener {
                currentUrl?.let {
                    handleLoadUrl(it)
                    showLoadingScreen()
                }
            }
        }
    }

    /** Responsible for sending events using Kotlin Flows.
     * @param event object to broadcast to the event bus
     */
    private fun sendWebViewEvent(event: OSIABEvents) {
        lifecycleScope.launch {
            OSIABEvents.postEvent(event)
        }
    }

    private fun showErrorScreen() {
        webView.isVisible = false
        errorView.isVisible = true
        loadingView.isVisible = false
    }

    private fun hideErrorScreen() {
        errorView.isVisible = false
        webView.isVisible = true
    }

    private fun showLoadingScreen() {
        loadingView.isVisible = true
        errorView.isVisible = false
        webView.isVisible = false
    }

    private fun hideLoadingScreen() {
        loadingView.isVisible = false
        webView.isVisible = true
    }

    /**
     * Responsible for handling standard permission requests coming from the WebView
     * @param request PermissionRequest containing the permissions to request
     */
    private fun handlePermissionRequest(request: PermissionRequest) {
        val requestPermissionMap = mapOf(
            Pair(
                PermissionRequest.RESOURCE_VIDEO_CAPTURE,
                arrayOf(Manifest.permission.CAMERA)),
            Pair(
                PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS)
            )
        )

        val permissionsNeeded =
            request.resources.fold(mutableListOf<String>()) { accumulator, permission ->
                requestPermissionMap[permission]?.let { manifestPermissionArray ->
                    manifestPermissionArray.forEach { manifestPermission ->
                        if (ContextCompat.checkSelfPermission(
                                this, manifestPermission
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            accumulator.add(manifestPermission)
                        }
                    }
                }
                return@fold accumulator
            }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), REQUEST_STANDARD_PERMISSION)
            currentPermissionRequest = request
        } else {
            request.grant(request.resources)
        }
    }

    /**
     * Responsible for handling geolocation permission requests coming from the WebView
     * @param origin From onGeolocationPermissionsShowPrompt, identifying the origin of the request
     * @param callback Holds the callback of the permission request
     */
    private fun handleGeolocationPermission(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        if (wasGeolocationPermissionDenied) {
            callback.invoke(origin, false, false)
            return
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_LOCATION_PERMISSION
            )
            geolocationCallback = callback
            geolocationOrigin = origin

        } else {
            callback.invoke(origin, true, false)
        }
    }

}
