package com.gxdevs.screenx.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.StatFs
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gxdevs.screenx.MainActivity
import com.gxdevs.screenx.data.AdbManager
import com.gxdevs.screenx.data.SettingsManager
import android.content.ComponentName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.gxdevs.screenx.R
import com.gxdevs.screenx.utils.LocaleHelper
import com.gxdevs.screenx.utils.DebugLogger
import com.gxdevs.screenx.utils.VideoMerger

/**
 * Foreground service that runs `screenrecord` via the ADB TCP connection.
 *
 * Lifecycle:
 *   ACTION_START_ADB → runs screenrecord for up to TIME_LIMIT_SECS seconds
 *                       (Android's hard 3-min limit)
 *                       auto-stops when screenrecord exits, MediaScans the file.
 *   ACTION_STOP_ADB  → sends SIGINT to screenrecord, letting it finalize the file.
 */
class AdbRecordService : LifecycleService() {

    override fun attachBaseContext(newBase: Context) {
        val langCode = SettingsManager.getLanguageSync(newBase)
        val localizedContext = LocaleHelper.applyLocale(newBase, langCode)
        super.attachBaseContext(localizedContext)
    }

    companion object {
        private const val TAG = "AdbRecordService"

        const val CHANNEL_ID      = "ScreenX_ADB_Channel"
        const val NOTIFICATION_ID = 889

        const val ACTION_START_ADB = "com.gxdevs.screenx.action.START_ADB"
        const val ACTION_STOP_ADB  = "com.gxdevs.screenx.action.STOP_ADB"
        const val EXTRA_CALLER_IS_LANDSCAPE = "extra_caller_is_landscape"

        /** screenrecord --time-limit in seconds. Stay under 180 so it saves cleanly. */
        const val TIME_LIMIT_SECS = 170

        @Volatile var isRecording = false
            private set

        private val _isRecordingFlow = MutableStateFlow(false)
        val isRecordingFlow: StateFlow<Boolean> = _isRecordingFlow.asStateFlow()
    }

    private lateinit var settingsManager: SettingsManager
    private var outputPath: String? = null
    @Volatile private var userStopRequested = false
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var recordingStartTimeMs: Long = 0L

    private var orientationListener: android.view.OrientationEventListener? = null
    @Volatile var isSensorLandscape = false
        private set
    private var windowContext: Context? = null
    var callerIsLandscape: Boolean = false
        private set

    private var audioCaptureHelper: AudioCaptureHelper? = null
    private var tempAudioFile: File? = null

