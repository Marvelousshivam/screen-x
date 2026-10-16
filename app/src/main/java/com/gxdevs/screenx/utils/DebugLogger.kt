package com.gxdevs.screenx.utils

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.FileProvider
import com.gxdevs.screenx.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.LinkedList
import java.util.Locale

/**
 * Extreme-Depth Diagnostic Logging Engine for ScreenX.
 *
 * Records step-by-step diagnostic events across display metric queries, hardware encoder negotiation,
 * MediaProjection / VirtualDisplay creation, rotation transformations, and remuxing.
 *
 * Provides a one-click export option in Settings that generates a comprehensive log.txt
 * containing device hardware, ROM details, display geometry snapshots, recent in-app events,
 * and system logcat records for seamless sharing with developers.
 */
object DebugLogger {

    private const val TAG = "ScreenX_Diag"
    private const val MAX_LOG_ENTRIES = 3000
    private val logBuffer: MutableList<String> = Collections.synchronizedList(LinkedList())

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(tag: String, message: String, throwable: Throwable? = null) {
        val timestamp = timeFormat.format(Date())
        val logLine = if (throwable != null) {
            "[$timestamp] [$tag] $message\n${Log.getStackTraceString(throwable)}"
        } else {
            "[$timestamp] [$tag] $message"
        }

        // Print to logcat
        Log.i(tag, message, throwable)

        // Add to ring buffer
        logBuffer.add(logLine)
        if (logBuffer.size > MAX_LOG_ENTRIES) {
            logBuffer.removeAt(0)
        }
    }

    fun i(tag: String, message: String) = log(tag, "[INFO] $message")
    fun d(tag: String, message: String) = log(tag, "[DEBUG] $message")
    fun w(tag: String, message: String, throwable: Throwable? = null) = log(tag, "[WARN] $message", throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(tag, "[ERROR] $message", throwable)

    /**
     * Captures a complete hardware display, orientation, and window metrics snapshot.
     */
    fun logOrientationSnapshot(context: Context, tag: String, stepDescription: String) {
        try {
            val sb = java.lang.StringBuilder()
            sb.append("\n═══════════════════════════════════════════════════════════════\n")
            sb.append("📸 ORIENTATION & DISPLAY METRICS SNAPSHOT: $stepDescription\n")
            sb.append("═══════════════════════════════════════════════════════════════\n")

            // 1. WindowManager Bounds
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (wm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val curr = wm.currentWindowMetrics.bounds
                    sb.append("• WindowManager.currentWindowMetrics.bounds: width=${curr.width()}, height=${curr.height()}, rect=${curr}\n")
                } catch (e: Exception) {
                    sb.append("• WindowManager.currentWindowMetrics: Error (${e.message})\n")
                }
                try {
                    val max = wm.maximumWindowMetrics.bounds
                    sb.append("• WindowManager.maximumWindowMetrics.bounds: width=${max.width()}, height=${max.height()}, rect=${max}\n")
                } catch (e: Exception) {
                    sb.append("• WindowManager.maximumWindowMetrics: Error (${e.message})\n")
                }
            }

            // 2. Real Metrics (DisplayMetrics)
            val realMetrics = DisplayMetrics()
            if (wm != null) {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(realMetrics)
                sb.append("• defaultDisplay.getRealMetrics: widthPixels=${realMetrics.widthPixels}, heightPixels=${realMetrics.heightPixels}, densityDpi=${realMetrics.densityDpi}, density=${realMetrics.density}\n")
            }

            // 3. DisplayManager & Active Display
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY)
                ?: @Suppress("DEPRECATION") wm?.defaultDisplay
            if (display != null) {
                val rot = display.rotation
                val rotStr = when (rot) {
                    Surface.ROTATION_0 -> "ROTATION_0 (0°)"
                    Surface.ROTATION_90 -> "ROTATION_90 (90° Landscape)"
                    Surface.ROTATION_180 -> "ROTATION_180 (180° Reverse Portrait)"
                    Surface.ROTATION_270 -> "ROTATION_270 (270° Reverse Landscape)"
                    else -> "Unknown ($rot)"
                }
                sb.append("• Display.rotation: $rotStr (raw=$rot)\n")
                sb.append("• Display.displayId: ${display.displayId}, name=${display.name}, isValid=${display.isValid}\n")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val mode = display.mode
                    sb.append("• Display.mode: ${mode.physicalWidth}x${mode.physicalHeight} @ ${mode.refreshRate}Hz\n")
                }
            } else {
                sb.append("• Display: NULL\n")
            }

