package com.example.touchlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class TouchLockService : Service() {

    companion object {
        const val ACTION_START = "com.example.touchlock.START"
        const val ACTION_STOP = "com.example.touchlock.STOP"

        private const val CHANNEL_ID = "touch_lock_channel"
        private const val NOTIFICATION_ID = 101

        @Volatile
        var isRunning = false
    }

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var lockOverlay: View? = null
    private var unlockButton: View? = null
    private var pinOverlay: View? = null

    private var lastTapTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                startLockWithDelay()
            }

            ACTION_STOP -> {
                stopLock()
            }

            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                startLockWithDelay()
            }
        }
        return START_STICKY
    }

    private fun startLockWithDelay() {
        removeOverlays()

        val delaySeconds = Prefs.getAutoLockDelay(this)
        if (delaySeconds <= 0) {
            showLockOverlay()
            return
        }

        val overlayType = getOverlayType()

        val armingOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#66000000"))
            isClickable = true
            isFocusable = false

            val textView = TextView(this@TouchLockService).apply {
                text = "Touch lock will start in $delaySeconds second(s)..."
                textSize = 20f
                setTextColor(Color.WHITE)
                setPadding(40, 40, 40, 40)
                setBackgroundColor(Color.parseColor("#CC111111"))
            }

            addView(
                textView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START

        windowManager.addView(armingOverlay, params)
        lockOverlay = armingOverlay

        handler.postDelayed({
            removeOverlays()
            showLockOverlay()
        }, delaySeconds * 1000L)

        isRunning = true
    }

    private fun showLockOverlay() {
        if (lockOverlay != null) return

        val overlayType = getOverlayType()

        val fullOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#01000000"))

            setOnTouchListener { _, event ->
                if (Prefs.isDoubleTapEnabled(this@TouchLockService) &&
                    event.action == MotionEvent.ACTION_UP
                ) {
                    handleDoubleTap()
                }
                true
            }
        }

        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        overlayParams.gravity = Gravity.TOP or Gravity.START

        windowManager.addView(fullOverlay, overlayParams)
        lockOverlay = fullOverlay

        if (Prefs.isShowUnlockButton(this)) {
            val button = Button(this).apply {
                text = "Unlock"
                textSize = 16f
                setPadding(30, 20, 30, 20)
                setBackgroundColor(Color.parseColor("#CC111111"))
                setTextColor(Color.WHITE)

                setOnClickListener {
                    requestUnlock()
                }
            }

            val buttonParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            buttonParams.gravity = Gravity.TOP or Gravity.END
            buttonParams.x = 40
            buttonParams.y = 120

            windowManager.addView(button, buttonParams)
            unlockButton = button
        }

        isRunning = true
    }

    private fun handleDoubleTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime < 300) {
            requestUnlock()
        }
        lastTapTime = now
    }

    private fun requestUnlock() {
        if (Prefs.isPinRequired(this)) {
            showPinOverlay()
        } else {
            stopLock()
        }
    }

    private fun showPinOverlay() {
        if (pinOverlay != null) return

        val overlayType = getOverlayType()

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#66000000"))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            setBackgroundColor(Color.parseColor("#FF1A1A1A"))
        }

        val title = TextView(this).apply {
            text = "Enter PIN to Unlock"
            textSize = 20f
            setTextColor(Color.WHITE)
        }

        val input = EditText(this).apply {
            hint = "PIN"
            setHintTextColor(Color.parseColor("#AAAAAA"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }

        val info = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(Color.RED)
        }

        val rowButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnCancel = Button(this).apply {
            text = "Cancel"
        }

        val btnUnlock = Button(this).apply {
            text = "Unlock"
        }

        rowButtons.addView(
            btnCancel,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        rowButtons.addView(
            btnUnlock,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        card.addView(title)
        card.addView(input)
        card.addView(info)
        card.addView(rowButtons)

        root.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                leftMargin = 50
                rightMargin = 50
            }
        )

        val pinParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        pinParams.gravity = Gravity.CENTER
        pinParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE

        windowManager.addView(root, pinParams)
        pinOverlay = root

        input.requestFocus()

        handler.postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 200)

        btnCancel.setOnClickListener {
            removePinOverlay()
        }

        btnUnlock.setOnClickListener {
            val entered = input.text.toString().trim()
            val realPin = Prefs.getPin(this)

            if (entered == realPin) {
                stopLock()
            } else {
                info.text = "Wrong PIN"
            }
        }
    }

    private fun removePinOverlay() {
        pinOverlay?.let {
            windowManager.removeView(it)
            pinOverlay = null
        }
    }

    private fun stopLock() {
        handler.removeCallbacksAndMessages(null)
        removeOverlays()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        isRunning = false
    }

    private fun removeOverlays() {
        try {
            lockOverlay?.let {
                windowManager.removeView(it)
            }
        } catch (_: Exception) {
        }
        lockOverlay = null

        try {
            unlockButton?.let {
                windowManager.removeView(it)
            }
        } catch (_: Exception) {
        }
        unlockButton = null

        try {
            pinOverlay?.let {
                windowManager.removeView(it)
            }
        } catch (_: Exception) {
        }
        pinOverlay = null
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, TouchLockService::class.java).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag()
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag()
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TouchLock is active")
            .setContentText("Touches are blocked")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Touch Lock",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun getOverlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun pendingIntentImmutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }

    override fun onDestroy() {
        removeOverlays()
        isRunning = false
        super.onDestroy()
    }
}