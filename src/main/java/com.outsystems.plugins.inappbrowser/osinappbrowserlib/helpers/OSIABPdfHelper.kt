package com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers

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
}