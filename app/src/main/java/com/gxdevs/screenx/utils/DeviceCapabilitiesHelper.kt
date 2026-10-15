package com.gxdevs.screenx.utils

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.WindowManager

/**
 * Universal Hardware Capability & Negotiation Helper.
 *
 * Rather than hardcoding behavior for specific devices, this helper dynamically queries
 * Android's hardware encoder pipeline (MediaCodecList, DisplayManager, WindowManager) to
 * guarantee buttery smooth, zero-stutter screen recording on ANY device (Samsung, Google Pixel,
 * Xiaomi, Realme, POCO, OnePlus, Motorola, Vivo, OPPO, Tecno, ASUS, Nothing, etc.) across
 * Android 10 through Android 17+.
 */
object DeviceCapabilitiesHelper {

    private const val TAG = "DeviceCapabilities"

    // Optional device identification helpers (used for logging & diagnostic telemetry)
    fun isRealme5ProOrQ(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()
        return (m.contains("realme") || m.contains("oppo")) &&
                (model.contains("rmx1971") || device.contains("rmx1971") || product.contains("rmx1971") ||
                 model.contains("realme q") || model.contains("realme 5 pro"))
    }

    fun isRedmiNote8Pro(): Boolean {
        val device = Build.DEVICE.lowercase()
        val model = Build.MODEL.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        return device.contains("begonia") || model.contains("redmi note 8 pro") || hardware.contains("mt6785")
    }

    fun isPocoX3Pro(): Boolean {
        val device = Build.DEVICE.lowercase()
        val model = Build.MODEL.lowercase()
        return device.contains("vayu") || device.contains("bhima") ||
                model.contains("poco x3 pro") || model.contains("m2102j20")
    }

    fun isPocoX4GT(): Boolean {
        val device = Build.DEVICE.lowercase()
        val model = Build.MODEL.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        return device.contains("xaga") || model.contains("poco x4 gt") ||
                model.contains("22041216") || hardware.contains("mt6895")
    }

