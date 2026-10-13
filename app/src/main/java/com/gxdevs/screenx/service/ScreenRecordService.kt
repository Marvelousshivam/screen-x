package com.gxdevs.screenx.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.StatFs
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gxdevs.screenx.MainActivity
import com.gxdevs.screenx.data.SettingsManager
import com.gxdevs.screenx.utils.VideoHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer

@Suppress("DEPRECATION")
class ScreenRecordService : LifecycleService() {

    companion object {
        private const val TAG = "ScreenRecordService"
        const val CHANNEL_ID = "ScreenX_Recording_Channel"
        const val NOTIFICATION_ID = 888

        // Intent Actions
        const val ACTION_START = "com.gxdevs.screenx.action.START"
        const val ACTION_PAUSE = "com.gxdevs.screenx.action.PAUSE"
        const val ACTION_RESUME = "com.gxdevs.screenx.action.RESUME"
        const val ACTION_STOP = "com.gxdevs.screenx.action.STOP"
        const val ACTION_EXIT = "com.gxdevs.screenx.action.EXIT"
        
        // Screenshot Action
        const val ACTION_SCREENSHOT = "com.gxdevs.screenx.action.SCREENSHOT"
        const val ACTION_START_FLOATING_ONLY = "com.gxdevs.screenx.action.START_FLOATING_ONLY"
        const val ACTION_TOGGLE_MIC = "com.gxdevs.screenx.action.TOGGLE_MIC"

        // Intent Extras
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private val _isRecordingFlow = MutableStateFlow(false)
        val isRecordingFlow = _isRecordingFlow.asStateFlow()

        var isRecording = false
            private set(value) {
                field = value
                _isRecordingFlow.value = value
            }

        private val _isSavingFlow = MutableStateFlow(false)
        val isSavingFlow = _isSavingFlow.asStateFlow()

        var isSaving = false
            private set(value) {
                field = value
                _isSavingFlow.value = value
            }
        var isPaused = false
            private set
        var isMicMuted = false
            private set
        var recordingStartTimeMs: Long = 0
            private set
        var accumulatedDurationMs: Long = 0
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var settingsManager: SettingsManager

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioCaptureHelper: AudioCaptureHelper? = null

    private var tempVideoFile: File? = null
    private var tempAudioFile: File? = null
    private var startTimeMs: Long = 0
    private var recordingDurationMs: Long = 0
    private var targetResolution = "1080p"

    private var floatingControlOverlay: FloatingControlOverlay? = null
    private var brushDrawingOverlay: BrushDrawingOverlay? = null
    private var countdownOverlay: CountdownOverlay? = null
    private var storageMonitorJob: Job? = null

    private fun isHeadsetConnected(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.any { device ->
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_USB_DEVICE
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isWiredHeadsetOn || audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
        }
    }

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

        return if (minFreeBytes == Long.MAX_VALUE) 1024L * 1024L * 1024L else minFreeBytes
    }

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lastUpdate: Long = 0
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val curTime = System.currentTimeMillis()
                if ((curTime - lastUpdate) > 100) {
                    val diffTime = curTime - lastUpdate
                    lastUpdate = curTime

                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]

                    val speed = Math.abs(x + y + z - lastX - lastY - lastZ) / diffTime * 10000

