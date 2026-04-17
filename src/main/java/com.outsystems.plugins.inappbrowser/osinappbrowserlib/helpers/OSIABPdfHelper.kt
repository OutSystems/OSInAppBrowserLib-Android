package com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.URL

object OSIABPdfHelper {

    fun isPdf(mimeType: String?, contentDisposition: String?): Boolean {
        return mimeType == "application/pdf" || contentDisposition?.contains(".pdf") == true
    }

    @Throws(IOException::class)
    fun downloadPdfToCache(context: Context, url: String): File {
        val pdfFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.pdf")
        URL(url).openStream().use { input ->
            pdfFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return pdfFile
    }
}
