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

## Motivation

This library is to be used by the InAppBrowser Plugin for [OutSystems' Cordova Plugin](https://github.com/OutSystems/cordova-outsystems-inappbrowser) and [Ionic's Capacitor Plugin](https://github.com/ionic-team/capacitor-os-inappbrowser).

## Usage

> Replace ${version to use} below with the version you want to use. Check the Releases page for available versions.

In your app-level gradle file, import the `OSInAppBrowserLib` library like so:

    dependencies {
    	implementation("com.github.outsystems:osinappbrowser-android:${version to use}@aar")
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