# OSInAppBrowserLib

The `OSInAppBrowserLib-Android` is a library built using `Kotlin` that provides a web browser view to load a web page within a Mobile Application. It behaves as a standard web browser and is useful to load untrusted content without risking your application's security.

The `OSIABEngine` structure provides the main features of the Library, which are 3 different ways to open a URL:
- using an External Browser;
- using a System Browser;
- using a Web View.

There's also an `OSIABClosable` interface that handles closing an opened browser.

Each is detailed in the following sections.

## Index

- [Motivation](#motivation)
- [Usage](#usage)
- [Methods](#methods)
    - [Open a URL in an External Browser](#open-a-url-in-an-external-browser)
    - [Open a URL in a System Browser](#open-a-url-in-a-system-browser)
    - [Open a URL in a Web View](#open-a-url-in-a-web-view)
    - [Close](#close)
- [Debug log capture (RMET-5394)](#debug-log-capture-rmet-5394)
    - [Enabling capture](#enabling-capture)
    - [Sharing log files](#sharing-log-files)
    - [Removing log files](#removing-log-files)
    - [What gets logged](#what-gets-logged)

## Motivation

This library is to be used by the InAppBrowser Plugin for [OutSystems' Cordova Plugin](https://github.com/OutSystems/cordova-outsystems-inappbrowser) and [Ionic's Capacitor Plugin](https://github.com/ionic-team/capacitor-os-inappbrowser).

## Usage

In your app-level gradle file, import the `OSInAppBrowserLib` library like so:

    dependencies {
    	implementation("com.github.outsystems:osinappbrowser-android:1.0.0@aar")
	}


## Methods

As mentioned before, the library offers the `OSIABEngine` structure that provides the following methods to interact with:

### Open a URL in an External Browser

```kotlin
fun openExternalBrowser(externalBrowserRouter: OSIABRouter<Boolean>, url: String, completionHandler: (Boolean) -> Unit)
```

Uses the parameter `externalBrowserRouter` - an object that offers an External Browser interface - to open the parameter `url`. The method is composed of the following input parameters:
- **url**: the URL for the web page to be opened.
- **externalBrowserRouter**: The External Browser interface that will open the URL. Its return type should be `Bool`. The library provides an `OSIABExternalBrowserRouterAdapter` class that delegates the open operation to the device's default browser.
- **completionHandler**: The callback with the result of opening the URL with the External Browser interface.

### Open a URL in a System Browser

```kotlin
fun openCustomTabs(customTabsRouter: OSIABRouter<Boolean>, url: String, completionHandler: (Boolean) -> Unit)
```

Uses the parameter `customTabsRouter` - an object that offers a System Browser interface - to open the parameter `url`. The method is composed of the following input parameters:
- **url**: the URL for the web page to be opened.
- **customTabsRouter**: The System Browser interface that will open the URL. The library provides an `OSIABCustomTabsRouterAdapter` class that uses a `CustomTabsSession` object to open it. 
- **completionHandler**: The callback with the result of opening the URL with the System Browser interface.

### Open a URL in a Web View

```kotlin
fun openWebView(webViewRouter: OSIABRouter<Boolean>, url: String, completionHandler: (Boolean) -> Unit)
```

Uses the parameter `webViewRouter` - an object that offers a Web View interface - to open the parameter `url`. The method is composed of the following input parameters:
- **url**: the URL for the web page to be opened.
- **webViewRouter**: The Web View interface that will open the URL. The library provides an `OSIABWebViewRouterAdapter` class that uses `WebView` to open it. 
- **completionHandler**: The callback with the result of opening the URL with the Web View interface.

### Close

```kotlin
fun close(completionHandler: (Boolean) -> Unit)
```

Handles closing an opened browser. The method is composed of the following input parameters:
- **completionHandler**: The callback with the result of closing the browser.

## Debug log capture (RMET-5394)

> This is a **debug-only diagnostic tool**, not part of the library's public API surface, and not intended for production builds. It exists to capture repro sessions for [RMET-5394](https://outsystemsrd.atlassian.net/browse/RMET-5394) (main process getting frozen while the isolated Web View is in the foreground) on devices/scenarios where staying attached via `adb` isn't practical - USB debugging suppresses the freeze under investigation, and Wi-Fi debugging has been unstable in practice.

`OSIABLogCaptureHelper` (in `helpers/OSIABLogCaptureHelper.kt`) shells out to the device's `logcat` binary and tails the full device log - not just this library's own log lines, but everything logged under the app's UID (other plugins, host app code, etc.) - to a rotating set of files on disk, with no live debugger connection required.

### Enabling capture

Because the Web View runs in its own isolated process, capture needs to start as early as possible in **both** processes. Android calls `Application.onCreate()` independently in every process the app spawns (main process at launch, and again in the isolated process when it's created for the browser), so adding a single call there covers both:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OSIABLogCaptureHelper.start(this)
    }

    // optional but recommended: see "What gets logged" below
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        OSIABLogCaptureHelper.logTrimMemory(level)
    }
}
```

Register the custom `Application` class in the consuming app's manifest if it doesn't already declare one:

```xml
<application
    android:name=".MyApplication"
    ...>
```

Logs are written to `<cacheDir>/logs/oslog-<main|isolated>-<yyyyMMdd_HHmmss>.txt`, rotating every 10 MB per file. Files are **not** deleted automatically - only [`deleteLogs`](#removing-log-files) removes them - so remember to clear them between test sessions.

Requires API 28+ (`Application.getProcessName()`); below that, capture still runs but can't distinguish the isolated process from the main one.

### Sharing log files

Call `OSIABLogCaptureHelper.shareLogs(context)` to open a standard share chooser (email, Drive, Slack, etc.) with every captured log file attached:

```kotlin
OSIABLogCaptureHelper.shareLogs(context)
```

`OSIABWebViewActivity` already wires this up with two built-in triggers, so no extra code is needed in most cases:
- **Long-press the Close button** - only reachable when the toolbar is shown (`showToolbar: true`).
- **Long-press Volume Down** - always reachable regardless of toolbar visibility, since it's intercepted at the Activity level (`onKeyLongPress`) before the Web View ever sees the key. A normal short press still adjusts volume as usual.

You can still call `shareLogs(context)` directly from anywhere else convenient (e.g. temporarily added to app code) if neither of those fits your repro.

### Removing log files

Call `OSIABLogCaptureHelper.deleteLogs(context)` to delete every captured log file:

```kotlin
OSIABLogCaptureHelper.deleteLogs(context)
```

### What gets logged

Beyond the raw `logcat` tail, a few signals are deliberately emitted to make the captured logs useful for diagnosing RMET-5394 specifically:

- **`OSIABEvents` send/receive timestamps** - `broadcastEvent()` logs right before sending (from the isolated process, which never freezes), and the registered receiver logs immediately on `onReceive()` (in whichever process registered it, typically the main process). The gap between these two log lines is the most direct evidence of the main process being frozen - e.g. "sent at T, received at T+40s" - rather than something inferred indirectly.
- **Activity lifecycle breadcrumbs** - `OSIABWebViewActivity.onCreate`/`onDestroy` log the `browserId` and (for `onDestroy`) `isFinishing`, and `OSIABEvents.registerReceiver`/`unregisterReceiver` log their ref-counted register/unregister transitions. Mainly for timeline correlation across the two processes' separate log files.
- **`onTrimMemory` levels** - `OSIABLogCaptureHelper.logTrimMemory(level)` logs Android's own `ComponentCallbacks2.onTrimMemory()` signal, an early OS-native indicator of a process trending toward the cached/frozen state, ahead of an actual freeze taking effect. `OSIABWebViewActivity` already logs this for the isolated process; call it from the consuming app's own `Application.onTrimMemory()` (see [Enabling capture](#enabling-capture)) to get the same signal for the main process.
- **WebView JS console messages** - `OSIABWebChromeClient.onConsoleMessage` bridges page `console.log`/`warn`/`error` output into the same log capture, so what the page believed happened (e.g. "payment complete, notifying app") can be correlated against when the native app actually reacted.