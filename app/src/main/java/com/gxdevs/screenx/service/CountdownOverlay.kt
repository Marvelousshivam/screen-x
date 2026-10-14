package com.gxdevs.screenx.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class CountdownOverlay(
    private val context: Context,
    private val totalSeconds: Int = 3,
    private val keepScreenAwake: Boolean = false,
    private val onTick: ((secondsRemaining: Int) -> Unit)? = null
) {
    companion object {
        private const val TAG = "CountdownOverlay"
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var containerView: FrameLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isDismissed = false
    private var tickRunnable: Runnable? = null

    fun show(onFinished: () -> Unit) {
        if (totalSeconds <= 0) {
            onFinished()
            return
        }

        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (keepScreenAwake) {
            flags = flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        }

        val layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            this.flags = flags
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }

        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#66000000")) // Semi-transparent overlay
        }
        containerView = container

        val textView = TextView(context).apply {
            text = totalSeconds.toString()
            setTextColor(Color.WHITE)
            textSize = 96f
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val textParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        container.addView(textView, textParams)

        var overlayAdded = false
        try {
            windowManager.addView(container, layoutParams)
            overlayAdded = true
        } catch (e: Exception) {
            Log.w(TAG, "Could not add overlay view (permission missing or denied): ${e.message}")
        }

        var count = totalSeconds
        val runnable = object : Runnable {
            override fun run() {
                if (isDismissed) return
                if (count > 0) {
                    if (overlayAdded) {
                        textView.text = count.toString()
                    }
                    onTick?.invoke(count)
                    count--
                    handler.postDelayed(this, 1000)
                } else {
                    dismiss()
                    onFinished()
                }
            }
        }
        tickRunnable = runnable
        handler.post(runnable)
    }

    fun dismiss() {
        if (isDismissed) return
        isDismissed = true
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
        containerView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing overlay view: ${e.message}")
            }
        }
        containerView = null
    }
}