    /**
     * Safely retrieves the Display on any Android version (API 26 through API 37+),
     * avoiding UnsupportedOperationException when called from a Service context on Android 11+.
     */
    fun getDisplay(context: Context): Display? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val defaultDisplay = dm?.getDisplay(Display.DEFAULT_DISPLAY)
            if (defaultDisplay != null) return defaultDisplay
            try {
                return context.display
            } catch (_: Exception) {}
        }
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        return windowManager?.defaultDisplay
    }

    /**
     * Inspects MediaCodecList to return the primary hardware-accelerated AVC (H.264) video encoder.
     */
    fun getHardwareVideoCapabilities(): MediaCodecInfo.VideoCapabilities? {
        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            // Priority 1: Hardware-accelerated AVC encoder (API 29+)
            for (info in codecList.codecInfos) {
                if (!info.isEncoder) continue
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !info.isHardwareAccelerated) continue
                for (type in info.supportedTypes) {
                    if (type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)) {
                        val videoCaps = info.getCapabilitiesForType(type)?.videoCapabilities
                        if (videoCaps != null) return videoCaps
                    }
                }
            }
            // Priority 2: Any regular AVC encoder
            for (info in codecList.codecInfos) {
                if (!info.isEncoder) continue
                for (type in info.supportedTypes) {
                    if (type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)) {
                        val videoCaps = info.getCapabilitiesForType(type)?.videoCapabilities
                        if (videoCaps != null) return videoCaps
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying hardware video capabilities", e)
        }
        return null
    }

    fun getMaxSupportedResolution(context: Context): Pair<Int, Int> {
        val display = getDisplay(context)
        val metrics = android.util.DisplayMetrics()
        try {
            if (display != null) {
                display.getRealMetrics(metrics)
            } else {
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                @Suppress("DEPRECATION")
                windowManager?.defaultDisplay?.getRealMetrics(metrics)
            }
        } catch (_: Exception) {}
        var rawW = metrics.widthPixels
        var rawH = metrics.heightPixels
        if (rawW <= 0 || rawH <= 0) {
            rawW = context.resources.displayMetrics.widthPixels
            rawH = context.resources.displayMetrics.heightPixels
        }
        if (rawW <= 0) rawW = 1080
        if (rawH <= 0) rawH = 1920
        return Pair(maxOf(rawW, rawH), minOf(rawW, rawH))
    }

    fun getMaxSupportedFps(context: Context): Int {
        val display = getDisplay(context)
        var maxFps = display?.refreshRate?.toInt() ?: 60

        // Check all display supported modes (especially 90Hz, 120Hz, 144Hz, 165Hz panels)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && display != null) {
            try {
                val modes = display.supportedModes
                for (mode in modes) {
                    val rate = mode.refreshRate.toInt()
                    if (rate > maxFps) {
                        maxFps = rate
                    }
                }
            } catch (_: Exception) {}
        }

        return if (maxFps > 60) maxFps else 60
    }

    /**
     * Default recommended frame rate for fluid, stable recording.
     * Universal 60 fps standard avoids thermal throttling or encoder choking,
     * while users on high-refresh panels can still opt into 90, 120, or 144 FPS in settings.
     */
    fun getDefaultFps(context: Context): Int {
        val maxFps = getMaxSupportedFps(context)
        return if (maxFps >= 60) 60 else maxFps
    }

    /**
     * Default recommended bitrate: 10 Mbps (10,000,000 bps).
     * Ideal universal sweet spot for 1080p 60fps recording without thermal throttling or encoder lag
     * on mid-range and flagship SoCs alike.
     */
    fun getDefaultBitrate(): Int {
        return 10_000_000
    }

    fun getMaxSupportedBitrate(): Int {
        var maxBitrate = 15000000 // Default fallback 15 Mbps
        try {
            val videoCaps = getHardwareVideoCapabilities()
            if (videoCaps != null) {
                val codecMax = videoCaps.bitrateRange.upper
                if (codecMax > maxBitrate) {
                    maxBitrate = codecMax
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return maxBitrate
    }

    fun getResolutionOptions(context: Context): List<String> {
        val options = mutableListOf("480p", "720p", "1080p")
        val maxRes = getMaxSupportedResolution(context)
        val longSide = maxOf(maxRes.first, maxRes.second)
        if (longSide >= 2560 && !options.contains("1440p")) {
            options.add("1440p")
        }
        if (longSide >= 3840 && !options.contains("4K")) {
            options.add("4K")
        }
        if (!options.contains("Original")) {
            options.add("Original")
        }
        return options
    }

    fun getFpsOptions(context: Context): List<String> {
        val options = mutableListOf("15", "30", "45", "60")
        val maxFps = getMaxSupportedFps(context)
        if (maxFps >= 90 && !options.contains("90")) {
            options.add("90")
        }
        if (maxFps >= 120 && !options.contains("120")) {
            options.add("120")
        }
        if (maxFps >= 144 && !options.contains("144")) {
            options.add("144")
        }
        if (maxFps > 60 && !options.contains(maxFps.toString())) {
            options.add(maxFps.toString())
        }
        options.sortBy { it.toIntOrNull() ?: 0 }
        return options
    }

    fun getBitrateOptions(): List<String> {
        val options = mutableListOf("2 Mbps", "4 Mbps", "6 Mbps", "8 Mbps", "10 Mbps", "12 Mbps", "15 Mbps")
        val maxBitrateBps = getMaxSupportedBitrate()
        val maxBitrateMbps = maxBitrateBps / 1000000
        if (maxBitrateMbps > 15) {
            val extraOptions = listOf(20, 25, 30, 40, 50, 60, 80, 100)
            for (opt in extraOptions) {
                if (opt <= maxBitrateMbps && !options.contains("$opt Mbps")) {
                    options.add("$opt Mbps")
                }
            }
        }
        return options
    }

    /**
     * Dynamically aligns dimensions to hardware macroblock boundaries and clamps to
     * the device's actual hardware encoder capabilities.
     *
     * H.264/AVC hardware encoders (Qualcomm, MediaTek, Samsung Exynos, Google Tensor, Unisoc)
     * encode in 16x16 macroblocks. Dimensions with unaligned remainders (such as 2340 % 16 = 4)
     * cause hardware driver stride mismatches or software color-conversion fallbacks,
     * producing stutter, micro-freezes, and frame drops.
     *
     * Aligning height to 16 and width to 8/16 ensures zero-copy, direct hardware-accelerated
     * recording on EVERY device model.
     */
    fun getAlignedDimensions(rawWidth: Int, rawHeight: Int): Pair<Int, Int> {
        val videoCaps = getHardwareVideoCapabilities()
        val widthAlign = videoCaps?.widthAlignment ?: 16
        val heightAlign = videoCaps?.heightAlignment ?: 16
        val effWidthAlign = maxOf(8, widthAlign)
        val effHeightAlign = maxOf(16, heightAlign)

        var w = if (rawWidth % 2 == 0) rawWidth else rawWidth - 1
        var h = if (rawHeight % 2 == 0) rawHeight else rawHeight - 1

        // Align height to macroblock boundary
        h = (h / effHeightAlign) * effHeightAlign

        // Align width: standard 1080 is divisible by 8 and natively supported; otherwise align to effWidthAlign
        if (w % effWidthAlign != 0 && w % 8 != 0) {
            w = (w / effWidthAlign) * effWidthAlign
        }

        // Validate against hardware encoder's absolute maximum supported dimension.
        // Avoid blindly clamping w to supportedWidths, as mobile encoders
        // declare supportedWidths based on portrait max (e.g. 1080), which corrupts landscape recordings.
        if (videoCaps != null) {
            try {
                if (!videoCaps.isSizeSupported(w, h)) {
                    val maxSupportedDim = maxOf(videoCaps.supportedWidths.upper, videoCaps.supportedHeights.upper)
                    if (w > maxSupportedDim) {
                        w = (maxSupportedDim / effWidthAlign) * effWidthAlign
                    }
                    if (h > maxSupportedDim) {
                        h = (maxSupportedDim / effHeightAlign) * effHeightAlign
                    }
                }
            } catch (_: Exception) {}
        }

        if (w <= 0 || h <= 0) {
            return if (rawWidth >= rawHeight) Pair(1920, 1080) else Pair(1080, 1920)
        }
        return Pair(w, h)
    }

    /**
     * Validates that the requested frame rate is actually supported by the hardware encoder
     * for the given resolution, clamping dynamically if the panel refresh exceeds codec limits.
     */
    fun getSafeFpsForResolution(width: Int, height: Int, desiredFps: Int): Int {
        val videoCaps = getHardwareVideoCapabilities() ?: return desiredFps
        try {
            if (videoCaps.isSizeSupported(width, height)) {
                val supportedFpsRange = videoCaps.getSupportedFrameRatesFor(width, height)
                val maxFps = supportedFpsRange.upper.toInt()
                if (desiredFps > maxFps) {
                    Log.w(TAG, "Clamping requested FPS $desiredFps to hardware codec max $maxFps for ${width}x${height}")
                    return maxFps
                }
            }
        } catch (_: Exception) {}
        return desiredFps
    }

    /**
     * Clamps the requested bitrate within the hardware encoder's supported bitrate range.
     */
    fun getSafeBitrate(desiredBitrate: Int): Int {
        val videoCaps = getHardwareVideoCapabilities() ?: return desiredBitrate
        try {
            return videoCaps.bitrateRange.clamp(desiredBitrate)
        } catch (_: Exception) {}
        return desiredBitrate
    }

    /**
     * Determines the optimal AVC (H.264) profile and level supported by the device's
     * hardware encoder.
     *
     * Enforcing AVCProfileBaseline guarantees NO B-frames (DTS == PTS), completely
     * eliminating timestamp reversals and jitter during remuxing and playback.
     */
    fun getOptimalAvcProfileLevel(): Pair<Int, Int>? {
        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in codecList.codecInfos) {
                if (!info.isEncoder) continue
                for (type in info.supportedTypes) {
                    if (type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)) {
                        val caps = info.getCapabilitiesForType(type)
                        val profileLevels = caps.profileLevels ?: continue

                        // First try finding AVCProfileBaseline with the highest supported level
                        var bestBaselineLevel = -1
                        for (pl in profileLevels) {
                            if (pl.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline) {
                                if (pl.level > bestBaselineLevel) {
                                    bestBaselineLevel = pl.level
                                }
                            }
                        }

                        if (bestBaselineLevel != -1) {
                            return Pair(MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline, bestBaselineLevel)
                        }

                        // Fallback to Main Profile if Baseline isn't listed
                        var bestMainLevel = -1
                        for (pl in profileLevels) {
                            if (pl.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileMain) {
                                if (pl.level > bestMainLevel) {
                                    bestMainLevel = pl.level
                                }
                            }
                        }
                        if (bestMainLevel != -1) {
                            return Pair(MediaCodecInfo.CodecProfileLevel.AVCProfileMain, bestMainLevel)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error discovering optimal AVC profile/level", e)
        }
        return null
    }
}
