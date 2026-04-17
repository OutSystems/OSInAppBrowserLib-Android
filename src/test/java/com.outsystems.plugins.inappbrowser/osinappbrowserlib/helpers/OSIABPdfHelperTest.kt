package com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import kotlin.concurrent.thread

class OSIABPdfHelperTest {

    // region isPdf

    @Test
    fun `isPdf returns true when mimeType is application pdf`() {
        assertTrue(OSIABPdfHelper.isPdf("application/pdf", null))
    }

    @Test
    fun `isPdf returns true when contentDisposition contains pdf extension`() {
        assertTrue(OSIABPdfHelper.isPdf(null, "attachment; filename=test.pdf"))
    }

    @Test
    fun `isPdf returns true when both mimeType and contentDisposition indicate pdf`() {
        assertTrue(OSIABPdfHelper.isPdf("application/pdf", "attachment; filename=test.pdf"))
    }

    @Test
    fun `isPdf returns true when mimeType is not pdf but contentDisposition contains pdf extension`() {
        assertTrue(OSIABPdfHelper.isPdf("text/html", "attachment; filename=report.pdf"))
    }

    @Test
    fun `isPdf returns false when neither mimeType nor contentDisposition indicate pdf`() {
        assertFalse(OSIABPdfHelper.isPdf("text/html", "inline"))
    }

    @Test
    fun `isPdf returns false when both are null`() {
        assertFalse(OSIABPdfHelper.isPdf(null, null))
    }

    // endregion

    // region downloadPdfToCache

    @Test
    fun `downloadPdfToCache creates file with content`() {
        val server = ServerSocket(0)
        val port = server.localPort
        val pdfBytes = "%PDF-1.4 test".toByteArray()
        thread {
            val client: Socket = server.accept()
            val out = client.getOutputStream()
            out.write(
                ("HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/pdf\r\n" +
                        "Content-Length: ${pdfBytes.size}\r\n" +
                        "\r\n").toByteArray()
            )
            out.write(pdfBytes)
            out.flush()
            client.close()
            server.close()
        }

        val context = mockk<Context>()
        val cacheDir = Files.createTempDirectory("test_cache").toFile()
        every { context.cacheDir } returns cacheDir

        val url = "http://localhost:$port/test.pdf"
        val file = OSIABPdfHelper.downloadPdfToCache(context, url)

        assertTrue(file.exists())
        assertTrue(file.readBytes().copyOfRange(0, 8).contentEquals("%PDF-1.4".toByteArray()))
        file.delete()
        cacheDir.deleteRecursively()
    }

    // endregion
}