            // 4. Resources Configuration
            val config = context.resources.configuration
            val configOriStr = when (config.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> "ORIENTATION_LANDSCAPE (2)"
                Configuration.ORIENTATION_PORTRAIT -> "ORIENTATION_PORTRAIT (1)"
                Configuration.ORIENTATION_UNDEFINED -> "ORIENTATION_UNDEFINED (0)"
                else -> "Unknown (${config.orientation})"
            }
            sb.append("• resources.configuration.orientation: $configOriStr\n")
            sb.append("• resources.configuration.densityDpi: ${config.densityDpi}\n")
            sb.append("• resources.configuration: screenWidthDp=${config.screenWidthDp}, screenHeightDp=${config.screenHeightDp}\n")

            // 5. Standard DisplayMetrics
            val stdMetrics = context.resources.displayMetrics
            sb.append("• resources.displayMetrics: widthPixels=${stdMetrics.widthPixels}, heightPixels=${stdMetrics.heightPixels}, densityDpi=${stdMetrics.densityDpi}\n")
            sb.append("═══════════════════════════════════════════════════════════════")

            log(tag, sb.toString())
        } catch (e: Exception) {
            log(tag, "Failed to capture orientation snapshot: ${e.message}", e)
        }
    }

    /**
     * Generates a complete diagnostic report file (log.txt) and returns the File handle.
     */
    suspend fun generateLogFile(context: Context): File = withContext(Dispatchers.IO) {
        val logsDir = File(context.cacheDir, "logs")
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }

        val logFile = File(logsDir, "log.txt")
        if (logFile.exists()) {
            logFile.delete()
        }

        val sb = java.lang.StringBuilder()
        sb.append("══════════════════════════════════════════════════════════════════════\n")
        sb.append("          SCREENX EXTREME DIAGNOSTIC LOG REPORT                       \n")
        sb.append("══════════════════════════════════════════════════════════════════════\n")
        sb.append("Generated At: ${timeFormat.format(Date())}\n")
        sb.append("ScreenX Version: ${BuildConfig.VERSION_NAME} (versionCode: ${BuildConfig.VERSION_CODE})\n")
        sb.append("Application ID: ${context.packageName}\n\n")

        // 1. Hardware & System Details
        sb.append("─── [1] DEVICE HARDWARE & FIRMWARE ──────────────────────────────────\n")
        sb.append("• Manufacturer: ${Build.MANUFACTURER}\n")
        sb.append("• Brand: ${Build.BRAND}\n")
        sb.append("• Model: ${Build.MODEL}\n")
        sb.append("• Device (Codename): ${Build.DEVICE}\n")
        sb.append("• Product: ${Build.PRODUCT}\n")
        sb.append("• Hardware: ${Build.HARDWARE}\n")
        sb.append("• Board: ${Build.BOARD}\n")
        sb.append("• Bootloader: ${Build.BOOTLOADER}\n")
        sb.append("• Build Display ID: ${Build.DISPLAY}\n")
        sb.append("• Build Fingerprint: ${Build.FINGERPRINT}\n")
        sb.append("• Android OS: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            sb.append("• Security Patch: ${Build.VERSION.SECURITY_PATCH}\n")
        }

        // Custom ROM & OEM Properties
        val miuiVer = getSystemProperty("ro.miui.ui.version.name")
        val hyperOsVer = getSystemProperty("ro.mi.os.version.name")
        val romVer = getSystemProperty("ro.modversion").takeIf { it.isNotBlank() }
            ?: getSystemProperty("ro.build.display.id")
        val customRomName = getSystemProperty("ro.infinity.version").takeIf { it.isNotBlank() }
            ?: getSystemProperty("ro.lineage.version").takeIf { it.isNotBlank() }
            ?: getSystemProperty("ro.crdroid.version").takeIf { it.isNotBlank() }
            ?: "AOSP/Stock"

        sb.append("• Detected ROM Flavor: $customRomName\n")
        if (miuiVer.isNotBlank()) sb.append("• MIUI Version: $miuiVer\n")
        if (hyperOsVer.isNotBlank()) sb.append("• HyperOS Version: $hyperOsVer\n")
        if (romVer.isNotBlank()) sb.append("• ROM Identifier: $romVer\n")
        sb.append("\n")

        // 2. Hardware Video Encoder Capabilities
        sb.append("─── [2] HARDWARE AVC ENCODER CAPABILITIES ──────────────────────────\n")
        try {
            val videoCaps = DeviceCapabilitiesHelper.getHardwareVideoCapabilities()
            if (videoCaps != null) {
                sb.append("• Supported Widths: ${videoCaps.supportedWidths}\n")
                sb.append("• Supported Heights: ${videoCaps.supportedHeights}\n")
                sb.append("• Width Alignment: ${videoCaps.widthAlignment}\n")
                sb.append("• Height Alignment: ${videoCaps.heightAlignment}\n")
                sb.append("• Supported Bitrates: ${videoCaps.bitrateRange}\n")
                sb.append("• Supported Frame Rates: ${videoCaps.supportedFrameRates}\n")
            } else {
                sb.append("• Hardware AVC Capabilities: Query returned null\n")
            }
        } catch (e: Exception) {
            sb.append("• Hardware Capabilities Query Failed: ${e.message}\n")
        }
        sb.append("\n")

        // 3. Current Real-Time Display Metrics
        sb.append("─── [3] ACTIVE DISPLAY & GEOMETRY ──────────────────────────────────\n")
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (wm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val curr = wm.currentWindowMetrics.bounds
                val max = wm.maximumWindowMetrics.bounds
                sb.append("• currentWindowMetrics: width=${curr.width()}, height=${curr.height()} (Rect: $curr)\n")
                sb.append("• maximumWindowMetrics: width=${max.width()}, height=${max.height()} (Rect: $max)\n")
            }
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm?.defaultDisplay?.getRealMetrics(metrics)
            sb.append("• defaultDisplay.getRealMetrics: width=${metrics.widthPixels}, height=${metrics.heightPixels}, dpi=${metrics.densityDpi}\n")

            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY) ?: @Suppress("DEPRECATION") wm?.defaultDisplay
            if (display != null) {
                val rot = display.rotation
                val rotStr = when (rot) {
                    Surface.ROTATION_0 -> "ROTATION_0 (0° Portrait)"
                    Surface.ROTATION_90 -> "ROTATION_90 (90° Landscape)"
                    Surface.ROTATION_180 -> "ROTATION_180 (180° Reverse Portrait)"
                    Surface.ROTATION_270 -> "ROTATION_270 (270° Reverse Landscape)"
                    else -> "Unknown ($rot)"
                }
                sb.append("• display.rotation: $rotStr (raw int: $rot)\n")
            }
            val cfg = context.resources.configuration
            val cfgOri = if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE) "LANDSCAPE" else "PORTRAIT"
            sb.append("• configuration.orientation: $cfgOri (${cfg.orientation})\n")
            sb.append("• configuration.densityDpi: ${cfg.densityDpi}\n")
        } catch (e: Exception) {
            sb.append("• Error querying active display: ${e.message}\n")
        }
        sb.append("\n")

        // 4. In-App Diagnostic Event Log (Chronological)
        sb.append("─── [4] CHRONOLOGICAL EVENT LOG (IN-APP) ────────────────────────────\n")
        val events = synchronized(logBuffer) { ArrayList(logBuffer) }
        if (events.isEmpty()) {
            sb.append("No in-app recording events recorded yet in this session.\n")
        } else {
            for (line in events) {
                sb.append(line).append("\n")
            }
        }
        sb.append("\n")

        // 5. System Logcat Snippet (Recent 350 lines)
        sb.append("─── [5] SYSTEM LOGCAT DUMP (RECENT BUFFER) ─────────────────────────\n")
        try {
            val process = Runtime.getRuntime().exec("logcat -d -v time -t 350 ScreenX:V ScreenRecordService:V AdbRecordService:V ScreenX_Diag:V MediaCodec:W MediaRecorder:W MediaProjection:V SurfaceFlinger:W *:E")
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                var count = 0
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                    count++
                }
                if (count == 0) {
                    sb.append("(Logcat buffer empty or permission restricted by OEM)\n")
                }
            }
        } catch (e: Exception) {
            sb.append("Unable to execute logcat -d: ${e.message}\n")
        }

        sb.append("\n══════════════════════════════════════════════════════════════════════\n")
        sb.append("                        END OF LOG REPORT                             \n")
        sb.append("══════════════════════════════════════════════════════════════════════\n")

        FileOutputStream(logFile).use { fos ->
            fos.write(sb.toString().toByteArray(Charsets.UTF_8))
        }

        logFile
    }

    /**
     * Shares the generated log.txt file via Android Intent.ACTION_SEND chooser.
     */
    suspend fun shareLogFile(context: Context) {
        val file = generateLogFile(context)
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ScreenX Diagnostic Logs (${Build.MODEL})")
            putExtra(Intent.EXTRA_TEXT, "ScreenX diagnostic log for ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) running Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share ScreenX log.txt via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun getSystemProperty(key: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            (method.invoke(null, key, "") as? String)?.trim() ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
