package com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
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

    // RMET-5394 debug build only: shake-to-share tuning
    private const val SHAKE_THRESHOLD_GRAVITY = 2.7f
    private const val SHAKE_SLOP_TIME_MS = 200L
    private const val SHAKE_COUNT_RESET_TIME_MS = 3000L
    private const val SHAKE_COUNT_THRESHOLD = 2
    private const val SHAKE_TRIGGER_COOLDOWN_MS = 5000L

    private var logcatProcess: Process? = null
    private var shakeSensorManager: SensorManager? = null
    private var shakeCount = 0
    private var lastShakeTimestamp = 0L
    private var lastShakeTriggerTimestamp = 0L

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

    /**
     * Starts shake-to-share: 2 shakes within ~3 seconds calls [shareLogs]. Meant to
     * be started once from the consuming app's Application.onCreate() (same call
     * site, and same reasoning, as [start]) - since sensors aren't tied to any
     * specific Activity's focus, one registration per process covers every screen,
     * including both inside and outside the isolated Web View. No-op if already
     * started in this process.
     */
    fun startShakeToShare(context: Context) {
        if (shakeSensorManager != null) return
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        manager.registerListener(
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val gX = event.values[0] / SensorManager.GRAVITY_EARTH
                    val gY = event.values[1] / SensorManager.GRAVITY_EARTH
                    val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
                    val gForce = kotlin.math.sqrt(gX * gX + gY * gY + gZ * gZ)
                    if (gForce < SHAKE_THRESHOLD_GRAVITY) return

                    val now = SystemClock.elapsedRealtime()
                    if (lastShakeTimestamp + SHAKE_SLOP_TIME_MS > now) return
                    if (lastShakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) shakeCount = 0
                    lastShakeTimestamp = now
                    shakeCount++

                    if (shakeCount >= SHAKE_COUNT_THRESHOLD &&
                        now - lastShakeTriggerTimestamp > SHAKE_TRIGGER_COOLDOWN_MS
                    ) {
                        lastShakeTriggerTimestamp = now
                        shakeCount = 0
                        shareLogs(appContext)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            },
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )
        shakeSensorManager = manager
    }

    /**
     * Logs a ComponentCallbacks2.onTrimMemory() level. Meant to be called from both
     * the consuming app's Application.onTrimMemory() (main process) and this
     * library's Activity.onTrimMemory() (isolated process) - it's an early, OS-native
     * signal of a process trending toward the cached/frozen state, ahead of an actual
     * freeze taking effect.
     */
    fun logTrimMemory(level: Int) {
        Log.d(LOG_TAG, "onTrimMemory level=$level (${trimMemoryLevelName(level)})")
    }

    private fun trimMemoryLevelName(level: Int): String = when (level) {
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE"
        ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
        else -> "UNKNOWN($level)"
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
