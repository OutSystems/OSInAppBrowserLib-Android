package com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Debug-only helper (RMET-5394) that captures the full device log - not just this
 * library's own log lines - to a rotating set of files, for repro sessions where a
 * live adb connection can't be used (USB suppresses the process freeze under
 * investigation; Wi-Fi debugging has been unstable). Not intended for production use.
 *
 * Meant to be started once from the consuming app's Application.onCreate(), which
 * Android calls independently in every process the app spawns (main process at
 * launch, and again in the isolated WebView process when it's created), so a single
 * call site covers both.
 */
object OSIABLogCaptureHelper {

    private const val LOG_TAG = "OSIABLogCaptureHelper"
    private const val LOG_DIR_NAME = "logs"
    private const val ROTATE_KB = "10240" // 10 MB per file
    private const val MAX_ROTATED_FILES = "9999" // large on purpose: only deleteLogs() should remove files
    private const val ISOLATED_PROCESS_SUFFIX = ":OSInAppBrowser"

    private var logcatProcess: Process? = null

    /**
     * Starts a `logcat` subprocess that tails the device log to a timestamped file
     * under [Context.getCacheDir]/logs. No-op if already started in this process.
     */
    fun start(context: Context) {
        if (logcatProcess != null) return

        val logDir = File(context.cacheDir, LOG_DIR_NAME).apply { mkdirs() }
        val timestamp = LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.getDefault())
        )
        val logFile = File(logDir, "oslog-${processSuffix()}-$timestamp.txt")

        try {
            logcatProcess = ProcessBuilder(
                "logcat", "-v", "threadtime",
                "-r", ROTATE_KB,
                "-n", MAX_ROTATED_FILES,
                "-f", logFile.absolutePath
            ).redirectErrorStream(true).start()
        } catch (e: Exception) {
            Log.d(LOG_TAG, "Failed to start log capture: ${e.message}")
        }
    }

    /**
     * Stops the `logcat` subprocess for this process, if running.
     */
    fun stop() {
        logcatProcess?.destroy()
        logcatProcess = null
    }

    /**
     * Opens a share chooser with every captured log file for this device.
     * [context] must be an Activity context, or the intent will carry
     * FLAG_ACTIVITY_NEW_TASK to allow launching from an Application context.
     */
    fun shareLogs(context: Context) {
        val files = File(context.cacheDir, LOG_DIR_NAME).listFiles()?.filter { it.isFile }
        if (files.isNullOrEmpty()) return

        val authority = "${context.packageName}.fileprovider"
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, authority, it) })

        val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/plain"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserIntent = Intent.createChooser(sendIntent, "Share OSIAB logs").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Log.d(LOG_TAG, "Failed to launch log share chooser: ${e.message}")
        }
    }

    /**
     * Deletes every captured log file for this device.
     */
    fun deleteLogs(context: Context) {
        File(context.cacheDir, LOG_DIR_NAME).listFiles()?.forEach { it.delete() }
    }

    private fun processSuffix(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            Application.getProcessName().endsWith(ISOLATED_PROCESS_SUFFIX)
        ) {
            "isolated"
        } else {
            "main"
        }
}
