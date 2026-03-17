package com.example.touchlock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import kotlin.math.sqrt

class TouchLockService : Service() {

    companion object {
        const val ACTION_START_LOCK_NOW = "com.example.touchlock.START_LOCK_NOW"
        const val ACTION_START_MONITOR = "com.example.touchlock.START_MONITOR"
        const val ACTION_STOP_ALL = "com.example.touchlock.STOP_ALL"

        private const val CHANNEL_ID = "touch_lock_channel"
        private const val NOTIFICATION_ID = 101

        @Volatile
        var isRunning = false

        @Volatile
        var isMonitoring = false

        @Volatile
        var isOverlayShowing = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var usageStatsManager: UsageStatsManager
    private val handler = Handler(Looper.getMainLooper())

    private var lockOverlay: View? = null
    private var touchFeedbackView: View? = null
    private var unlockButton: View? = null
    private var pinOverlay: View? = null
    private var infoOverlay: View? = null

    // Shake Detection Variables
    private var sensorManager: SensorManager? = null
    private var acceleration = 0f
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f

    private var lastTapTime = 0L
    private var unlockCooldownUntil = 0L

    private val monitorRunnable = object : Runnable {
        override fun run() {
            try {
                if (isMonitoring && !isOverlayShowing && System.currentTimeMillis() >= unlockCooldownUntil) {
                    val currentPackage = getForegroundPackage()
                    val selectedPackages = Prefs.getSelectedPackages(this@TouchLockService)

                    if (!currentPackage.isNullOrEmpty() && selectedPackages.contains(currentPackage)) {
                        startLockWithDelay()
                    }
                }
            } catch (_: Exception) {
            }

            if (isMonitoring) {
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        createNotificationChannel()

        // Initialize Shake Sensor
        setupShakeSensor()
    }

    private fun setupShakeSensor() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager?.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)

        acceleration = 10f
        currentAcceleration = SensorManager.GRAVITY_EARTH
        lastAcceleration = SensorManager.GRAVITY_EARTH
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            lastAcceleration = currentAcceleration
            currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = currentAcceleration - lastAcceleration
            acceleration = acceleration * 0.9f + delta

            // If shake is strong enough and screen is NOT already locked
            if (acceleration > 12 && !isOverlayShowing) {
                startLockWithDelay()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        when (intent?.action) {
            ACTION_START_LOCK_NOW -> {
                isMonitoring = false
                handler.removeCallbacks(monitorRunnable)
                startLockWithDelay()
            }
            ACTION_START_MONITOR -> startMonitorMode()
            ACTION_STOP_ALL -> stopAll()
            else -> startMonitorMode()
        }

        return START_STICKY
    }

    private fun startMonitorMode() {
        isRunning = true
        isMonitoring = true
        updateNotification()
        handler.removeCallbacks(monitorRunnable)
        handler.post(monitorRunnable)
    }

    private fun startLockWithDelay() {
        val delaySeconds = Prefs.getAutoLockDelay(this)
        if (delaySeconds <= 0) {
            showLockOverlay()
            return
        }

        removeInfoOverlay()

        val overlayType = getOverlayType()
        val armingView = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#55000000"))
            isClickable = true

            val text = TextView(this@TouchLockService).apply {
                text = "Touch lock starts in $delaySeconds second(s)..."
                setTextColor(Color.WHITE)
                textSize = 20f
                setPadding(40, 40, 40, 40)
                setBackgroundColor(Color.parseColor("#DD111111"))
            }

            addView(
                text,
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

        windowManager.addView(armingView, params)
        infoOverlay = armingView

        handler.postDelayed({
            removeInfoOverlay()
            showLockOverlay()
        }, delaySeconds * 1000L)
    }

    private fun showLockOverlay() {
        if (lockOverlay != null) return

        val overlayType = getOverlayType()

        val fullOverlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#01000000"))

            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    showTouchFeedback(event.rawX, event.rawY)
                }

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

        touchFeedbackView = TextView(this).apply {
            text = "🔒"
            textSize = 30f
            alpha = 0f
            gravity = Gravity.CENTER
        }

        val feedbackParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        feedbackParams.gravity = Gravity.TOP or Gravity.START
        windowManager.addView(touchFeedbackView, feedbackParams)

        isOverlayShowing = true
        isRunning = true
        updateNotification()

        addBottomHint(overlayType)

        if (Prefs.isShowUnlockButton(this)) {
            addUnlockButton(overlayType)
        }
    }

    private fun showTouchFeedback(x: Float, y: Float) {
        val view = touchFeedbackView ?: return

        val params = view.layoutParams as WindowManager.LayoutParams
        params.x = x.toInt() - 50
        params.y = y.toInt() - 100
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Exception) {}

        view.animate().cancel()
        view.alpha = 0.8f
        view.animate()
            .alpha(0f)
            .setDuration(1000)
            .start()
    }

    private fun addBottomHint(overlayType: Int) {
        val hint = TextView(this).apply {
            text = buildHintText()
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(26, 16, 26, 16)
            setBackgroundColor(Color.parseColor("#77000000"))
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        params.y = 70

        windowManager.addView(hint, params)
        infoOverlay = hint
    }

    private fun buildHintText(): String {
        val parts = mutableListOf<String>()
        if (Prefs.isShowUnlockButton(this)) parts.add("button")
        if (Prefs.isDoubleTapEnabled(this)) parts.add("double tap")
        if (Prefs.isPinRequired(this)) parts.add("PIN")
        parts.add("notification")
        return "Unlock: ${parts.joinToString(", ")}"
    }

    private fun addUnlockButton(overlayType: Int) {
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

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 40
        params.y = 120

        windowManager.addView(button, params)
        unlockButton = button
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
            unlockOverlayOnly()
        }
    }

    private fun showPinOverlay() {
        if (pinOverlay != null) return

        val overlayType = getOverlayType()

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#77000000"))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            setBackgroundColor(Color.parseColor("#FF1A1A1A"))
        }

        val title = TextView(this).apply {
            text = "Enter PIN to unlock"
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

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnCancel = Button(this).apply { text = "Cancel" }
        val btnUnlock = Button(this).apply { text = "Unlock" }

        row.addView(
            btnCancel,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        row.addView(
            btnUnlock,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        card.addView(title)
        card.addView(input)
        card.addView(info)
        card.addView(row)

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

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE

        windowManager.addView(root, params)
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
                unlockOverlayOnly()
            } else {
                info.text = "Wrong PIN"
            }
        }
    }

    private fun unlockOverlayOnly() {
        if (lockOverlay == null) return

        // Step 4: Smooth Fade-out Animation
        lockOverlay?.animate()
            ?.alpha(0f)
            ?.setDuration(300)
            ?.withEndAction {
                removeOverlayViews()
                removePinOverlay()
                unlockCooldownUntil = System.currentTimeMillis() + 5000
                isOverlayShowing = false
                isRunning = isMonitoring
                updateNotification()

                if (!isMonitoring) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    isRunning = false
                }
            }
            ?.start()
    }

