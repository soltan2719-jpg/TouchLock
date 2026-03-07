package com.example.touchlock

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.example.touchlock.databinding.OverlayLockBinding
import com.example.touchlock.TouchLockState
import kotlin.math.sqrt

class TouchLockService : Service(), SensorEventListener {

    private val prefs by lazy { getSharedPreferences(Prefs.NAME, MODE_PRIVATE) }

    private var wm: WindowManager? = null
    private var overlayView: View? = null
    private var binding: OverlayLockBinding? = null

    // Double tap detection
    private var lastTapTime = 0L

    // Timer auto-unlock
    private val handler = Handler(Looper.getMainLooper())
    private val autoUnlockRunnable = Runnable { stopSelf() }

    // Shake unlock
    private var sensorManager: SensorManager? = null
    private var accel: Sensor? = null
    private var lastShakeTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        showOverlay()
        applyAutoUnlockTimer()
        maybeStartShakeListener()
        TouchLockState.setLocked(this, true)
    }

    override fun onDestroy() {
        removeOverlay()
        stopShakeListener()
        handler.removeCallbacks(autoUnlockRunnable)
        TouchLockState.setLocked(this, false)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UNLOCK -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        val b = OverlayLockBinding.inflate(inflater)
        binding = b

        // Settings
        val dim = prefs.getInt(Prefs.DIM_PERCENT, 20).coerceIn(0, 80)
        val showBtn = prefs.getBoolean(Prefs.SHOW_UNLOCK_BTN, true)
        val doubleTap = prefs.getBoolean(Prefs.DOUBLE_TAP_UNLOCK, true)
        val edgeHandle = prefs.getBoolean(Prefs.EDGE_HANDLE_UNLOCK, true)

        b.dimLayer.alpha = dim / 100f
        b.unlockButton.visibility = if (showBtn) View.VISIBLE else View.GONE
        b.edgeHandle.visibility = if (edgeHandle) View.VISIBLE else View.GONE

        // Consume ALL touches
        b.root.setOnTouchListener { _, event ->
            if (doubleTap && event.action == MotionEvent.ACTION_DOWN) {
                val now = System.currentTimeMillis()
                if (now - lastTapTime < 300) {
                    stopSelf()
                    return@setOnTouchListener true
                }
                lastTapTime = now
            }
            true
        }

        // Long press unlock button
        b.unlockButton.setOnLongClickListener {
            stopSelf()
            true
        }

        // Edge handle: long press
        b.edgeHandle.setOnLongClickListener {
            stopSelf()
            true
        }

        // Text hint
        b.txtHint.text = buildString {
            append("Touch is LOCKED\n")
            if (doubleTap) append("• Double-tap anywhere to unlock\n")
            if (edgeHandle) append("• Long-press edge handle to unlock\n")
            if (showBtn) append("• Long-press UNLOCK button\n")
            append("• Or use notification UNLOCK")
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START

        overlayView = b.root
        wm?.addView(overlayView, params)
    }

    private fun removeOverlay() {
        try {
            overlayView?.let { wm?.removeView(it) }
        } catch (_: Exception) {
        } finally {
            overlayView = null
            binding = null
        }
    }

    private fun applyAutoUnlockTimer() {
        handler.removeCallbacks(autoUnlockRunnable)

        val index = prefs.getInt(Prefs.TIMER_INDEX, 0).coerceIn(0, 4)
        val ms = when (index) {
            1 -> 30_000L
            2 -> 60_000L
            3 -> 5 * 60_000L
            4 -> 10 * 60_000L
            else -> 0L
        }

        if (ms > 0) {
            handler.postDelayed(autoUnlockRunnable, ms)
        }
    }

    private fun maybeStartShakeListener() {
        val shakeEnabled = prefs.getBoolean(Prefs.SHAKE_UNLOCK, false)
        if (!shakeEnabled) return

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accel?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun stopShakeListener() {
        sensorManager?.unregisterListener(this)
        sensorManager = null
        accel = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        // Simple shake detection
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val g = sqrt((x * x + y * y + z * z).toDouble())

        // Typically around 9.8 when steady; shake can spike
        val now = System.currentTimeMillis()
        val cooldown = 900L

        if (g > 17 && now - lastShakeTime > cooldown) {
            lastShakeTime = now
            stopSelf()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun buildNotification(): Notification {
        createNotificationChannel()

        val unlockIntent = Intent(this, TouchLockService::class.java).apply { action = ACTION_UNLOCK }
        val unlockPending = PendingIntent.getService(
            this,
            1,
            unlockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPending = PendingIntent.getActivity(
            this,
            2,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Touch Lock is ON")
            .setContentText("Tap UNLOCK to stop blocking touches.")
            .setContentIntent(openAppPending)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "UNLOCK", unlockPending)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Touch Lock",
            NotificationManager.IMPORTANCE_LOW
        )
        channel.setSound(null, null)
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "touch_lock_channel"
        private const val NOTIF_ID = 1001
        private const val ACTION_UNLOCK = "ACTION_UNLOCK"

        fun start(context: Context) {
            val i = Intent(context, TouchLockService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TouchLockService::class.java))
        }
    }
}