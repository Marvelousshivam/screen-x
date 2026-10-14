package com.gxdevs.screenx.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.CirclePlay
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Tablet
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Usb
import com.composables.icons.lucide.Video
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.VolumeX
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Zap
import com.gxdevs.screenx.data.AdbManager
import com.gxdevs.screenx.data.SettingsManager
import com.gxdevs.screenx.service.PairingInputService
import com.gxdevs.screenx.utils.DeviceCapabilitiesHelper
import com.gxdevs.screenx.utils.NetworkUtils
import com.gxdevs.screenx.utils.UpdateManager
import com.gxdevs.screenx.utils.UpdateCheckResult
import com.gxdevs.screenx.utils.AppUpdateInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.ui.res.stringResource
import com.gxdevs.screenx.R
import com.gxdevs.screenx.ui.components.LanguageSelectionBottomSheet
import com.gxdevs.screenx.utils.LocaleHelper
import com.gxdevs.screenx.ui.components.UpdateDialog
import com.gxdevs.screenx.ui.theme.EmeraldAccent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    settingsManager: SettingsManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Settings Flows
    val currentLanguageCode by settingsManager.languageFlow.collectAsState(
        initial = SettingsManager.getLanguageSync(context)
    )
    val currentAppLanguage = remember(currentLanguageCode) {
        LocaleHelper.getLanguageByCode(currentLanguageCode)
    }
    var showLanguageBottomSheet by remember { mutableStateOf(false) }

    val resolution by settingsManager.resolutionFlow.collectAsState(initial = "1080p")
    val fps by settingsManager.fpsFlow.collectAsState(initial = 30)
    val bitrate by settingsManager.bitrateFlow.collectAsState(initial = 8000000)
    val audioSource by settingsManager.audioSourceFlow.collectAsState(initial = "Mic")
    val voicePriority by settingsManager.voicePriorityFlow.collectAsState(initial = true)
    val micVolume by settingsManager.micVolumeFlow.collectAsState(initial = 200)
    val internalAudioVolume by settingsManager.internalAudioVolumeFlow.collectAsState(initial = 100)
    val vocalClarity by settingsManager.vocalClarityFlow.collectAsState(initial = true)
    val countdown by settingsManager.countdownFlow.collectAsState(initial = 3)
    val showFloating by settingsManager.showFloatingFlow.collectAsState(initial = true)
    val hideDuringRecord by settingsManager.hideDuringRecordFlow.collectAsState(initial = false)
    val themeMode by settingsManager.themeModeFlow.collectAsState(initial = "system")
    val shakeToStop by settingsManager.shakeToStopFlow.collectAsState(initial = false)
    val stopOnScreenOff by settingsManager.stopOnScreenOffFlow.collectAsState(initial = false)
    val keepScreenAwake by settingsManager.keepScreenAwakeFlow.collectAsState(initial = false)
    val showStatusBarPill by settingsManager.showStatusBarPillFlow.collectAsState(initial = true)
    val autoCheckUpdates by settingsManager.autoCheckUpdatesFlow.collectAsState(initial = true)
    val orientation by settingsManager.orientationFlow.collectAsState(initial = "Auto")
    val floatingShowMode by settingsManager.floatingShowModeFlow.collectAsState(initial = "Only when recording")
    val safeStorageStop by settingsManager.safeStorageStopFlow.collectAsState(initial = true)
    val safeStorageThresholdMb by settingsManager.safeStorageThresholdMbFlow.collectAsState(initial = 150)
    val adbEnabled by settingsManager.adbEnabledFlow.collectAsState(initial = false)
    val adbCaptureMode by settingsManager.adbCaptureModeFlow.collectAsState(initial = "mediaprojection")
    val adbPaired by settingsManager.adbPairedFlow.collectAsState(initial = false)
    val swapHomeCards by settingsManager.swapHomeCardsFlow.collectAsState(initial = false)

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var availableUpdateForDialog by remember { mutableStateOf<AppUpdateInfo?>(null) }

    val isAdbRecordingDefault = adbEnabled && adbCaptureMode == "adb"

    LaunchedEffect(isAdbRecordingDefault) {
        if (isAdbRecordingDefault && audioSource != "None") {
            settingsManager.setAudioSource("None")
        }
    }

    // Option Dialog Flags
    var showResDialog by remember { mutableStateOf(false) }
    var showFpsDialog by remember { mutableStateOf(false) }
    var showBitrateDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showMicVolumeDialog by remember { mutableStateOf(false) }
    var showInternalVolumeDialog by remember { mutableStateOf(false) }
    var showCountdownDialog by remember { mutableStateOf(false) }
    var showFloatingShowModeDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showStorageThresholdDialog by remember { mutableStateOf(false) }
    var showAdbModeDialog by remember { mutableStateOf(false) }
    var showAdbLimitationsDialog by remember { mutableStateOf(false) }
    var showAdbConfirmEnableDialog by remember { mutableStateOf(false) }
    var showAdbPairingDialog by remember { mutableStateOf(false) }

    var pendingFloatingMode by remember { mutableStateOf<String?>(null) }
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
            if (pendingFloatingMode != null) {
                coroutineScope.launch {
                    settingsManager.setShowFloating(true)
                    settingsManager.setFloatingShowMode(pendingFloatingMode!!)
                    val mode = pendingFloatingMode
                    pendingFloatingMode = null
                    if (mode?.startsWith("All the time") == true && !com.gxdevs.screenx.service.ScreenRecordService.isRecording) {
                        val intent = Intent(context, com.gxdevs.screenx.service.ScreenRecordService::class.java).apply {
                            action = com.gxdevs.screenx.service.ScreenRecordService.ACTION_START_FLOATING_ONLY
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    }
                }
            }
        } else {
            Toast.makeText(context, context.getString(R.string.toast_overlay_required), Toast.LENGTH_SHORT).show()
            pendingFloatingMode = null
        }
    }

    var notifsEnabled by remember {
        mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notifsEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notifsEnabled = isGranted || androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (isGranted) {
            coroutineScope.launch { settingsManager.setShowStatusBarPill(true) }
        } else {
            Toast.makeText(context, context.getString(R.string.setting_status_bar_pill_perm_warning), Toast.LENGTH_LONG).show()
            try {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    // Dynamic storage calculations
    val freeSpaceGB = remember {
        try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            (stat.blockSizeLong * stat.availableBlocksLong) / (1024 * 1024 * 1024)
        } catch (_: Exception) {
            42L
        }
    }
    val usedPercent = remember {
        try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val total = stat.blockSizeLong * stat.blockCountLong
            val free = stat.blockSizeLong * stat.availableBlocksLong
            (((total - free).toDouble() / total.toDouble()) * 100).toInt()
        } catch (_: Exception) {
            50
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 768.dp)
        ) {
        // Top Header matching HomeScreen spacing
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BouncyIconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Lucide.ArrowLeft,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.settings),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-0.5).sp
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. STORAGE & SAFETY
            item {
                Column {
                    OrionSectionHeader(stringResource(R.string.section_storage_safety))
                    OrionStackedGroupCard {
                        OrionSettingsSwitchItem(
                            icon = Lucide.HardDrive,
                            title = stringResource(R.string.setting_safe_storage_title),
                            subtitle = stringResource(R.string.setting_safe_storage_sub),
                            checked = safeStorageStop,
                            position = StackPosition.Top,
                            onCheckedChange = { coroutineScope.launch { settingsManager.setSafeStorageStop(it) } }
                        )
                        OrionSettingsValueItem(
                            icon = Lucide.HardDrive,
                            title = stringResource(R.string.setting_safe_stop_buffer_title),
                            subtitle = stringResource(R.string.setting_safe_stop_buffer_sub),
                            value = "$safeStorageThresholdMb MB",
                            position = StackPosition.Middle,
                            onClick = { showStorageThresholdDialog = true }
                        )
                        OrionSettingsInfoItem(
                            icon = Lucide.HardDrive,
                            title = stringResource(R.string.setting_internal_storage_title),
                            subtitle = stringResource(
                                R.string.setting_internal_storage_sub_fmt,
                                freeSpaceGB,
                                if (safeStorageStop) stringResource(R.string.safe_stop_active) else stringResource(R.string.safe_stop_off)
                            ),
                            badge = stringResource(R.string.percent_used_fmt, usedPercent),
                            position = StackPosition.Middle
                        )
                        OrionSettingsValueItem(
                            icon = Lucide.Film,
                            title = stringResource(R.string.setting_save_location_title),
                            subtitle = stringResource(R.string.setting_save_location_sub),
                            value = stringResource(R.string.save_location_movies),
                            showChevron = false,
                            position = StackPosition.Bottom,
                            onClick = {
                                Toast.makeText(context, context.getString(R.string.toast_location_locked), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // 2. VIDEO CAPTURE
            item {
                Column {
                    OrionSectionHeader(stringResource(R.string.section_video_capture))
                    OrionStackedGroupCard {
                        OrionSettingsValueItem(
                            icon = Lucide.Video,
                            title = stringResource(R.string.setting_resolution_title),
                            subtitle = stringResource(R.string.setting_resolution_sub),
                            value = resolution,
                            position = StackPosition.Top,
                            onClick = { showResDialog = true }
                        )
                        OrionSettingsValueItem(
                            icon = Lucide.CirclePlay,
                            title = stringResource(R.string.setting_fps_title),
                            subtitle = stringResource(R.string.setting_fps_sub),
                            value = "$fps FPS",
                            position = StackPosition.Middle,
                            onClick = { showFpsDialog = true }
                        )
                        OrionSettingsValueItem(
                            icon = Lucide.Film,
                            title = stringResource(R.string.setting_bitrate_title),
                            subtitle = stringResource(R.string.setting_bitrate_sub),
                            value = "${bitrate / 1000000} Mbps",
                            position = StackPosition.Middle,
                            onClick = { showBitrateDialog = true }
                        )
                        val orientationDisplay = when (orientation) {
                            "Portrait" -> stringResource(R.string.orientation_portrait)
                            "Landscape" -> stringResource(R.string.orientation_landscape)
                            else -> stringResource(R.string.orientation_auto)
                        }
                        OrionSettingsValueItem(
                            icon = when (orientation) {
                                "Auto" -> Lucide.RotateCcw
                                "Portrait" -> Lucide.Smartphone
                                else -> Lucide.Tablet
                            },
                            title = stringResource(R.string.setting_orientation_title),
                            subtitle = stringResource(R.string.setting_orientation_sub),
                            value = orientationDisplay,
                            position = StackPosition.Bottom,
                            onClick = {
                                val nextOri = when (orientation) {
                                    "Auto" -> "Portrait"
                                    "Portrait" -> "Landscape"
                                    else -> "Auto"
                                }
                                coroutineScope.launch { settingsManager.setOrientation(nextOri) }
                            }
                        )
                    }
                }
            }

            // 3. AUDIO & VOICE PRIORITY
            item {
                Column {
                    OrionSectionHeader(stringResource(R.string.section_audio_voice))
                    OrionStackedGroupCard {
                        val audioSubtitle = if (isAdbRecordingDefault) {
                            stringResource(R.string.audio_sub_stealth_locked)
                        } else {
                            when (audioSource) {
                                "Mic" -> stringResource(R.string.audio_sub_mic)
                                "System" -> stringResource(R.string.audio_sub_system)
                                "MicSystem" -> stringResource(R.string.audio_sub_dual)
                                "None" -> stringResource(R.string.audio_sub_none)
                                else -> stringResource(R.string.audio_sub_mic)
                            }
                        }
                        val audioDisplay = if (isAdbRecordingDefault) {
                            stringResource(R.string.audio_display_none)
                        } else {
                            when (audioSource) {
                                "Mic" -> stringResource(R.string.audio_display_mic)
                                "System" -> stringResource(R.string.audio_display_system)
                                "MicSystem" -> stringResource(R.string.audio_display_dual)
                                "None" -> stringResource(R.string.audio_display_muted)
                                else -> stringResource(R.string.audio_display_mic)
                            }
                        }
                        val audioIcon = if (isAdbRecordingDefault) {
                            Lucide.VolumeX
                        } else {
                            when (audioSource) {
                                "System", "MicSystem" -> Lucide.Volume2
                                "None" -> Lucide.VolumeX
                                else -> Lucide.Mic
                            }
                        }

                        val hasSubSettings = !isAdbRecordingDefault && (audioSource == "MicSystem" || audioSource == "Mic" || audioSource == "System")

                        OrionSettingsValueItem(
                            icon = audioIcon,
                            title = stringResource(R.string.setting_audio_input_title),
                            subtitle = audioSubtitle,
                            value = audioDisplay,
                            position = if (hasSubSettings) StackPosition.Top else StackPosition.Single,
                            showChevron = !isAdbRecordingDefault,
                            onClick = {
                                if (isAdbRecordingDefault) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_audio_locked_stealth),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    showAudioDialog = true
                                }
                            }
                        )

                        if (!isAdbRecordingDefault) {
                            if (audioSource == "MicSystem") {
                                OrionSettingsSwitchItem(
                                    icon = Lucide.Volume2,
                                    title = stringResource(R.string.setting_voice_priority_title),
                                    subtitle = stringResource(R.string.setting_voice_priority_sub),
                                    checked = voicePriority,
                                    position = StackPosition.Middle,
                                    onCheckedChange = { coroutineScope.launch { settingsManager.setVoicePriority(it) } }
                                )
                                OrionSettingsValueItem(
                                    icon = Lucide.Mic,
                                    title = stringResource(R.string.setting_mic_boost_title),
                                    subtitle = stringResource(R.string.setting_mic_boost_sub),
                                    value = "$micVolume%",
                                    position = StackPosition.Middle,
                                    onClick = { showMicVolumeDialog = true }
                                )
                                OrionSettingsValueItem(
                                    icon = Lucide.Volume2,
                                    title = stringResource(R.string.setting_internal_audio_mix_title),
                                    subtitle = stringResource(R.string.setting_internal_audio_mix_sub_dual),
                                    value = "$internalAudioVolume%",
                                    position = StackPosition.Middle,
                                    onClick = { showInternalVolumeDialog = true }
                                )
                                OrionSettingsSwitchItem(
                                    icon = Lucide.Mic,
                                    title = stringResource(R.string.setting_vocal_clarity_title),
                                    subtitle = stringResource(R.string.setting_vocal_clarity_sub),
                                    checked = vocalClarity,
                                    position = StackPosition.Bottom,
                                    onCheckedChange = { coroutineScope.launch { settingsManager.setVocalClarity(it) } }
                                )
                            } else if (audioSource == "Mic") {
                                OrionSettingsValueItem(
                                    icon = Lucide.Mic,
                                    title = stringResource(R.string.setting_mic_boost_title),
                                    subtitle = stringResource(R.string.setting_mic_boost_sub),
                                    value = "$micVolume%",
                                    position = StackPosition.Middle,
                                    onClick = { showMicVolumeDialog = true }
                                )
                                OrionSettingsSwitchItem(
                                    icon = Lucide.Mic,
                                    title = stringResource(R.string.setting_vocal_clarity_title),
                                    subtitle = stringResource(R.string.setting_vocal_clarity_sub),
                                    checked = vocalClarity,
                                    position = StackPosition.Bottom,
                                    onCheckedChange = { coroutineScope.launch { settingsManager.setVocalClarity(it) } }
                                )
                            } else if (audioSource == "System") {
                                OrionSettingsValueItem(
                                    icon = Lucide.Volume2,
                                    title = stringResource(R.string.setting_internal_audio_mix_title),
                                    subtitle = stringResource(R.string.setting_internal_audio_mix_sub_sys),
                                    value = "$internalAudioVolume%",
                                    position = StackPosition.Bottom,
                                    onClick = { showInternalVolumeDialog = true }
                                )
                            }
                        }
                    }
                }
            }

            // 4. RECORDING CONTROLS
            item {
                Column {
                    OrionSectionHeader(stringResource(R.string.section_recording_controls))
                    OrionStackedGroupCard {
                        OrionSettingsValueItem(
                            icon = Lucide.RotateCcw,
                            title = stringResource(R.string.setting_countdown_title),
                            subtitle = stringResource(R.string.setting_countdown_sub),
                            value = if (countdown == 0) stringResource(R.string.countdown_off) else "${countdown}s",
                            position = StackPosition.Top,
                            onClick = { showCountdownDialog = true }
                        )
                        OrionSettingsSwitchItem(
                            icon = Lucide.Smartphone,
                            title = stringResource(R.string.setting_shake_stop_title),
                            subtitle = stringResource(R.string.setting_shake_stop_sub),
                            checked = shakeToStop,
                            position = StackPosition.Middle,
                            onCheckedChange = { coroutineScope.launch { settingsManager.setShakeToStop(it) } }
                        )
                        OrionSettingsSwitchItem(
                            icon = Lucide.Smartphone,
                            title = stringResource(R.string.setting_screen_off_stop_title),
                            subtitle = stringResource(R.string.setting_screen_off_stop_sub),
                            checked = stopOnScreenOff,
                            position = StackPosition.Middle,
                            onCheckedChange = { coroutineScope.launch { settingsManager.setStopOnScreenOff(it) } }
                        )
                        OrionSettingsSwitchItem(
                            icon = Lucide.Sun,
                            title = stringResource(R.string.setting_keep_screen_awake_title),
                            subtitle = stringResource(R.string.setting_keep_screen_awake_sub),
                            checked = keepScreenAwake,
                            position = StackPosition.Middle,
                            onCheckedChange = { coroutineScope.launch { settingsManager.setKeepScreenAwake(it) } }
                        )
                        OrionSettingsSwitchItem(
                            icon = Lucide.Activity,
                            title = stringResource(R.string.setting_status_bar_pill_title),
                            subtitle = if (!notifsEnabled) {
                                stringResource(R.string.setting_status_bar_pill_perm_warning)
                            } else {
                                stringResource(R.string.setting_status_bar_pill_sub)
                            },
                            checked = showStatusBarPill && notifsEnabled,
                            position = StackPosition.Middle,
                            onCheckedChange = { checked ->
                                if (checked && !notifsEnabled) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        try {
                                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                }
                                            } else {
                                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.fromParts("package", context.packageName, null)
                                                }
                                            }
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, context.getString(R.string.setting_status_bar_pill_perm_warning), Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    coroutineScope.launch { settingsManager.setShowStatusBarPill(checked) }
                                }
                            }
                        )
                        val floatingBallSummary = when {
                            !showFloating -> stringResource(R.string.floating_status_hidden)
                            floatingShowMode.startsWith("All the time") -> stringResource(R.string.floating_status_always)
                            else -> stringResource(R.string.floating_status_recording)
                        }
                        OrionSettingsValueItem(
                            icon = Lucide.CircleDot,
                            title = stringResource(R.string.setting_floating_controls_title),
                            subtitle = stringResource(R.string.setting_floating_controls_sub),
                            value = floatingBallSummary,
                            position = StackPosition.Middle,
                            onClick = { showFloatingShowModeDialog = true }
                        )
                        if (showFloating) {
                            OrionSettingsSwitchItem(
                                icon = Lucide.CircleDot,
                                title = stringResource(R.string.setting_hide_ball_rec_title),
                                subtitle = stringResource(R.string.setting_hide_ball_rec_sub),
                                checked = hideDuringRecord,
                                position = StackPosition.Middle,
                                onCheckedChange = { coroutineScope.launch { settingsManager.setHideDuringRecord(it) } }
                            )
                        }
                        OrionSettingsValueItem(
                            icon = Lucide.Zap,
                            title = stringResource(R.string.setting_qs_tile_title),
                            subtitle = stringResource(R.string.setting_qs_tile_sub),
                            value = stringResource(R.string.qs_tile_add_btn),
                            position = StackPosition.Bottom,
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val statusBarManager = context.getSystemService(android.app.StatusBarManager::class.java)
                                    statusBarManager?.requestAddTileService(
                                        android.content.ComponentName(context, com.gxdevs.screenx.service.ScreenXTileService::class.java),
                                        context.getString(com.gxdevs.screenx.R.string.app_name),
                                        android.graphics.drawable.Icon.createWithResource(context, com.gxdevs.screenx.R.drawable.ic_qs_record),
                                        context.mainExecutor
                                    ) { resultCode ->
                                        if (resultCode == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED) {
                                            Toast.makeText(context, context.getString(R.string.toast_tile_added), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_tile_manual_guide),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }

            // 5. APPEARANCE & THEME
            item {
                Column {
                    OrionSectionHeader(stringResource(R.string.section_appearance_theme))
                    OrionStackedGroupCard {
                        OrionSettingsValueItem(
                            icon = Icons.Default.Translate,
                            title = stringResource(R.string.app_language),
                            subtitle = stringResource(R.string.language_subtitle),
                            value = currentAppLanguage.nativeName,
                            position = StackPosition.Top,
                            onClick = { showLanguageBottomSheet = true }
                        )
                        val themeSummary = when (themeMode) {
                            "dark" -> stringResource(R.string.theme_dark)
                            "light" -> stringResource(R.string.theme_light)
                            "system" -> stringResource(R.string.theme_system)
                            "dynamic" -> stringResource(R.string.theme_dynamic)
                            else -> stringResource(R.string.theme_system)
                        }
                        OrionSettingsValueItem(
                            icon = Lucide.Camera,
                            title = stringResource(R.string.setting_theme_mode_title),
                            subtitle = stringResource(R.string.setting_theme_mode_sub),
                            value = themeSummary,
                            position = StackPosition.Bottom,
                            onClick = { showThemeDialog = true }
                        )
                    }
                }
            }

            // 6. ADB STEALTH RECORDING
            item {
                Column {
                    OrionSectionHeader(stringResource(R.string.section_stealth_recording))
                    OrionStackedGroupCard {
                        OrionSettingsSwitchItem(
                            icon = Lucide.Zap,
                            title = stringResource(R.string.setting_stealth_rec_title),
                            subtitle = stringResource(R.string.setting_stealth_rec_sub),
                            checked = adbEnabled,
                            position = if (adbEnabled) StackPosition.Top else StackPosition.Single,
                            onCheckedChange = { willEnable ->
                                if (willEnable) {
                                    showAdbConfirmEnableDialog = true
                                } else {
                                    coroutineScope.launch { settingsManager.setAdbEnabled(false) }
                                }
                            },
                            iconTint = if (adbEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            iconBackground = MaterialTheme.colorScheme.surfaceVariant
                        )

                        if (adbEnabled) {
                            OrionSettingsValueItem(
                                icon = Lucide.Usb,
                                title = stringResource(R.string.setting_wireless_adb_title),
                                subtitle = if (adbPaired)
                                    stringResource(R.string.setting_wireless_adb_sub_paired)
                                else
                                    stringResource(R.string.setting_wireless_adb_sub_unpaired),
                                value = if (adbPaired) stringResource(R.string.badge_paired) else stringResource(R.string.badge_set_up),
                                position = StackPosition.Middle,
                                iconTint = if (adbPaired)
                                    EmeraldAccent
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                iconBackground = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = { showAdbPairingDialog = true }
                            )

                            val adbModeDisplay = when (adbCaptureMode) {
                                "adb" -> stringResource(R.string.mode_display_stealth)
                                "ask" -> stringResource(R.string.mode_display_prompt)
                                else -> stringResource(R.string.mode_display_standard)
                            }
                            OrionSettingsValueItem(
                                icon = Lucide.Settings,
                                title = stringResource(R.string.setting_default_engine_title),
                                subtitle = stringResource(R.string.setting_default_engine_sub),
                                value = adbModeDisplay,
                                position = StackPosition.Middle,
                                onClick = { showAdbModeDialog = true }
                            )

                            OrionSettingsSwitchItem(
                                icon = Lucide.RotateCcw,
                                title = stringResource(R.string.setting_swap_cards_title),
                                subtitle = stringResource(R.string.setting_swap_cards_sub),
                                checked = swapHomeCards,
                                position = StackPosition.Middle,
                                onCheckedChange = { coroutineScope.launch { settingsManager.setSwapHomeCards(it) } }
                            )

                            OrionSettingsInfoItem(
                                icon = Lucide.Info,
                                title = stringResource(R.string.setting_stealth_rules_title),
                                subtitle = stringResource(R.string.setting_stealth_rules_sub),
                                badge = stringResource(R.string.badge_stealth),
                                position = StackPosition.Bottom,
                                onClick = { showAdbLimitationsDialog = true }
                            )
                        }
                    }
                }
            }

            // 7. ABOUT SCREENX
            item {
                Column {
                    OrionSectionHeader(stringResource(R.string.section_about))
                    OrionStackedGroupCard {
                        val appVersion = remember {
                            try {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.5.0"
                            } catch (_: Exception) {
                                "1.5.0"
                            }
                        }
                        OrionSettingsInfoItem(
                            icon = Lucide.Settings,
                            title = stringResource(R.string.setting_about_app_title),
                            subtitle = stringResource(R.string.setting_about_app_sub_fmt, appVersion),
                            badge = "v$appVersion",
                            position = StackPosition.Top
                        )
                        OrionSettingsValueItem(
                            icon = Lucide.RotateCcw,
                            title = stringResource(R.string.setting_check_updates_title),
                            subtitle = if (isCheckingUpdate) stringResource(R.string.setting_check_updates_sub_checking) else stringResource(R.string.setting_check_updates_sub_idle),
                            value = if (isCheckingUpdate) stringResource(R.string.status_checking) else "v$appVersion",
                            position = StackPosition.Middle,
                            onClick = {
                                if (!isCheckingUpdate) {
                                    isCheckingUpdate = true
                                    coroutineScope.launch {
                                        val result = UpdateManager.checkForUpdate(context)
                                        isCheckingUpdate = false
                                        when (result) {
                                            is UpdateCheckResult.Available -> {
                                                availableUpdateForDialog = result.updateInfo
                                            }
                                            is UpdateCheckResult.Latest -> {
                                                Toast.makeText(context, context.getString(R.string.toast_update_latest_fmt, result.currentVersion), Toast.LENGTH_LONG).show()
                                            }
                                            is UpdateCheckResult.Error -> {
                                                Toast.makeText(context, context.getString(R.string.toast_update_error_fmt, result.message), Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                }
                            }
                        )
                        OrionSettingsSwitchItem(
                            icon = Lucide.Zap,
                            title = stringResource(R.string.setting_auto_check_updates_title),
                            subtitle = stringResource(R.string.setting_auto_check_updates_sub),
                            checked = autoCheckUpdates,
                            position = StackPosition.Middle,
                            onCheckedChange = { coroutineScope.launch { settingsManager.setAutoCheckUpdates(it) } }
                        )
                        val maxFps = DeviceCapabilitiesHelper.getMaxSupportedFps(context)
                        OrionSettingsInfoItem(
                            icon = Lucide.CirclePlay,
                            title = stringResource(R.string.setting_refresh_rate_title),
                            subtitle = stringResource(R.string.setting_refresh_rate_sub_fmt, maxFps),
                            badge = "$maxFps Hz",
                            position = StackPosition.Bottom
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    }

    // Update Dialog if available from manual check
    availableUpdateForDialog?.let { updateInfo ->
        UpdateDialog(
            updateInfo = updateInfo,
            onDismiss = { availableUpdateForDialog = null },
            onDownload = {
                availableUpdateForDialog = null
                UpdateManager.downloadOrOpenUpdate(context, updateInfo.apkDownloadUrl ?: updateInfo.htmlUrl)
            },
            onViewGitHub = {
                UpdateManager.openUrl(context, updateInfo.htmlUrl)
            }
        )
    }

    // Confirmation Info Dialog shown when user tries to turn on the ADB switch
    if (showAdbConfirmEnableDialog) {
        AdbLimitationsDialog(
            onDismiss = { showAdbConfirmEnableDialog = false },
            onConfirmEnable = {
                coroutineScope.launch {
                    settingsManager.setAdbEnabled(true)
                    if (adbCaptureMode == "adb") {
                        settingsManager.setAudioSource("None")
                    }
                }
                showAdbConfirmEnableDialog = false
            }
        )
    }

    // Read-only ADB limitations info dialog
    if (showAdbLimitationsDialog) {
        AdbLimitationsDialog(onDismiss = { showAdbLimitationsDialog = false })
    }

    // ADB Pairing Dialog
    if (showAdbPairingDialog) {
        AdbPairingDialog(
            settingsManager = settingsManager,
            onDismiss = { showAdbPairingDialog = false }
        )
    }

    // Theme Mode Dialog
    if (showThemeDialog) {
        val lightStr = stringResource(R.string.theme_light)
        val darkStr = stringResource(R.string.theme_dark)
        val sysStr = stringResource(R.string.theme_system)
        val dynStr = stringResource(R.string.theme_dynamic)
        val mapping = mapOf(
            "light" to lightStr,
            "dark" to darkStr,
            "system" to sysStr,
            "dynamic" to dynStr
        )
        OptionSelectionDialog(
            title = stringResource(R.string.dialog_select_theme),
            options = mapping.values.toList(),
            selectedOption = mapping[themeMode] ?: sysStr,
            onDismiss = { showThemeDialog = false },
            onSelect = { displayName ->
                val key = mapping.entries.firstOrNull { it.value == displayName }?.key ?: "system"
                coroutineScope.launch { settingsManager.setThemeMode(key) }
                showThemeDialog = false
            }
        )
    }

    // Storage Threshold Dialog
    if (showStorageThresholdDialog) {
        val options = listOf("100 MB", "150 MB (Recommended)", "200 MB", "300 MB", "500 MB")
        val currentStr = when (safeStorageThresholdMb) {
            100 -> "100 MB"
            150 -> "150 MB (Recommended)"
            200 -> "200 MB"
            300 -> "300 MB"
            500 -> "500 MB"
            else -> "$safeStorageThresholdMb MB"
        }
        OptionSelectionDialog(
            title = stringResource(R.string.dialog_safe_stop_buffer_title),
            options = options,
            selectedOption = currentStr,
            onDismiss = { showStorageThresholdDialog = false },
            onSelect = { selected ->
                val mb = selected.substringBefore(" MB").toIntOrNull() ?: 150
                coroutineScope.launch { settingsManager.setSafeStorageThresholdMb(mb) }
                showStorageThresholdDialog = false
            }
        )
    }

    // Video Resolution Dialog
    if (showResDialog) {
        OptionSelectionDialog(
            title = stringResource(R.string.dialog_select_resolution),
            options = DeviceCapabilitiesHelper.getResolutionOptions(context),
            selectedOption = resolution,
            onDismiss = { showResDialog = false },
            onSelect = {
                coroutineScope.launch { settingsManager.setResolution(it) }
                showResDialog = false
            }
        )
    }

    // Frame Rate Dialog
    if (showFpsDialog) {
        OptionSelectionDialog(
            title = stringResource(R.string.dialog_select_fps),
            options = DeviceCapabilitiesHelper.getFpsOptions(context),
            selectedOption = fps.toString(),
            onDismiss = { showFpsDialog = false },
            onSelect = {
                coroutineScope.launch { settingsManager.setFps(it.toInt()) }
                showFpsDialog = false
            }
        )
    }

    // Video Bitrate Dialog
    if (showBitrateDialog) {
        val options = DeviceCapabilitiesHelper.getBitrateOptions()
        val selectedString = "${bitrate / 1000000} Mbps"
        OptionSelectionDialog(
            title = stringResource(R.string.dialog_select_bitrate),
            options = options,
            selectedOption = selectedString,
            onDismiss = { showBitrateDialog = false },
            onSelect = {
                val value = it.substringBefore(" Mbps").toInt() * 1000000
                coroutineScope.launch { settingsManager.setBitrate(value) }
                showBitrateDialog = false
            }
        )
    }

    // Audio Source Dialog
    if (showAudioDialog) {
        val micStr = stringResource(R.string.audio_opt_mic)
        val sysStr = stringResource(R.string.audio_opt_system)
        val dualStr = stringResource(R.string.audio_opt_dual)
        val noneStr = stringResource(R.string.audio_opt_none)
        val mapping = linkedMapOf(
            "Mic" to micStr,
            "System" to sysStr,
            "MicSystem" to dualStr,
            "None" to noneStr
        )
        val options = mapping.values.toList()
        val selectedOption = mapping[audioSource] ?: micStr
        OptionSelectionDialog(
            title = stringResource(R.string.dialog_select_audio),
            options = options,
            selectedOption = selectedOption,
            onDismiss = { showAudioDialog = false },
            onSelect = { displayName ->
                val sourceKey = mapping.entries.firstOrNull { it.value == displayName }?.key ?: "Mic"
                coroutineScope.launch { settingsManager.setAudioSource(sourceKey) }
                showAudioDialog = false
            }
        )
    }

    // Microphone Volume Boost Dialog
    if (showMicVolumeDialog) {
        val options = listOf(
            "100% (Standard)",
            "200% (+6 dB • Recommended)",
            "300% (+9.5 dB)",
            "350% (+11 dB)",
            "450% (+13 dB • Quiet Voice)",
            "500% (+14 dB • High Boost)"
        )
        val currentStr = when (micVolume) {
            100 -> "100% (Standard)"
            200 -> "200% (+6 dB • Recommended)"
            300 -> "300% (+9.5 dB)"
            350 -> "350% (+11 dB)"
            450 -> "450% (+13 dB • Quiet Voice)"
            500 -> "500% (+14 dB • High Boost)"
            else -> "$micVolume%"
        }
        OptionSelectionDialog(
            title = stringResource(R.string.dialog_mic_boost),
            options = options,
            selectedOption = currentStr,
            onDismiss = { showMicVolumeDialog = false },
            onSelect = { selected ->
                val percent = selected.substringBefore("%").toIntOrNull() ?: 200
                coroutineScope.launch { settingsManager.setMicVolume(percent) }
                showMicVolumeDialog = false
            }
        )
    }

    // Internal Audio Mix Dialog
    if (showInternalVolumeDialog) {
        val options = listOf(
            "25% (Background Audio)",
            "50% (Balanced Voice & Game)",
            "75% (Clear Game Audio)",
            "100% (Full Output • Recommended)"
        )
        val currentStr = when (internalAudioVolume) {
            25 -> "25% (Background Audio)"
            50 -> "50% (Balanced Voice & Game)"
            75 -> "75% (Clear Game Audio)"
            100 -> "100% (Full Output • Recommended)"
            else -> "$internalAudioVolume%"
        }
        OptionSelectionDialog(
            title = stringResource(R.string.dialog_internal_audio_mix),
            options = options,
            selectedOption = currentStr,
            onDismiss = { showInternalVolumeDialog = false },
            onSelect = { selected ->
                val percent = selected.substringBefore("%").toIntOrNull() ?: 100
                coroutineScope.launch { settingsManager.setInternalAudioVolume(percent) }
                showInternalVolumeDialog = false
            }
        )
    }

    // Countdown Dialog
    if (showCountdownDialog) {
        val offStr = stringResource(R.string.countdown_off)
        val mapping = mapOf("0" to offStr, "3" to "3s", "5" to "5s", "10" to "10s", "20" to "20s")
        val options = mapping.values.toList()
        val selectedOption = mapping[countdown.toString()] ?: "3s"
        OptionSelectionDialog(
            title = stringResource(R.string.dialog_select_countdown),
            options = options,
            selectedOption = selectedOption,
            onDismiss = { showCountdownDialog = false },
            onSelect = { displayName ->
                val key = mapping.entries.firstOrNull { it.value == displayName }?.key ?: "3"
                coroutineScope.launch { settingsManager.setCountdown(key.toInt()) }
                showCountdownDialog = false
            }
        )
    }

    // ADB Capture Mode Dialog
    if (showAdbModeDialog) {
        val mpStr = stringResource(R.string.mode_mediaprojection)
        val stealthStr = stringResource(R.string.mode_stealth)
        val askStr = stringResource(R.string.mode_ask)
        val modeMapping = mapOf(
            "mediaprojection" to mpStr,
            "adb" to stealthStr,
            "ask" to askStr
        )
        OptionSelectionDialog(
            title = stringResource(R.string.dialog_default_recording_mode),
            options = modeMapping.values.toList(),
            selectedOption = modeMapping[adbCaptureMode] ?: mpStr,
            onDismiss = { showAdbModeDialog = false },
            onSelect = { displayName ->
                val key = modeMapping.entries.firstOrNull { it.value == displayName }?.key ?: "mediaprojection"
                coroutineScope.launch {
                    settingsManager.setAdbCaptureMode(key)
                    if (key == "adb" && adbEnabled) {
                        settingsManager.setAudioSource("None")
                    }
                }
                showAdbModeDialog = false
            }
        )
    }

    // Floating Show Mode Dialog
    if (showFloatingShowModeDialog) {
        val hideStr = stringResource(R.string.floating_hide_always)
        val recStr = stringResource(R.string.floating_while_recording)
        val allStr = stringResource(R.string.floating_always)
        val options = listOf(hideStr, recStr, allStr)
        val currentSelected = when {
            !showFloating -> hideStr
            floatingShowMode.startsWith("All the time") -> allStr
            else -> recStr
        }
        OptionSelectionDialog(
            title = stringResource(R.string.dialog_floating_ball),
            options = options,
            selectedOption = currentSelected,
            onDismiss = { showFloatingShowModeDialog = false },
            onSelect = { selected ->
                coroutineScope.launch {
                    val intent = Intent(context, com.gxdevs.screenx.service.ScreenRecordService::class.java)
                    when (selected) {
                        hideStr -> {
                            settingsManager.setShowFloating(false)
                            if (!com.gxdevs.screenx.service.ScreenRecordService.isRecording) {
                                intent.action = com.gxdevs.screenx.service.ScreenRecordService.ACTION_EXIT
                                context.startService(intent)
                            }
                        }
                        recStr -> {
                            settingsManager.setShowFloating(true)
                            settingsManager.setFloatingShowMode("Only while recording")
                            if (!com.gxdevs.screenx.service.ScreenRecordService.isRecording) {
                                intent.action = com.gxdevs.screenx.service.ScreenRecordService.ACTION_EXIT
                                context.startService(intent)
                            }
                        }
                        allStr -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                pendingFloatingMode = "All the time"
                                Toast.makeText(context, context.getString(R.string.toast_allow_overlay), Toast.LENGTH_LONG).show()
                                val overlayIntent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                overlayPermissionLauncher.launch(overlayIntent)
                            } else {
                                settingsManager.setShowFloating(true)
                                settingsManager.setFloatingShowMode("All the time")
                                if (!com.gxdevs.screenx.service.ScreenRecordService.isRecording) {
                                    intent.action = com.gxdevs.screenx.service.ScreenRecordService.ACTION_START_FLOATING_ONLY
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                }
                            }
                        }
                    }
                }
                showFloatingShowModeDialog = false
            }
        )
    }

    if (showLanguageBottomSheet) {
        LanguageSelectionBottomSheet(
            currentLanguageCode = currentLanguageCode,
            onDismiss = { showLanguageBottomSheet = false },
            onSelectLanguage = { selectedLang ->
                coroutineScope.launch {
                    settingsManager.setLanguage(selectedLang)
                    LocaleHelper.updateSystemLocales(context, selectedLang)
                }
                showLanguageBottomSheet = false
            }
        )
    }
}