    private fun stopAll() {
        handler.removeCallbacksAndMessages(null)
        isMonitoring = false
        isOverlayShowing = false
        isRunning = false
        removeOverlayViews()
        removePinOverlay()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun removeOverlayViews() {
        try {
            lockOverlay?.let { windowManager.removeView(it) }
        } catch (_: Exception) {
        }
        lockOverlay = null

        try {
            touchFeedbackView?.let { windowManager.removeView(it) }
        } catch (_: Exception) {
        }
        touchFeedbackView = null

        try {
            unlockButton?.let { windowManager.removeView(it) }
        } catch (_: Exception) {
        }
        unlockButton = null

        removeInfoOverlay()
    }

    private fun removeInfoOverlay() {
        try {
            infoOverlay?.let { windowManager.removeView(it) }
        } catch (_: Exception) {
        }
        infoOverlay = null
    }

    private fun removePinOverlay() {
        try {
            pinOverlay?.let { windowManager.removeView(it) }
        } catch (_: Exception) {
        }
        pinOverlay = null
    }

    private fun getForegroundPackage(): String? {
        val end = System.currentTimeMillis()
        val begin = end - 10_000L
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            begin,
            end
        )

        if (stats.isNullOrEmpty()) return null

        val recent = stats.maxByOrNull { it.lastTimeUsed } ?: return null
        return recent.packageName
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, TouchLockService::class.java).apply {
            action = ACTION_STOP_ALL
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

        val text = when {
            isMonitoring && isOverlayShowing -> "Monitoring selected apps • Touch lock active"
            isMonitoring -> "Monitoring selected apps"
            isOverlayShowing -> "Touch lock active"
            else -> "TouchLock running"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TouchLock")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "STOP", stopPendingIntent)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
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
            @Suppress("DEPRECATION")
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
        handler.removeCallbacksAndMessages(null)
        sensorManager?.unregisterListener(sensorListener) // Step 3 cleanup
        removeOverlayViews()
        removePinOverlay()
        isRunning = false
        isMonitoring = false
        isOverlayShowing = false
        super.onDestroy()
    }
    private fun sendStatusBroadcast() {
        val intent = Intent("com.example.touchlock.STATUS_CHANGED")
        sendBroadcast(intent)
    }
}