                    if (speed > 800) {
                        lifecycleScope.launch {
                            val shakeToStop = settingsManager.shakeToStopFlow.first()
                            if (shakeToStop && isRecording) {
                                Toast.makeText(this@ScreenRecordService, "Shake detected! Stopping...", Toast.LENGTH_SHORT).show()
                                stopRecording()
                            }
                        }
                    }
                    lastX = x
                    lastY = y
                    lastZ = z
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                lifecycleScope.launch {
                    val stopOnScreenOff = settingsManager.stopOnScreenOffFlow.first()
                    if (stopOnScreenOff) {
                        if (isRecording) {
                            Log.i(TAG, "Screen off detected — stopping recording")
                            stopRecording(reason = "Screen turned off")
                        }
                        if (AdbRecordService.isRecording) {
                            Log.i(TAG, "Screen off detected — stopping stealth recording")
                            try {
                                val stopIntent = Intent(this@ScreenRecordService, AdbRecordService::class.java).apply {
                                    action = AdbRecordService.ACTION_STOP_ADB
                                }
                                startService(stopIntent)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to send STOP to AdbRecordService", e)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        settingsManager = SettingsManager(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createNotificationChannel()

        val screenOffFilter = android.content.IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, screenOffFilter)

        lifecycleScope.launch {
            AdbRecordService.isRecordingFlow.collect {
                floatingControlOverlay?.updateState()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        val action = intent?.action
        when (action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (resultData != null) {
                    startRecordingSequence(resultCode, resultData)
                } else {
                    stopSelf()
                }
            }
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
            ACTION_SCREENSHOT -> takeScreenshot()
            ACTION_TOGGLE_MIC -> toggleMicMute()
            ACTION_EXIT -> exitService()
            ACTION_START_FLOATING_ONLY -> {
                val notification = createNotification("Floating controls active")
                // On Android 15+, calling startForeground() again with a different type
                // while already foreground in mediaProjection mode causes a crash.
                // Guard: only call startForeground if not already running as foreground.
                if (!isForeground) {
                    startForegroundSpecialUse(notification)
                } else {
                    updateNotification("Floating controls active")
                }
                showFloatingControls()
            }
            null -> {
                lifecycleScope.launch {
                    val showFloating = settingsManager.showFloatingFlow.first()
                    val mode = settingsManager.floatingShowModeFlow.first()
                    if (showFloating && mode.startsWith("All the time")) {
                        val notification = createNotification("Floating controls active")
                        if (!isForeground) {
                            startForegroundSpecialUse(notification)
                        } else {
                            updateNotification("Floating controls active")
                        }
                        showFloatingControls()
                    } else if (!isRecording) {
                        exitService()
                    }
                }
            }
        }
        
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "ScreenX Recording Status"
            val descriptionText = "Displays status and controls for active screen recording"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingMainIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Actions
        val stopIntent = Intent(this, ScreenRecordService::class.java).apply { this.action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(this, 10, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val pauseIntent = Intent(this, ScreenRecordService::class.java).apply { this.action = ACTION_PAUSE }
        val pendingPause = PendingIntent.getService(this, 11, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val resumeIntent = Intent(this, ScreenRecordService::class.java).apply { this.action = ACTION_RESUME }
        val pendingResume = PendingIntent.getService(this, 12, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val exitIntent = Intent(this, ScreenRecordService::class.java).apply { this.action = ACTION_EXIT }
        val pendingExit = PendingIntent.getService(this, 13, exitIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ScreenX Recorder")
            .setContentText(if (status == "Floating controls active") "Floating ball is active • Tap to open" else status)
            .setSmallIcon(com.gxdevs.screenx.R.drawable.ic_notification)
            .setContentIntent(pendingMainIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (status == "Recording") {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pendingPause)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
        } else if (status == "Paused") {
            builder.addAction(android.R.drawable.ic_media_play, "Resume", pendingResume)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
        }
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel, 
            if (status == "Floating controls active") "Hide Ball" else "Exit", 
            pendingExit
        )

        return builder.build()
    }

    /** True once startForeground() has been successfully called at least once. */
    private var isForeground = false

    private fun startForegroundSpecialUse(notification: Notification) {
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
        isForeground = true
    }

    private fun updateNotification(status: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(status))
    }

    private fun startRecordingSequence(resultCode: Int, resultData: Intent) {
        if (isSaving) {
            Log.w(TAG, "Previous recording is still saving. Waiting for save to finish before starting new recording...")
            lifecycleScope.launch {
                isSavingFlow.first { !it }
                startRecordingSequence(resultCode, resultData)
            }
            return
        }

        // Start Foreground with MEDIA_PROJECTION immediately for Android 14+ requirements.
        // Even if the service was already running as SPECIAL_USE for floating controls,
        // it must be updated to FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION before getMediaProjection().
        val initialNotification = createNotification("Starting countdown...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Combine SPECIAL_USE so floating controls overlay remains valid alongside media projection
                type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            try {
                startForeground(NOTIFICATION_ID, initialNotification, type)
            } catch (e: Exception) {
                Log.e(TAG, "Failed startForeground with type $type, retrying fallback", e)
                try {
                    val fallbackType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    } else {
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    }
                    startForeground(NOTIFICATION_ID, initialNotification, fallbackType)
                } catch (e2: Exception) {
                    Log.e(TAG, "Fallback startForeground failed", e2)
                    startForeground(NOTIFICATION_ID, initialNotification)
                }
            }
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }
        isForeground = true

        lifecycleScope.launch {
            val countdownSecs = settingsManager.countdownFlow.first()
            if (countdownSecs > 0) {
                // Show countdown overlay
                countdownOverlay = CountdownOverlay(this@ScreenRecordService)
                countdownOverlay?.show {
                    // When countdown finishes
                    startRecording(resultCode, resultData)
                    countdownOverlay?.dismiss()
                    countdownOverlay = null
                }
            } else {
                startRecording(resultCode, resultData)
            }
        }
    }

    private fun startRecording(resultCode: Int, resultData: Intent) {
        // Load settings and initiate hardware recording inside lifecycleScope
        lifecycleScope.launch {
            try {
                val fps = settingsManager.fpsFlow.first()
                val bitrate = settingsManager.bitrateFlow.first()
                val audioSource = settingsManager.audioSourceFlow.first()
                targetResolution = settingsManager.resolutionFlow.first()
                val showFloating = settingsManager.showFloatingFlow.first()
                val orientationSetting = settingsManager.orientationFlow.first()
                val safeStorageStop = settingsManager.safeStorageStopFlow.first()
                val safeStorageThresholdMb = settingsManager.safeStorageThresholdMbFlow.first()
                val thresholdBytes = safeStorageThresholdMb * 1024L * 1024L

                // Safe Storage Pre-check: Ensure enough free storage before starting
                if (safeStorageStop) {
                    val availableBytes = getAvailableStorageBytes()
                    if (availableBytes < thresholdBytes) {
                        val availableMb = availableBytes / (1024 * 1024)
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                this@ScreenRecordService,
                                "Storage is critically low ($availableMb MB free)! At least $safeStorageThresholdMb MB required to record safely.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        isRecording = false
                        stopSelf()
                        return@launch
                    }
                }

                val sessionTime = System.currentTimeMillis()
                setupMediaRecorder(fps, bitrate, audioSource, orientationSetting, sessionTime)

                // Get projection
                mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
                
                // Set callbacks (with Android 14+ / Android 16-17 resize & visibility handlers)
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.i(TAG, "MediaProjection stopped by system (status bar chip / screen lock / permission revoke)")
                        stopRecording(reason = "Screen recording ended by system")
                    }
                    override fun onCapturedContentResize(width: Int, height: Int) {
                        Log.d(TAG, "Captured content resized: ${width}x${height}")
                    }
                    override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
                        Log.d(TAG, "Captured content visibility changed: $isVisible")
                    }
                }, Handler(Looper.getMainLooper()))

                // Initialize audio capture helper for active audio modes (Mic, System, MicSystem)
                if (audioSource == "Mic" || audioSource == "System" || audioSource == "MicSystem") {
                    tempAudioFile = File(cacheDir, "temp_audio_$sessionTime.m4a")
                    if (tempAudioFile?.exists() == true) {
                        tempAudioFile?.delete()
                    }
                    val voicePriority = settingsManager.voicePriorityFlow.first()
                    val micVolPercent = settingsManager.micVolumeFlow.first()
                    val intVolPercent = settingsManager.internalAudioVolumeFlow.first()
                    val vocalClarity = settingsManager.vocalClarityFlow.first()

                    val micGain = (micVolPercent.toFloat() / 100f).coerceIn(0.1f, 6.0f)
                    val intRatio = (intVolPercent.toFloat() / 100f).coerceIn(0.01f, 1.0f)

                    audioCaptureHelper = AudioCaptureHelper(
                        context = this@ScreenRecordService,
                        mediaProjection = mediaProjection,
                        audioSource = audioSource,
                        outputFile = tempAudioFile!!,
                        voicePriorityEnabled = voicePriority,
                        micGainMultiplier = micGain,
                        internalAudioVolumeRatio = intRatio,
                        vocalClarityEnabled = vocalClarity
                    )
                } else {
                    tempAudioFile = null
                    audioCaptureHelper = null
                }

                // Metrics: Use maximumWindowMetrics on API 30+ to capture full screen even in multi-window/split-screen
                val screenWidth: Int
                val screenHeight: Int
                val screenDensityDpi: Int
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val maxBounds = try { windowManager.maximumWindowMetrics.bounds } catch (_: Exception) { null }
                    val currBounds = windowManager.currentWindowMetrics.bounds
                    val bounds = if (maxBounds != null && (maxBounds.width() > currBounds.width() || maxBounds.height() > currBounds.height())) {
                        maxBounds
                    } else {
                        currBounds
                    }
                    screenWidth = bounds.width()
                    screenHeight = bounds.height()
                    screenDensityDpi = resources.configuration.densityDpi
                } else {
                    val metrics = DisplayMetrics()
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay.getRealMetrics(metrics)
                    screenWidth = metrics.widthPixels
                    screenHeight = metrics.heightPixels
                    screenDensityDpi = metrics.densityDpi
                }
                
                val dimensions = getTargetDimensions(targetResolution, screenWidth, screenHeight, orientationSetting)
                val recordWidth = dimensions.first
                val recordHeight = dimensions.second

                // Virtual Display: VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR ensures continuous hardware compositor mirroring,
                // fixing stuttering and dropped frames on Realme UI (Realme 5 Pro / Q) and MIUI (Redmi Note 8 Pro).
                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "ScreenXDisplay",
                    recordWidth,
                    recordHeight,
                    screenDensityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mediaRecorder?.surface,
                    null,
                    null
                )

                // Start hardware video recorder and audio capture synchronously to prevent A/V timestamp offset
                mediaRecorder?.start()
                audioCaptureHelper?.start()
                isRecording = true
                isPaused = false
                isMicMuted = (audioSource == "System")
                startTimeMs = System.currentTimeMillis()
                recordingStartTimeMs = startTimeMs
                recordingDurationMs = 0L
                accumulatedDurationMs = 0L
                updateNotification("Recording")

                // Launch Safe Storage Auto-Stop monitor (polling every 1000ms for safety)
                if (safeStorageStop) {
                    storageMonitorJob?.cancel()
                    storageMonitorJob = lifecycleScope.launch {
                        while (isActive && isRecording) {
                            delay(1000)
                            if (!isRecording) break
                            val freeBytes = getAvailableStorageBytes()
                            if (freeBytes < thresholdBytes) {
                                val freeMb = freeBytes / (1024 * 1024)
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(
                                        this@ScreenRecordService,
                                        "Storage is almost full ($freeMb MB left)! Recording auto-stopped & finalized safely.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                stopRecording(reason = "Storage full safeguard: Recording saved safely!")
                                break
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ScreenRecordService, "Recording started", Toast.LENGTH_SHORT).show()
                }

                // Show floating controls if set
                if (showFloating) {
                    showFloatingControls()
                    val hideDuringRecord = settingsManager.hideDuringRecordFlow.first()
                    if (hideDuringRecord) {
                        floatingControlOverlay?.hideView()
                    }
                }

                // Register shake-to-stop detector
                val shakeToStop = settingsManager.shakeToStopFlow.first()
                if (shakeToStop) {
                    sensorManager?.registerListener(
                        sensorListener,
                        accelerometer,
                        SensorManager.SENSOR_DELAY_NORMAL
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this@ScreenRecordService, "Failed to start recording: ${e.message}", Toast.LENGTH_LONG).show()
                }
                isRecording = false
                stopSelf()
            }
        }
    }

    private fun setupMediaRecorder(fps: Int, bitrate: Int, audioSource: String, orientationSetting: String, sessionTime: Long) {
        // Create temp output file
        tempVideoFile = File(cacheDir, "temp_video_$sessionTime.mp4")
        if (tempVideoFile?.exists() == true) {
            tempVideoFile?.delete()
        }

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }

        mediaRecorder?.apply {
            // Video stream is captured directly from VirtualDisplay surface.
            // Audio is captured via AudioCaptureHelper (Mic, System, MicSystem) and remuxed seamlessly.
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            
            // Set paths
            setOutputFile(tempVideoFile?.absolutePath)

            // Setup dimensions (use maximumWindowMetrics on API 30+ to prevent window-bound clipping)
            val setupWidth: Int
            val setupHeight: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val maxBounds = try { windowManager.maximumWindowMetrics.bounds } catch (_: Exception) { null }
                val currBounds = windowManager.currentWindowMetrics.bounds
                val bounds = if (maxBounds != null && (maxBounds.width() > currBounds.width() || maxBounds.height() > currBounds.height())) {
                    maxBounds
                } else {
                    currBounds
                }
                setupWidth = bounds.width()
                setupHeight = bounds.height()
            } else {
                val m = DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(m)
                setupWidth = m.widthPixels
                setupHeight = m.heightPixels
            }
            val dimensions = getTargetDimensions(targetResolution, setupWidth, setupHeight, orientationSetting)
            setVideoSize(dimensions.first, dimensions.second)

            val safeFps = com.gxdevs.screenx.utils.DeviceCapabilitiesHelper.getSafeFpsForResolution(dimensions.first, dimensions.second, fps)
            val safeBitrate = com.gxdevs.screenx.utils.DeviceCapabilitiesHelper.getSafeBitrate(bitrate)

            // Setup Encoders
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoEncodingBitRate(safeBitrate)
            setVideoFrameRate(safeFps)

            // Optimize AVC Profile & Level: AVCProfileBaseline guarantees no B-frames and strictly monotonic PTS
            try {
                val profileLevel = com.gxdevs.screenx.utils.DeviceCapabilitiesHelper.getOptimalAvcProfileLevel()
                if (profileLevel != null) {
                    setVideoEncodingProfileLevel(profileLevel.first, profileLevel.second)
                    Log.d(TAG, "Applied AVC profile ${profileLevel.first}, level ${profileLevel.second}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not set video encoding profile/level: ${e.message}")
            }

            setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                    stopRecording("Maximum file size reached: Recording saved safely.")
                } else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stopRecording("Maximum duration reached: Recording saved safely.")
                }
            }
            setOnErrorListener { _, what, _ ->
                stopRecording("Recording error encountered ($what): Saving recording...")
            }
            
