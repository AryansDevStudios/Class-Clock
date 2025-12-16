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
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale


class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams

    // UI References
    private lateinit var currentTime: TextView
    private lateinit var periodName: TextView
    private lateinit var timeRemainingLabel: TextView
    private lateinit var timeRemainingValue: TextView
    private lateinit var timeElapsedLabel: TextView
    private lateinit var timeElapsedValue: TextView
    private lateinit var timeProgress: ProgressBar

    // Data
    private var timetableResponse: TimetableResponse? = null
    private var lastPeriodName: String? = null
    private var isCriticalPulseRunning = false

    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        @RequiresApi(Build.VERSION_CODES.O)
        override fun run() {
            updateOverlayText()
            updateHandler.postDelayed(this, 1000)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "class_clock_channel"
        private const val TAG = "OverlayService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: Service creating.")
        loadTimetable()
        if (Settings.canDrawOverlays(this)) {
            showOverlay()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                updateHandler.post(updateRunnable)
            }
        } else {
            Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        updateHandler.removeCallbacks(updateRunnable)
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) {
            windowManager.removeView(overlayView)
        }
        Log.d(TAG, "onDestroy: Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Overlay Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Class Clock is running")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("InflateParams")
    private fun showOverlay() {
        if (::overlayView.isInitialized && overlayView.isAttachedToWindow) return
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_view, null)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Bind Views
        currentTime = overlayView.findViewById(R.id.currentTime)
        periodName = overlayView.findViewById(R.id.periodName)
        timeRemainingLabel = overlayView.findViewById(R.id.timeRemainingLabel)
        timeRemainingValue = overlayView.findViewById(R.id.timeRemainingValue)
        timeElapsedLabel = overlayView.findViewById(R.id.timeElapsedLabel)
        timeElapsedValue = overlayView.findViewById(R.id.timeElapsedValue)
        timeProgress = overlayView.findViewById(R.id.timeProgress)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            y = 100
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
        val nowTime = LocalTime.now()
        val nowDate = LocalDate.now()

        // 1. Update Top Clock
        val twelveHourFormatter = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.US)
        currentTime.text = nowTime.format(twelveHourFormatter)

        // 2. Determine Day and Schedule
        val currentDayName = nowDate.dayOfWeek.name // Returns "MONDAY", "TUESDAY", etc.

        val todaysSchedule = timetableResponse?.timetables?.find {
            it.dayOfWeek.equals(currentDayName, ignoreCase = true)
        }

        if (todaysSchedule == null) {
            setNoClassState("NO SCHEDULE")
            return
        }

        // 3. Find Active Period
        var activePeriod: Period? = null

        for (period in todaysSchedule.periods) {
            try {
                // Parse "08:00" to LocalTime
                val start = LocalTime.parse(period.startTime)
                val end = LocalTime.parse(period.endTime)

                // Check if current time is within this period
                // (start <= now < end)
                if ((nowTime == start || nowTime.isAfter(start)) && nowTime.isBefore(end)) {
                    activePeriod = period

                    // Perform Calculations
                    val totalDurationSeconds = ChronoUnit.SECONDS.between(start, end)
                    val elapsedSeconds = ChronoUnit.SECONDS.between(start, nowTime)
                    val remainingSeconds = totalDurationSeconds - elapsedSeconds

                    // Prevent division by zero
                    val progress = if (totalDurationSeconds > 0) {
                        ((elapsedSeconds.toFloat() / totalDurationSeconds.toFloat()) * 100).toInt()
                    } else {
                        0
                    }

                    updateActiveUI(
                        name = period.periodName,
                        remaining = formatSeconds(remainingSeconds),
                        elapsed = formatSeconds(elapsedSeconds),
                        progress = progress
                    )
                    return // Found the period, exit loop
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing time for period: ${'$'}{period.periodName}", e)
            }
        }

        // If loop finishes and activePeriod is still null, we are in free time (break or after school)
        if (activePeriod == null) {
            setNoClassState("FREE TIME")
        }
    }

    // Helper to format MM:SS or HH:MM:SS
    private fun formatSeconds(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun animatePeriodChange() {
        val anim = AnimationUtils.loadAnimation(this, R.anim.period_change)
        periodName.startAnimation(anim)
        timeRemainingValue.startAnimation(anim)
    }

    private fun updateRemainingTimeColor(secondsLeft: Long) {
        val color = when {
            secondsLeft <= 60 -> Color.parseColor("#FF5252")   // red
            secondsLeft <= 120 -> Color.parseColor("#FFB74D") // orange
            else -> Color.WHITE
        }

        timeRemainingValue.setTextColor(color)

        if (secondsLeft <= 60) {
            startCriticalPulse()
        } else {
            stopCriticalPulse()
        }
    }

    private fun startCriticalPulse() {
        if (isCriticalPulseRunning) return
        isCriticalPulseRunning = true

        timeRemainingValue.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(400)
            .withEndAction {
                timeRemainingValue.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(400)
                    .withEndAction {
                        isCriticalPulseRunning = false
                    }
                    .start()
            }
            .start()
    }

    private fun stopCriticalPulse() {
        isCriticalPulseRunning = false
        timeRemainingValue.animate().cancel()
        timeRemainingValue.scaleX = 1f
        timeRemainingValue.scaleY = 1f
    }

    private fun updateActiveUI(
        name: String,
        remaining: String,
        elapsed: String,
        progress: Int
    ) {
        // Animate only if period changed
        if (lastPeriodName != name) {
            lastPeriodName = name
            animatePeriodChange()
        }

        periodName.text = name
        timeRemainingValue.text = remaining
        timeElapsedValue.text = elapsed
        timeProgress.progress = progress

        // Convert remaining time back to seconds for warning logic
        val parts = remaining.split(":").map { it.toIntOrNull() ?: 0 }
        val remainingSeconds = if (parts.size == 3) {
            parts[0] * 3600 + parts[1] * 60 + parts[2]
        } else {
            parts[0] * 60 + parts[1]
        }

        updateRemainingTimeColor(remainingSeconds.toLong())

        // Ensure visibility
        if (timeRemainingLabel.visibility != View.VISIBLE) {
            timeRemainingLabel.visibility = View.VISIBLE
            timeRemainingValue.visibility = View.VISIBLE
            timeElapsedLabel.visibility = View.VISIBLE
            timeElapsedValue.visibility = View.VISIBLE
            timeProgress.visibility = View.VISIBLE
        }
    }

    private fun setNoClassState(message: String) {
        periodName.text = message
        lastPeriodName = null
        stopCriticalPulse()
        timeRemainingValue.setTextColor(Color.WHITE)

        // Hide details when no class is active
        timeRemainingLabel.visibility = View.GONE
        timeRemainingValue.visibility = View.GONE
        timeElapsedLabel.visibility = View.GONE
        timeElapsedValue.visibility = View.GONE
        timeProgress.visibility = View.GONE
    }

    private fun loadTimetable() {
        try {
            assets.open("timetable.json").use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    timetableResponse = Gson().fromJson(reader, TimetableResponse::class.java)
                    Log.d(TAG, "Timetable loaded successfully.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading timetable", e)
            // Just strictly for debugging if needed, otherwise Overlay might look broken
            periodName.text = "ERROR LOADING"
        }
    }
}
