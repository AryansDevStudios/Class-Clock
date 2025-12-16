package com.aryansdevstudios.classclock

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class Period(val subject: String, val startTime: String, val endTime: String)

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams

    // UI References
    private lateinit var periodName: TextView
    private lateinit var timeRemainingValue: TextView
    private lateinit var timeLabel: TextView
    private lateinit var overlayText: TextView
    private lateinit var timeProgress: ProgressBar

    // Data
    private val timetable: List<Period> = listOf(
        Period("MATHEMATICS", "09:00", "10:00"),
        Period("SCIENCE", "10:00", "11:00"),
        Period("HISTORY", "11:00", "12:00"),
        Period("LUNCH", "12:00", "12:30"),
        Period("ENGLISH", "12:30", "13:30")
    )

    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun run() {
            updateOverlayText()
            handler.postDelayed(this, 1000)
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    handler.post(updateRunnable)
                }
            } else {
                Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            windowManager.removeView(overlayView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.overlay_channel_description)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.overlay_notification_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("InflateParams")
    private fun showOverlay() {
        // Ensure you are inflating the correct XML layout file name
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_view, null)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Bind Views
        periodName = overlayView.findViewById(R.id.periodName)
        timeRemainingValue = overlayView.findViewById(R.id.timeRemainingValue)
        timeLabel = overlayView.findViewById(R.id.timeLabel)
        overlayText = overlayView.findViewById(R.id.overlayText)
        timeProgress = overlayView.findViewById(R.id.timeProgress)

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
            // Position it top-right or center for the big board
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            y = 100 // Slight padding from top
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

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateOverlayText() {
        val currentTime = LocalTime.now()
        val currentPeriod = findCurrentPeriod(currentTime)

        if (currentPeriod != null) {
            val startTime = LocalTime.parse(currentPeriod.startTime)
            val endTime = LocalTime.parse(currentPeriod.endTime)

            val totalSeconds = ChronoUnit.SECONDS.between(startTime, endTime)
            val remainingSeconds = ChronoUnit.SECONDS.between(currentTime, endTime)
            val elapsedMinutes = ChronoUnit.MINUTES.between(startTime, currentTime)

            periodName.text = currentPeriod.subject
            periodName.setTextColor(Color.parseColor("#B0BEC5")) // Reset color

            if (remainingSeconds >= 0) {
                // Formatting time as MM:SS for precision
                val minutes = remainingSeconds / 60
                val seconds = remainingSeconds % 60
                timeRemainingValue.text = String.format(Locale.US, "%02d:%02d", minutes, seconds)

                timeLabel.text = "REMAINING"
                timeLabel.setTextColor(Color.parseColor("#90FFFFFF"))

                // Color Logic: Green -> Yellow -> Red
                when {
                    remainingSeconds < 600 -> timeRemainingValue.setTextColor(Color.parseColor("#FF5252")) // Red < 10m
                    remainingSeconds < 1200 -> timeRemainingValue.setTextColor(Color.parseColor("#FFD740")) // Yellow < 20m
                    else -> timeRemainingValue.setTextColor(Color.WHITE)
                }

                overlayText.text = "$elapsedMinutes min elapsed • Ends ${currentPeriod.endTime}"

                timeProgress.max = totalSeconds.toInt()
                timeProgress.progress = (totalSeconds - remainingSeconds).toInt()

            } else {
                // OVERTIME LOGIC
                val overtimeMinutes = (-remainingSeconds + 59) / 60
                val overtimeSeconds = -remainingSeconds % 60

                timeRemainingValue.text = String.format(Locale.US, "+%02d:%02d", overtimeMinutes, overtimeSeconds)
                timeRemainingValue.setTextColor(Color.parseColor("#FF5252")) // Red

                timeLabel.text = "OVERTIME"
                timeLabel.setTextColor(Color.parseColor("#FF5252"))

                periodName.setTextColor(Color.parseColor("#FF5252")) // Alert effect

                overlayText.text = "Class has ended"
                timeProgress.progress = timeProgress.max // Full bar
            }
        } else {
            // NO CLASS LOGIC
            periodName.text = "NO CLASS"
            timeRemainingValue.text = currentTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            timeRemainingValue.setTextColor(Color.GRAY)
            timeLabel.text = "CURRENT TIME"
            overlayText.text = "Free Period"
            timeProgress.progress = 0
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun findCurrentPeriod(currentTime: LocalTime): Period? {
        return timetable.find {
            val startTime = LocalTime.parse(it.startTime)
            val endTime = LocalTime.parse(it.endTime)
            // Inclusive start, exclusive end logic
            (!currentTime.isBefore(startTime) && currentTime.isBefore(endTime)) ||
                    // Optional: Handle overlapping seconds if strictly needed
                    (currentTime == startTime)
        }
    }
}