            prepare()
        }
    }

    private fun getTargetDimensions(resolutionSetting: String, screenWidth: Int, screenHeight: Int, orientationSetting: String): Pair<Int, Int> {
        if (resolutionSetting == "Original") {
            return com.gxdevs.screenx.utils.DeviceCapabilitiesHelper.getAlignedDimensions(screenWidth, screenHeight)
        }

        val isLandscape = when (orientationSetting) {
            "Portrait" -> false
            "Landscape" -> true
            else -> screenWidth > screenHeight
        }
        val longSide = when (resolutionSetting) {
            "4K" -> 3840
            "1440p" -> 2560
            "1080p" -> 1920
            "720p" -> 1280
            "480p" -> 854
            else -> 1280
        }

        val targetW: Int
        val targetH: Int
        val minDim = minOf(screenWidth, screenHeight).toFloat()
        val maxDim = maxOf(screenWidth, screenHeight).toFloat()
        val aspectRatio = if (maxDim > 0) minDim / maxDim else 0.5625f

        if (isLandscape) {
            targetW = longSide
            targetH = (longSide * aspectRatio).toInt()
        } else {
            targetH = longSide
            targetW = (longSide * aspectRatio).toInt()
        }

        return com.gxdevs.screenx.utils.DeviceCapabilitiesHelper.getAlignedDimensions(targetW, targetH)
    }

    private fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording && !isPaused) {
            try {
                mediaRecorder?.pause()
                audioCaptureHelper?.pause()
                isPaused = true
                val segment = System.currentTimeMillis() - startTimeMs
                recordingDurationMs += segment
                accumulatedDurationMs = recordingDurationMs
                updateNotification("Paused")
                floatingControlOverlay?.updateState()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording && isPaused) {
            try {
                mediaRecorder?.resume()
                audioCaptureHelper?.resume()
                isPaused = false
                startTimeMs = System.currentTimeMillis()
                recordingStartTimeMs = startTimeMs
                updateNotification("Recording")
                floatingControlOverlay?.updateState()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleMicMute(): Boolean {
        if (audioCaptureHelper == null || audioCaptureHelper?.hasMic() != true) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "Microphone is not active in this recording mode", Toast.LENGTH_SHORT).show()
            }
            return isMicMuted
        }
        val newMute = !isMicMuted
        isMicMuted = newMute
        audioCaptureHelper?.setMicMuted(newMute)
        floatingControlOverlay?.updateState()
        val msg = if (newMute) "Microphone muted" else "Microphone unmuted"
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        return newMute
    }

    private fun stopRecording(reason: String? = null) {
        if (!isRecording) return
        val wasPaused = isPaused
        isRecording = false
        isPaused = false
        storageMonitorJob?.cancel()
        storageMonitorJob = null
        try {
            sensorManager?.unregisterListener(sensorListener)
        } catch (_: Exception) {}
        
        // Dismiss brush overlay if active
        dismissBrushOverlay()

        if (!wasPaused) {
            recordingDurationMs += System.currentTimeMillis() - startTimeMs
        }
        accumulatedDurationMs = recordingDurationMs

        // Immediately update floating controls on UI thread (do NOT dismiss if Always Show is on!)
        lifecycleScope.launch(Dispatchers.Main) {
            val showFloating = settingsManager.showFloatingFlow.first()
            val mode = settingsManager.floatingShowModeFlow.first()
            if (showFloating && mode.startsWith("All the time")) {
                updateNotification("Floating controls active")
                floatingControlOverlay?.updateState()
            } else {
                dismissFloatingControls()
            }
        }

        // Save references to active session objects before clearing them
        val recDuration = recordingDurationMs
        val targetRes = targetResolution
        val currentTempVideo = tempVideoFile
        val currentTempAudio = tempAudioFile
        val currentMediaRecorder = mediaRecorder
        val currentAudioHelper = audioCaptureHelper
        val currentVirtualDisplay = virtualDisplay
        val currentProjection = mediaProjection

        mediaRecorder = null
        audioCaptureHelper = null
        virtualDisplay = null
        mediaProjection = null
        tempVideoFile = null
        tempAudioFile = null


        isSaving = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // STEP 1: Detach VirtualDisplay surface first to halt incoming frames into MediaRecorder surface
                try {
                    currentVirtualDisplay?.surface = null
                } catch (e: Exception) {
                    Log.w(TAG, "Error clearing virtual display surface", e)
                }

                // STEP 2: Stop and flush AudioCaptureHelper first so AudioRecord cleanly unbinds from AudioFlinger
                try {
                    currentAudioHelper?.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping audio helper", e)
                }

                // STEP 3: Stop Video MediaRecorder cleanly without incoming frame buffer collisions
                try {
                    currentMediaRecorder?.apply {
                        stop()
                        release()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping media recorder", e)
                }

                // STEP 4: Release VirtualDisplay
                try {
                    currentVirtualDisplay?.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing virtual display", e)
                }

                // STEP 5: ONLY stop MediaProjection after all consumers (AudioRecord + VirtualDisplay) are released!
                // Stopping MediaProjection before AudioRecord causes AudioFlinger native session leaks on OEM HALs.
                try {
                    currentProjection?.stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping media projection", e)
                }

                // STEP 6: Remux video and audio files if both temp video and audio exist
                val fileToSave = if (currentTempAudio != null && currentTempAudio.exists() && currentTempAudio.length() > 0 &&
                    currentTempVideo != null && currentTempVideo.exists() && currentTempVideo.length() > 0) {
                    val mergedFile = File(cacheDir, "merged_${System.currentTimeMillis()}.mp4")
                    if (mergedFile.exists()) {
                        mergedFile.delete()
                    }
                    try {
                        mergeVideoAndAudio(currentTempVideo, currentTempAudio, mergedFile)
                        try { currentTempAudio.delete() } catch (_: Exception) {}
                        try { currentTempVideo.delete() } catch (_: Exception) {}
                        mergedFile
                    } catch (e: Exception) {
                        Log.e(TAG, "Remux failed, falling back to video only", e)
                        try { mergedFile.delete() } catch (_: Exception) {}
                        try { currentTempAudio.delete() } catch (_: Exception) {}
                        currentTempVideo // Fallback to video-only if remuxing fails
                    }
                } else {
                    currentTempVideo
                }

                // Save recorded file to MediaStore
                fileToSave?.let { tempFile ->
                    if (tempFile.exists() && tempFile.length() > 0) {
                        saveVideoToMediaStore(tempFile, targetRes, recDuration)
                        withContext(Dispatchers.Main) {
                            if (reason != null) {
                                Toast.makeText(this@ScreenRecordService, reason, Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@ScreenRecordService, "Recording stopped", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } finally {
                isSaving = false
                withContext(Dispatchers.Main) {
                    val showFloating = settingsManager.showFloatingFlow.first()
                    val mode = settingsManager.floatingShowModeFlow.first()
                    if (showFloating && mode.startsWith("All the time")) {
                        updateNotification("Floating controls active")
                        floatingControlOverlay?.updateState()
                    } else {
                        stopForeground(true)
                        isForeground = false
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun mergeVideoAndAudio(videoFile: File, audioFile: File, outputFile: File) {
        val extractorVideo = MediaExtractor()
        val extractorAudio = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false

        try {
            extractorVideo.setDataSource(videoFile.absolutePath)
            extractorAudio.setDataSource(audioFile.absolutePath)
            val m = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            
            // Select video track
            var videoTrackIndex = -1
            for (i in 0 until extractorVideo.trackCount) {
                val format = extractorVideo.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    extractorVideo.selectTrack(i)
                    videoTrackIndex = m.addTrack(format)
                    break
                }
            }
            
            // Select audio track
            var audioTrackIndex = -1
            for (i in 0 until extractorAudio.trackCount) {
                val format = extractorAudio.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    extractorAudio.selectTrack(i)
                    audioTrackIndex = m.addTrack(format)
                    break
                }
            }
            
            if (videoTrackIndex == -1) {
                throw java.io.IOException("No video track found in temp video file")
            }
            if (audioTrackIndex == -1) {
                throw java.io.IOException("No audio track found in temp audio file")
            }

            m.start()
            muxerStarted = true
            
            val bufferSize = 2 * 1024 * 1024
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            
            // Interleave video and audio tracks chronologically
            var videoDone = false
            var audioDone = false

            var lastVideoPts = -1L
            var lastAudioPts = -1L

            // Baseline PTS offset: normalize output to start at 0 while strictly preserving relative sync
            val firstVideoTime = if (extractorVideo.sampleTime >= 0) extractorVideo.sampleTime else 0L
            val firstAudioTime = if (extractorAudio.sampleTime >= 0) extractorAudio.sampleTime else 0L
            val basePtsOffset = minOf(firstVideoTime, firstAudioTime)

            while (!videoDone || !audioDone) {
                val videoTime = if (!videoDone) extractorVideo.sampleTime else Long.MAX_VALUE
                val audioTime = if (!audioDone) extractorAudio.sampleTime else Long.MAX_VALUE

                if (!videoDone && (audioDone || videoTime <= audioTime)) {
                    buffer.clear()
                    bufferInfo.offset = 0
                    bufferInfo.size = extractorVideo.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) {
                        videoDone = true
                    } else {
                        var pts = maxOf(0L, videoTime - basePtsOffset)
                        if (pts <= lastVideoPts) {
                            pts = lastVideoPts + 1000L
                        }
                        lastVideoPts = pts
                        bufferInfo.presentationTimeUs = pts
                        bufferInfo.flags = extractorVideo.sampleFlags
                        if (bufferInfo.size > 0) {
                            buffer.position(0)
                            buffer.limit(bufferInfo.size)
                            m.writeSampleData(videoTrackIndex, buffer, bufferInfo)
                        }
                        extractorVideo.advance()
                    }
                } else if (!audioDone) {
                    buffer.clear()
                    bufferInfo.offset = 0
                    bufferInfo.size = extractorAudio.readSampleData(buffer, 0)
                    if (bufferInfo.size < 0) {
                        audioDone = true
                    } else {
                        var pts = maxOf(0L, audioTime - basePtsOffset)
                        if (pts <= lastAudioPts) {
                            pts = lastAudioPts + 1000L
                        }
                        lastAudioPts = pts
                        bufferInfo.presentationTimeUs = pts
                        bufferInfo.flags = extractorAudio.sampleFlags
                        if (bufferInfo.size > 0) {
                            buffer.position(0)
                            buffer.limit(bufferInfo.size)
                            m.writeSampleData(audioTrackIndex, buffer, bufferInfo)
                        }
                        extractorAudio.advance()
                    }
                }
            }
            
            try {
                m.stop()
                muxerStarted = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } finally {
            // BUG-4 FIX: If m.stop() threw an exception, muxerStarted is still true here.
            // Calling release() on a started-but-not-stopped muxer corrupts the MP4 output.
            // Always attempt stop() before release() if the muxer was ever started.
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Exception) {}
            try {
                muxer?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try { extractorVideo.release() } catch (_: Exception) {}
            try { extractorAudio.release() } catch (_: Exception) {}
        }
    }

    private fun saveVideoToMediaStore(tempFile: File, resolution: String, durationMs: Long) {
        val fileName = "ScreenX_${System.currentTimeMillis()}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScreenX")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        }

        val resolver = contentResolver
        val collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val videoUri = resolver.insert(collectionUri, contentValues)

        if (videoUri != null) {
            try {
                resolver.openOutputStream(videoUri)?.use { outputStream ->
                    tempFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                val updateValues = ContentValues().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    put(MediaStore.Video.Media.DURATION, durationMs)
                    put(MediaStore.Video.Media.RESOLUTION, resolution)
                    put(MediaStore.Video.Media.SIZE, tempFile.length())
                }
                resolver.update(videoUri, updateValues, null, null)
                sendBroadcast(Intent("com.gxdevs.screenx.action.RECORDING_SAVED").apply {
                    setPackage(packageName)
                })
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }

    private fun takeScreenshot() {
        val projection = mediaProjection ?: return
        val vDisplay = virtualDisplay ?: return

        // Hide overlays before capture
        val wasFloating = floatingControlOverlay != null
        floatingControlOverlay?.hideView()

        Handler(Looper.getMainLooper()).postDelayed({
            val point = android.graphics.Point()
            vDisplay.display.getRealSize(point)
            val width = point.x
            val height = point.y

            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            val originalSurface = mediaRecorder?.surface

            imageReader.setOnImageAvailableListener({ reader ->
                var image: Image? = null
                var paddedBitmap: Bitmap? = null
                var finalBitmap: Bitmap? = null
                try {
                    image = reader.acquireLatestImage()
                    if (image != null) {
                        val planes      = image.planes
                        val buffer      = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride   = planes[0].rowStride
                        val rowPadding  = rowStride - pixelStride * width

                        paddedBitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
                        )
                        paddedBitmap.copyPixelsFromBuffer(buffer)
                        finalBitmap = Bitmap.createBitmap(paddedBitmap, 0, 0, width, height)
                        saveScreenshotToMediaStore(finalBitmap)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    image?.close()
                    imageReader.close()
                    // BUG-11 FIX: Recycle both bitmaps to prevent ~10 MB heap leak per screenshot
                    try { paddedBitmap?.recycle() } catch (_: Exception) {}
                    try { finalBitmap?.recycle() }  catch (_: Exception) {}
                    // Restore original surface
                    if (originalSurface != null && originalSurface.isValid) {
                        try {
                            vDisplay.surface = originalSurface
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    if (wasFloating) floatingControlOverlay?.showView()
                }
            }, Handler(Looper.getMainLooper()))

            // Swap surface to screenshot reader
            try {
                vDisplay.surface = imageReader.surface
            } catch (e: Exception) {
                e.printStackTrace()
                imageReader.close()
                if (wasFloating) floatingControlOverlay?.showView()
            }
        }, 200)
    }


    private fun saveScreenshotToMediaStore(bitmap: Bitmap) {
        val fileName = "ScreenX_Screenshot_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScreenX")
            }
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        }

        val resolver = contentResolver
        val collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val imageUri = resolver.insert(collectionUri, contentValues)

        if (imageUri != null) {
            try {
                resolver.openOutputStream(imageUri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
                sendBroadcast(Intent("com.gxdevs.screenx.action.RECORDING_SAVED").apply {
                    setPackage(packageName)
                })
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this@ScreenRecordService, "Screenshot saved to Pictures/ScreenX", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showFloatingControls() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            return
        }
        if (floatingControlOverlay != null && floatingControlOverlay?.isShowing == true) {
            floatingControlOverlay?.showView()
            floatingControlOverlay?.updateState()
            return
        }

        dismissFloatingControls()
        floatingControlOverlay = FloatingControlOverlay(this)
        floatingControlOverlay?.show(
            onStop = { 
                if (AdbRecordService.isRecording) {
                    val stopAdb = Intent(this, AdbRecordService::class.java).apply {
                        action = AdbRecordService.ACTION_STOP_ADB
                    }
                    startService(stopAdb)
                } else {
                    stopRecording()
                }
            },
            onPauseToggle = {
                if (isPaused) resumeRecording() else pauseRecording()
            },
            onBrushToggle = {
                toggleBrushOverlay()
            },
            onScreenshot = { takeScreenshot() },
            onDismiss = {
                floatingControlOverlay = null
                if (!isRecording && !AdbRecordService.isRecording) {
                    exitService()
                }
            }
        )
    }

    private fun dismissFloatingControls() {
        floatingControlOverlay?.dismiss()
        floatingControlOverlay = null
    }

    private fun toggleBrushOverlay() {
        if (brushDrawingOverlay == null) {
            brushDrawingOverlay = BrushDrawingOverlay(this)
            brushDrawingOverlay?.show {
                dismissBrushOverlay()
            }
            floatingControlOverlay?.bringToFront()
        } else {
            dismissBrushOverlay()
        }
    }

    private fun dismissBrushOverlay() {
        brushDrawingOverlay?.dismiss()
        brushDrawingOverlay = null
    }

    private fun exitService() {
        storageMonitorJob?.cancel()
        storageMonitorJob = null
        if (isRecording) {
            stopRecording()
        }
        dismissFloatingControls()
        dismissBrushOverlay()
        // BUG-13 FIX: Only call stopForeground if we are actually running as a foreground service
        if (isForeground) {
            stopForeground(true)
            isForeground = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {}
        storageMonitorJob?.cancel()
        storageMonitorJob = null
        try {
            sensorManager?.unregisterListener(sensorListener)
        } catch (_: Exception) {}
        dismissFloatingControls()
        dismissBrushOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
