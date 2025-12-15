package com.aryansdevstudios.classclock

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams

    // Timetable data (can be moved to a separate data source later)
    private val timetable = listOf(
        Period("Maths", "09:00", "09:45"),
        Period("Science", "09:45", "10:30"),
        Period("History", "10:30", "11:15")
        // Add more periods as needed
    )

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateOverlayText()
            handler.postDelayed(this, 1000) // Update every second
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "class_clock_channel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        if (!::overlayView.isInitialized || !overlayView.isAttachedToWindow) {
            if (Settings.canDrawOverlays(this)) {
                showOverlay()
                handler.post(updateRunnable) // Start periodic updates
            } else {
                Toast.makeText(this, "Draw over other apps permission is required.", Toast.LENGTH_LONG).show()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable) // Stop updates
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            windowManager.removeView(overlayView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Class Clock Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification to keep the timetable overlay service running."
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Class Clock")
            .setContentText("Timetable overlay is active.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("InflateParams")
    private fun showOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_view, null)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        setupDragListener()
        windowManager.addView(overlayView, params)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListener() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        overlayView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun updateOverlayText() {
        val currentTime = LocalTime.now()
        val currentPeriod = findCurrentPeriod(currentTime)
        val textView = overlayView.findViewById<TextView>(R.id.overlayText)
        val periodName = overlayView.findViewById<TextView>(R.id.periodName)
        val timeRemaining = overlayView.findViewById<TextView>(R.id.timeRemaining)


        if (currentPeriod != null) {
            val endTime = LocalTime.parse(currentPeriod.endTime)
            val remainingMinutes = ChronoUnit.MINUTES.between(currentTime, endTime)
            val elapsedMinutes = ChronoUnit.MINUTES.between(LocalTime.parse(currentPeriod.startTime), currentTime)

            periodName.text = currentPeriod.subject
            timeRemaining.text = "Remaining: ${remainingMinutes}min"
            textView.text = "Elapsed: ${elapsedMinutes}min"

            timeRemaining.setTextColor(if (remainingMinutes < 0) 0xFFFF0000.toInt() else 0xFFFFFFFF.toInt())
            if(remainingMinutes < 0) {
                timeRemaining.text = "OVERTIME: ${-remainingMinutes}min"
            }

        } else {
            periodName.text = "No period"
            timeRemaining.text = ""
            textView.text = currentTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        }
    }

    private fun findCurrentPeriod(currentTime: LocalTime): Period? {
        return timetable.find {
            val startTime = LocalTime.parse(it.startTime)
            val endTime = LocalTime.parse(it.endTime)
            !currentTime.isBefore(startTime) && currentTime.isBefore(endTime)
        }
    }
}

data class Period(val subject: String, val startTime: String, val endTime: String)