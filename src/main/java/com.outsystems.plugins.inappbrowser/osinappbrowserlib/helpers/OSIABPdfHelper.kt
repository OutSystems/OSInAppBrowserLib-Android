package com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers

import android.content.Context
import android.content.Intent
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABWebViewOptions
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.views.OSIABPdfViewerActivity
import java.net.HttpURLConnection
import java.net.URL

object OSIABPdfHelper {

    /**
     * Checks if the content type of the URL is application/pdf.
     * @param urlString The URL to check.
     * @return True if the content type is application/pdf, false otherwise.
     */
    fun isContentTypeApplicationPdf(urlString: String): Boolean {
        return try {
            val url = URL(urlString)
            (url.openConnection() as? HttpURLConnection)?.run {
                instanceFollowRedirects = true
                requestMethod = "HEAD"
                connect()
                val type = contentType
                val disposition = getHeaderField("Content-Disposition")
                type == "application/pdf" ||
                        (type == null && disposition?.contains(".pdf", ignoreCase = true) == true)
            } ?: false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Opens the PDF viewer activity with the given URL.
     * @param url The URL of the PDF to be viewed.
     */
    fun openPdfViewer(context: Context, url: String, options: OSIABWebViewOptions) {
        val intent = Intent(context, OSIABPdfViewerActivity::class.java).apply {
            putExtra("PDF_URL", url)
            putExtra(OSIABPdfViewerActivity.WEB_VIEW_OPTIONS_EXTRA, options)
        }
        context.startActivity(intent)
    }
}