    @Suppress("DEPRECATION")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = pm.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ON_AFTER_RELEASE,
                "ScreenX:AdbWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(TIME_LIMIT_SECS * 1000L + 30_000L)
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private var isScreenOffReceiverRegistered = false

    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                lifecycleScope.launch {
                    val stopOnScreenOff = settingsManager.stopOnScreenOffFlow.first()
                    if (stopOnScreenOff) {
                        if (isRecording) {
                            Log.i(TAG, "Screen off detected — stopping stealth recording")
                            userStopRequested = true
                            stopAdbRecording()
                        }
                        if (ScreenRecordService.isRecording) {
                            Log.i(TAG, "Screen off detected — stopping standard recording")
                            try {
                                val stopIntent = Intent(this@AdbRecordService, ScreenRecordService::class.java).apply {
                                    action = ScreenRecordService.ACTION_STOP
                                }
                                startService(stopIntent)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to send STOP to ScreenRecordService", e)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        createNotificationChannel()

        // Hardware sensor orientation detection
        orientationListener = object : android.view.OrientationEventListener(this, android.hardware.SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) return
                val landscape = (orientation in 50..130) || (orientation in 230..310)
                val portrait = (orientation in 0..40) || (orientation in 320..359) || (orientation in 140..220)
                if (landscape) {
                    isSensorLandscape = true
                } else if (portrait) {
                    isSensorLandscape = false
                }
            }
        }
        if (orientationListener?.canDetectOrientation() == true) {
            orientationListener?.enable()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val dm = getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
                val defDisp = dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
                if (defDisp != null) {
                    windowContext = createDisplayContext(defDisp).createWindowContext(
                        android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "WindowContext creation failed: ${e.message}")
            }
        }

        if (!isScreenOffReceiverRegistered) {
            try {
                val screenOffFilter = android.content.IntentFilter(Intent.ACTION_SCREEN_OFF)
                registerReceiver(screenOffReceiver, screenOffFilter)
                isScreenOffReceiverRegistered = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register screenOffReceiver", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_ADB -> {
                callerIsLandscape = intent.getBooleanExtra(EXTRA_CALLER_IS_LANDSCAPE, false)
                startAdbRecording()
            }
            ACTION_STOP_ADB  -> stopAdbRecording()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    // ── Start ────────────────────────────────────────────────────────────────

    private fun startAdbRecording() {
        if (isRecording) return
        userStopRequested = false

        try {
            val showPill = kotlinx.coroutines.runBlocking { settingsManager.showStatusBarPillFlow.first() }
            val keepAwake = kotlinx.coroutines.runBlocking { settingsManager.keepScreenAwakeFlow.first() }
            if (keepAwake) {
                acquireWakeLock()
            }
            recordingStartTimeMs = System.currentTimeMillis()
            val notification = buildNotification(getString(R.string.tile_stealth_recording), showPill, recordingStartTimeMs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, 0)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isRecording = true
            _isRecordingFlow.value = true
            notifyTileService()
            showToast(getString(R.string.toast_recording_started))

            val notifsEnabled = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
            if (!notifsEnabled) {
                showToast(getString(R.string.toast_stealth_notif_fallback))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            showToast(getString(R.string.toast_stealth_start_failed, e.message ?: ""))
            finish(success = false)
            return
        }

        DebugLogger.i(TAG, "===> ACTION_START_ADB received")
        DebugLogger.logOrientationSnapshot(this, TAG, "AdbRecordService - Session Start")

        lifecycleScope.launch(Dispatchers.IO) {
            var recordingSuccess = false
            val savedFiles = mutableListOf<File>()
            try {
                // 1. Verify ADB is paired
                val isPaired = settingsManager.adbPairedFlow.first()
                if (!isPaired) {
                    DebugLogger.w(TAG, "Adb recording aborted: not paired")
                    showToast(getString(R.string.home_toast_stealth_not_paired))
                    return@launch
                }

                // 2. Ensure ADB is connected (reusing port)
                val connected = AdbManager.reconnectIfNeeded(this@AdbRecordService)
                if (!connected) {
                    showToast(getString(R.string.toast_stealth_connect_failed))
                    return@launch
                }

                val mgr = AdbManager.manager
                if (mgr == null) {
                    showToast(getString(R.string.toast_stealth_not_ready))
                    return@launch
                }

                val adbAutoMerge = settingsManager.adbAutoMergeFlow.first()
                val safeStorageStop = settingsManager.safeStorageStopFlow.first()
                val safeStorageThresholdMb = settingsManager.safeStorageThresholdMbFlow.first()
                val thresholdBytes = safeStorageThresholdMb * 1024L * 1024L

                // Safe Storage Pre-check
                if (safeStorageStop) {
                    val availableBytes = getAvailableStorageBytes()
                    if (availableBytes < thresholdBytes) {
                        val availableMb = availableBytes / (1024 * 1024)
                        DebugLogger.w(TAG, "Adb start aborted: free storage ($availableMb MB) below threshold ($safeStorageThresholdMb MB)")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@AdbRecordService,
                                getString(R.string.toast_storage_critically_low_fmt, availableMb, safeStorageThresholdMb),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return@launch
                    }
                }

                // Output directory: /sdcard/Movies/ScreenX
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "ScreenX"
                )
                dir.mkdirs()

                val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                var partIndex = 1
                var consecutiveFailures = 0

                // Speaker audio capture in ADB mode (acoustic mic pickup with hardware DSP, zero MediaProjection prompt)
                val recordAudio = settingsManager.adbRecordAudioFlow.first()
                val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                    this@AdbRecordService,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (recordAudio && hasMicPermission) {
                    val gainPercent = settingsManager.adbAudioGainPercentFlow.first()
                    val clarity = settingsManager.adbAudioClarityFlow.first()
                    val gainMultiplier = (gainPercent / 100f).coerceIn(1.0f, 6.0f)

                    val audioOut = File(cacheDir, "temp_adb_audio_${System.currentTimeMillis()}.m4a")
                    if (audioOut.exists()) audioOut.delete()
                    tempAudioFile = audioOut

                    try {
                        val helper = AudioCaptureHelper(
                            context = this@AdbRecordService,
                            mediaProjection = null,
                            audioSource = "Mic",
                            outputFile = audioOut,
                            voicePriorityEnabled = false,
                            micGainMultiplier = gainMultiplier,
                            vocalClarityEnabled = clarity
                        )
                        helper.start()
                        audioCaptureHelper = helper
                        DebugLogger.i(TAG, "Adb speaker audio capture started: gain=$gainMultiplier, clarity=$clarity, out=${audioOut.name}")
                    } catch (e: Exception) {
                        DebugLogger.e(TAG, "Failed to start Adb speaker audio capture", e)
                        tempAudioFile = null
                        audioCaptureHelper = null
                    }
                }

                DebugLogger.i(TAG, "Adb recording started: autoMerge=$adbAutoMerge, recordAudio=$recordAudio, safeStorageStop=$safeStorageStop, thresholdMb=$safeStorageThresholdMb")

                while (!userStopRequested) {
                    // Safe storage check before recording each segment
                    if (safeStorageStop) {
                        val availableBytes = getAvailableStorageBytes()
                        if (availableBytes < thresholdBytes) {
                            val availableMb = availableBytes / (1024 * 1024)
                            DebugLogger.w(TAG, "Adb recording stopping: free storage ($availableMb MB) below safe threshold ($safeStorageThresholdMb MB)")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    this@AdbRecordService,
                                    getString(R.string.toast_storage_critically_low_fmt, availableMb, safeStorageThresholdMb),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            break
                        }
                    }

                    val outFile = if (adbAutoMerge) {
                        File(dir, "ADB_${dateStr}_part$partIndex.mp4")
                    } else {
                        File(dir, "ADB_${dateStr}.mp4")
                    }
                    outputPath = outFile.absolutePath

                    // Calculate display metrics for --size before each part so rotation between segments is captured
                    val wm = getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
                    val metrics = android.util.DisplayMetrics()
                    if (wm != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val bounds = wm.currentWindowMetrics.bounds
                            metrics.widthPixels = bounds.width()
                            metrics.heightPixels = bounds.height()
                        } else {
                            @Suppress("DEPRECATION")
                            wm.defaultDisplay.getRealMetrics(metrics)
                        }
                    }

                    val dm = getSystemService(Context.DISPLAY_SERVICE) as? android.hardware.display.DisplayManager
                    val display = dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
                        ?: wm?.defaultDisplay
                    val rotation = display?.rotation ?: android.view.Surface.ROTATION_0

                    val isDisplayLandscape = rotation == android.view.Surface.ROTATION_90 || rotation == android.view.Surface.ROTATION_270
                    val isConfigLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    val isWindowContextLandscape = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowContext != null) {
                        try {
                            val bounds = windowContext!!.getSystemService(android.view.WindowManager::class.java).currentWindowMetrics.bounds
                            bounds.width() > bounds.height() || windowContext!!.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        } catch (_: Exception) { false }
                    } else false
                    val isMetricsLandscape = metrics.widthPixels > metrics.heightPixels

                    val isLandscape = isDisplayLandscape || isConfigLandscape || isWindowContextLandscape || isSensorLandscape || isMetricsLandscape || callerIsLandscape

                    val rawW = metrics.widthPixels.coerceAtLeast(720)
                    val rawH = metrics.heightPixels.coerceAtLeast(1280)

                    // Explicit constraint mapping for custom ROMs (InfinityX / POCO X3 Pro safeguard)
                    val w = if (isLandscape) maxOf(rawW, rawH) else minOf(rawW, rawH)
                    val h = if (isLandscape) minOf(rawW, rawH) else maxOf(rawW, rawH)

                    val width = w and 1.inv()
                    val height = h and 1.inv()
                    val sizeParam = if (width > 0 && height > 0) " --size ${width}x${height}" else ""

                    val cmd = "screenrecord --time-limit $TIME_LIMIT_SECS$sizeParam ${outFile.absolutePath}"
                    Log.i(TAG, "Running: $cmd")
                    DebugLogger.i(TAG, "Adb Part $partIndex: metrics=${metrics.widthPixels}x${metrics.heightPixels}, rotation=$rotation, isLandscape=$isLandscape, chosen=${width}x${height}, cmd='$cmd'")

                    val partStartTime = System.currentTimeMillis()
                    val outputLog = java.lang.StringBuilder()

                    try {
                        mgr.openStream("exec:$cmd").use { stream ->
                            val buf = ByteArray(1024)
                            val inp = stream.openInputStream()
                            var bytesRead: Int
                            while (inp.read(buf).also { bytesRead = it } != -1) {
                                val chunk = String(buf, 0, bytesRead)
                                outputLog.append(chunk)
                                Log.d(TAG, "screenrecord output: $chunk")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "screenrecord stream exception: ${e.message}")
                    }

                    val partDurationMs = System.currentTimeMillis() - partStartTime
                    Log.i(TAG, "screenrecord part $partIndex exited after ${partDurationMs}ms. Output: $outputLog")
                    DebugLogger.i(TAG, "Adb Part $partIndex exited after ${partDurationMs}ms, fileExists=${outFile.exists()}, size=${outFile.length()} bytes, output='$outputLog'")

                    if (outFile.exists() && outFile.length() > 0) {
                        savedFiles.add(outFile)
                        recordingSuccess = true
                        consecutiveFailures = 0
                        partIndex++
                    } else {
                        consecutiveFailures++
                        Log.w(TAG, "Part $partIndex ended without saving valid video. Failures: $consecutiveFailures")
                        if (consecutiveFailures >= 3) {
                            Log.e(TAG, "Multiple consecutive screenrecord failures, stopping session.")
                            break
                        }
                    }

                    // If user requested stop, break immediately
                    if (userStopRequested) {
                        break
                    }

                    // If continuous recording / auto-merge is disabled, stop after single 3-min part
                    if (!adbAutoMerge) {
                        break
                    }

                    // Only backoff slightly if screenrecord died abnormally fast (< 1000ms) to prevent busy loops
                    if (partDurationMs < 1000L) {
                        kotlinx.coroutines.delay(300)
                    }
                }

                // Stop audio recording if active to flush AAC stream before merging
                try {
                    audioCaptureHelper?.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping audioCaptureHelper: ${e.message}")
                }
                audioCaptureHelper = null

                val currentTempAudio = tempAudioFile
                val hasValidAudio = currentTempAudio != null && currentTempAudio.exists() && currentTempAudio.length() > 512L

                // Process and save / auto-merge recorded files
                if (savedFiles.isNotEmpty()) {
                    if (savedFiles.size == 1) {
                        val singleFile = savedFiles[0]
                        val targetFile = File(dir, "ADB_${dateStr}.mp4")

                        // Show saving / merging notification
                        updateNotificationSafely(
                            buildSavingNotification(
                                title = getString(R.string.finalizing_recording),
                                content = if (hasValidAudio) getString(R.string.merging_audio_gallery) else getString(R.string.notif_saving_progress)
                            )
                        )

                        val finalSuccess = if (hasValidAudio) {
                            DebugLogger.i(TAG, "Muxing single ADB video with speaker audio...")
                            VideoMerger.mergeVideoAndAudio(singleFile, currentTempAudio!!, targetFile)
                        } else false

                        if (finalSuccess && targetFile.exists() && targetFile.length() > 0) {
                            try { singleFile.delete() } catch (_: Exception) {}
                            try { currentTempAudio?.delete() } catch (_: Exception) {}
                            MediaScannerConnection.scanFile(
                                this@AdbRecordService,
                                arrayOf(targetFile.absolutePath),
                                arrayOf("video/mp4"),
                                null
                            )
                            showToast(getString(R.string.toast_recording_stopped))
                            notifyRecordingSaved()
                        } else {
                            val finalFile = if (singleFile.absolutePath != targetFile.absolutePath) {
                                if (singleFile.renameTo(targetFile)) targetFile else singleFile
                            } else {
                                singleFile
                            }
                            try { currentTempAudio?.delete() } catch (_: Exception) {}

                            MediaScannerConnection.scanFile(
                                this@AdbRecordService,
                                arrayOf(finalFile.absolutePath),
                                arrayOf("video/mp4"),
                                null
                            )
                            showToast(getString(R.string.toast_recording_stopped))
                            notifyRecordingSaved()
                        }
                    } else {
                        // Multiple segments recorded: losslessly merge into a single continuous video
                        val targetFile = File(dir, "ADB_${dateStr}.mp4")

                        if (hasValidAudio) {
                            DebugLogger.i(TAG, "Auto-merging ${savedFiles.size} ADB video segments + speaker audio...")
                            updateNotificationSafely(
                                buildSavingNotification(
                                    title = getString(R.string.finalizing_recording),
                                    content = getString(R.string.merging_audio_gallery)
                                )
                            )

                            val tempVideoConcat = File(cacheDir, "temp_concat_${System.currentTimeMillis()}.mp4")
                            val videoConcatSuccess = VideoMerger.mergeSegments(savedFiles, tempVideoConcat)

                            val finalSuccess = if (videoConcatSuccess && tempVideoConcat.exists() && tempVideoConcat.length() > 0) {
                                VideoMerger.mergeVideoAndAudio(tempVideoConcat, currentTempAudio!!, targetFile)
                            } else false

                            if (finalSuccess && targetFile.exists() && targetFile.length() > 0) {
                                DebugLogger.i(TAG, "ADB video + speaker audio merged successfully: ${targetFile.length()} bytes")
                                savedFiles.forEach { part -> try { part.delete() } catch (_: Exception) {} }
                                try { tempVideoConcat.delete() } catch (_: Exception) {}
                                try { currentTempAudio.delete() } catch (_: Exception) {}

                                MediaScannerConnection.scanFile(
                                    this@AdbRecordService,
                                    arrayOf(targetFile.absolutePath),
                                    arrayOf("video/mp4"),
                                    null
                                )
                                withContext(Dispatchers.Main) {
                                    showToast(getString(R.string.toast_adb_merged_success))
                                }
                                notifyRecordingSaved()
                            } else {
                                DebugLogger.w(TAG, "Audio muxing failed; falling back to video-only merge.")
                                try { tempVideoConcat.delete() } catch (_: Exception) {}
                                try { currentTempAudio.delete() } catch (_: Exception) {}

                                val videoOnlySuccess = VideoMerger.mergeSegments(savedFiles, targetFile)
                                if (videoOnlySuccess && targetFile.exists() && targetFile.length() > 0) {
                                    savedFiles.forEach { part -> try { part.delete() } catch (_: Exception) {} }
                                    MediaScannerConnection.scanFile(
                                        this@AdbRecordService,
                                        arrayOf(targetFile.absolutePath),
                                        arrayOf("video/mp4"),
                                        null
                                    )
                                    withContext(Dispatchers.Main) {
                                        showToast(getString(R.string.toast_adb_merged_success))
                                    }
                                    notifyRecordingSaved()
                                } else {
                                    val paths = savedFiles.map { it.absolutePath }.toTypedArray()
                                    val mimeTypes = Array(paths.size) { "video/mp4" }
                                    MediaScannerConnection.scanFile(this@AdbRecordService, paths, mimeTypes, null)
                                    withContext(Dispatchers.Main) {
                                        showToast(getString(R.string.toast_adb_merge_fallback))
                                    }
                                    notifyRecordingSaved()
                                }
                            }
                        } else {
                            DebugLogger.i(TAG, "Auto-merging ${savedFiles.size} ADB recording segments (video only)...")
                            withContext(Dispatchers.Main) {
                                showToast(getString(R.string.toast_adb_merging))
                            }

                            // Show merging notification with initial progress
                            updateNotificationSafely(
                                buildSavingNotification(
                                    title = getString(R.string.finalizing_recording),
                                    content = getString(R.string.notif_merging_progress_fmt, savedFiles.size),
                                    progress = 0
                                )
                            )

                            val mergeSucceeded = VideoMerger.mergeSegments(savedFiles, targetFile) { progress ->
                                val percent = (progress * 100).toInt().coerceIn(0, 100)
                                updateNotificationSafely(
                                    buildSavingNotification(
                                        title = getString(R.string.finalizing_recording),
                                        content = getString(R.string.notif_merging_progress_fmt, savedFiles.size) + " ($percent%)",
                                        progress = percent
                                    )
                                )
                            }

                            if (mergeSucceeded && targetFile.exists() && targetFile.length() > 0) {
                                DebugLogger.i(TAG, "ADB segments merged successfully: ${targetFile.length()} bytes")
                                savedFiles.forEach { part ->
                                    try { part.delete() } catch (_: Exception) {}
                                }

                                updateNotificationSafely(
                                    buildSavingNotification(
                                        title = getString(R.string.finalizing_recording),
                                        content = getString(R.string.notif_saving_progress)
                                    )
                                )

                                MediaScannerConnection.scanFile(
                                    this@AdbRecordService,
                                    arrayOf(targetFile.absolutePath),
                                    arrayOf("video/mp4"),
                                    null
                                )
                                withContext(Dispatchers.Main) {
                                    showToast(getString(R.string.toast_adb_merged_success))
                                }
                                notifyRecordingSaved()
                            } else {
                                DebugLogger.w(TAG, "Auto-merge failed; preserving individual parts.")
                                val paths = savedFiles.map { it.absolutePath }.toTypedArray()
                                val mimeTypes = Array(paths.size) { "video/mp4" }
                                MediaScannerConnection.scanFile(
                                    this@AdbRecordService,
                                    paths,
                                    mimeTypes,
                                    null
                                )
                                withContext(Dispatchers.Main) {
                                    showToast(getString(R.string.toast_adb_merge_fallback))
                                }
                                notifyRecordingSaved()
                            }
                        }
                    }
                } else {
                    showToast(getString(R.string.toast_stealth_no_video))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Stealth recording error", e)
                showToast(getString(R.string.toast_stealth_error_fmt, e.message ?: "Unknown error"))
            } finally {
                finish(success = recordingSuccess)
            }
        }
    }

    // ── Stop ─────────────────────────────────────────────────────────────────

    private fun stopAdbRecording() {
        if (!isRecording) return
        userStopRequested = true
        try {
            audioCaptureHelper?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Could not stop audioCaptureHelper: ${e.message}")
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Send SIGINT so screenrecord finalises the MP4 properly
                AdbManager.manager?.openStream("exec:pkill -2 screenrecord")?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Could not send SIGINT to screenrecord: ${e.message}")
            }
            // The running shell() call in startAdbRecording will then return and finish naturally
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun getAvailableStorageBytes(): Long {
        var minFreeBytes = Long.MAX_VALUE
        try {
            val cacheStat = StatFs(cacheDir.absolutePath)
            val cacheFree = cacheStat.availableBlocksLong * cacheStat.blockSizeLong
            minFreeBytes = minOf(minFreeBytes, cacheFree)
        } catch (_: Exception) {}

        try {
            val dataStat = StatFs(Environment.getDataDirectory().absolutePath)
            val dataFree = dataStat.availableBlocksLong * dataStat.blockSizeLong
            minFreeBytes = minOf(minFreeBytes, dataFree)
        } catch (_: Exception) {}

        try {
            val extDir = getExternalFilesDir(null) ?: Environment.getExternalStorageDirectory()
            val extStat = StatFs(extDir.absolutePath)
            val extFree = extStat.availableBlocksLong * extStat.blockSizeLong
            minFreeBytes = minOf(minFreeBytes, extFree)
        } catch (_: Exception) {}

        return if (minFreeBytes == Long.MAX_VALUE) Long.MAX_VALUE else minFreeBytes
    }

    private fun finish(success: Boolean) {
        DebugLogger.i(TAG, "Adb finish() called: success=$success, userStopRequested=$userStopRequested")
        DebugLogger.logOrientationSnapshot(this, TAG, "AdbRecordService - Finish")
        releaseWakeLock()
        isRecording = false
        _isRecordingFlow.value = false
        notifyTileService()
        // BUG-19 FIX: success param was previously ignored; now used for logging
        if (!success) {
            Log.w(TAG, "Stealth recording session ended without saving valid video.")
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyTileService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                android.service.quicksettings.TileService.requestListeningState(
                    this,
                    ComponentName(this, ScreenXTileService::class.java)
                )
            } catch (_: Exception) {}
        }
    }

    private fun showToast(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    /** Broadcast so MainActivity refreshes the gallery */
    private fun notifyRecordingSaved() {
        // BUG-5 FIX: Use setPackage() so the broadcast is restricted to this app.
        // Without it, the RECEIVER_NOT_EXPORTED receiver in MainActivity may not receive it on API 33+.
        val intent = Intent("com.gxdevs.screenx.action.RECORDING_SAVED").apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stealth Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Status of Stealth screen recording" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String, showPill: Boolean = true, startTimeMs: Long = System.currentTimeMillis()): Notification {
        val mainIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 20,
            Intent(this, AdbRecordService::class.java).apply { action = ACTION_STOP_ADB },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title_stealth))
            .setContentText(status)
            .setSmallIcon(com.gxdevs.screenx.R.drawable.ic_notification)
            .setContentIntent(mainIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notif_action_stop), stopIntent)

        if (showPill) {
            builder.setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            builder.setUsesChronometer(true)
            builder.setChronometerCountDown(false)
            builder.setWhen(startTimeMs)

            try {
                val method = builder.javaClass.getMethod("setRequestPromotedOngoing", Boolean::class.javaPrimitiveType)
                method.invoke(builder, true)
            } catch (_: Throwable) {}

            builder.extras.apply {
                putBoolean("android.requestPromotedOngoing", true)
                putBoolean("android.ongoingActivityNoti.style", true)
                putBoolean("miui.focus.notification", true)
                putBoolean("coloros.notification.capsule", true)
                putBoolean("oplus.notification.capsule", true)
            }
        }

        return builder.build()
    }

    private fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
        }
    }

    private fun buildSavingNotification(
        title: String,
        content: String,
        progress: Int = -1,
        maxProgress: Int = 100
    ): Notification {
        val mainIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(mainIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progress in 0..maxProgress) {
            builder.setProgress(maxProgress, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun updateNotificationSafely(notification: Notification) {
        try {
            if (canPostNotifications()) {
                val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notifManager?.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification: ${e.message}")
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        if (isScreenOffReceiverRegistered) {
            try {
                unregisterReceiver(screenOffReceiver)
            } catch (_: Exception) {}
            isScreenOffReceiverRegistered = false
        }
        try {
            orientationListener?.disable()
            orientationListener = null
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
