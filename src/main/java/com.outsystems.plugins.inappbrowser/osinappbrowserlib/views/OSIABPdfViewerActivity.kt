package com.outsystems.plugins.inappbrowser.osinappbrowserlib.views

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.github.barteksc.pdfviewer.PDFView
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.R
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABWebViewOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest

class OSIABPdfViewerActivity : AppCompatActivity() {

    private lateinit var pdfView: PDFView
    private lateinit var loadingView: View
    private lateinit var options: OSIABWebViewOptions

    companion object {
        const val PDF_URL = "PDF_URL"
        const val WEB_VIEW_OPTIONS_EXTRA = "WEB_VIEW_OPTIONS_EXTRA"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.extras?.getSerializable(WEB_VIEW_OPTIONS_EXTRA, OSIABWebViewOptions::class.java)
                ?: OSIABWebViewOptions()
        } else {
            @Suppress("DEPRECATION")
            intent.extras?.getSerializable(WEB_VIEW_OPTIONS_EXTRA) as? OSIABWebViewOptions
                ?: OSIABWebViewOptions()
        }
        findViewById<TextView>(R.id.close_button).apply {
            text = options.closeButtonText.ifBlank { "Close" }
            setOnClickListener { finish() }
        }
        loadingView = findViewById(R.id.loading_layout)

        pdfView = findViewById(R.id.pdfView)

        val pdfUrl = intent.getStringExtra(PDF_URL)
        if (pdfUrl != null) {
            loadPdf(pdfUrl)
        } else {
            finish()
        }
    }

    private fun loadPdf(url: String) {
        lifecycleScope.launch {
            try {
                showLoading(true)
                val file = withContext(Dispatchers.IO) { downloadPdf(url) }
                openPdf(file)
            } catch (e: Exception) {
                Toast.makeText(this@OSIABPdfViewerActivity, "Failed to load PDF", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun downloadPdf(url: String): File {
        val fileName = generateFileName(url)
        val file = File(cacheDir, fileName)
        if (!file.exists()) {
            URL(url).openStream().use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file
    }

    private fun openPdf(file: File) {
        pdfView.fromFile(file)
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .defaultPage(0)
            .load()
    }

    private fun generateFileName(url: String): String {
        val hash = MessageDigest.getInstance("MD5")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "$hash.pdf"
    }

    private fun showLoading(show: Boolean) {
        loadingView.isVisible = show
    }
}
