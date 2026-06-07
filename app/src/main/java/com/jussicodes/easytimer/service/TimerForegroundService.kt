package com.jussicodes.easytimer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jussicodes.easytimer.data.PreferencesRepository
import com.jussicodes.easytimer.MainActivity
import com.jussicodes.easytimer.model.TimerState
import com.jussicodes.easytimer.root.RootShellManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimerForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "easytimer_channel"
        const val NOTIFICATION_ID = 100

        const val ACTION_START = "START"
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_RESUME = "RESUME"
        const val ACTION_CANCEL = "CANCEL"
        const val ACTION_FORCE_STOP_NOW = "FORCE_STOP_NOW"
        const val ACTION_ADD_5_MIN = "ADD_5_MIN"

        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_APP_NAME = "app_name"
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
        const val EXTRA_TOTAL_SECONDS = "total_seconds"
        const val EXTRA_DURATION_SECONDS = "duration_seconds"
        const val EXTRA_SELF_DESTRUCT = "selfdestruct"

        private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
        val timerState: StateFlow<TimerState> = _timerState.asStateFlow()
    }

    private var countDownTimer: CountDownTimer? = null
    private var packageName: String = ""
    private var appName: String = ""
    private var totalSeconds: Int = 0
    private var remainingSeconds: Int = 0
    private var isPaused: Boolean = false
    private var selfDestruct: Boolean = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: PreferencesRepository

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
                appName = intent.getStringExtra(EXTRA_APP_NAME) ?: ""
                val minutes = intent.getIntExtra(EXTRA_DURATION_MINUTES, 15)
                remainingSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, minutes * 60)
                totalSeconds = intent.getIntExtra(EXTRA_TOTAL_SECONDS, remainingSeconds)
                isPaused = false
                selfDestruct = intent.getBooleanExtra(EXTRA_SELF_DESTRUCT, false)
                startCountdown()
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_CANCEL -> cancelTimer()
            ACTION_FORCE_STOP_NOW -> forceStopNow()
            ACTION_ADD_5_MIN -> add5Minutes()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCountdown() {
        countDownTimer?.cancel()
        persistActiveTimer()

        countDownTimer = object : CountDownTimer(remainingSeconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingSeconds = (millisUntilFinished / 1000).toInt()
                emitState()
                updateNotification()
            }

            override fun onFinish() {
                executeForceStop()
            }
        }.start()

        emitState()
        startForegroundWithNotification()
    }

    private fun pauseTimer() {
        if (!isPaused) {
            isPaused = true
            countDownTimer?.cancel()
            emitState()
            persistActiveTimer()
            updateNotification()
        }
    }

    private fun resumeTimer() {
        if (isPaused) {
            isPaused = false
            startCountdown()
        }
    }

    private fun add5Minutes() {
        if (isPaused || countDownTimer == null) return
        countDownTimer?.cancel()
        remainingSeconds += 300
        totalSeconds += 300
        startCountdown()
    }

    private fun cancelTimer() {
        countDownTimer?.cancel()
        _timerState.value = TimerState.Idle
        clearPersistedTimer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun forceStopNow() {
        countDownTimer?.cancel()
        executeForceStop()
    }

    private fun executeForceStop() {
        countDownTimer?.cancel()
        serviceScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    RootShellManager.forceStopApp(packageName)
                    killSelf()
                    try {
                        Runtime.getRuntime().exec(arrayOf("am", "force-stop", packageName)).waitFor()
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            _timerState.value = TimerState.Idle
            clearPersistedTimer()

            val closedNotification = NotificationCompat.Builder(this@TimerForegroundService, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("已关闭 $appName")
                .setContentText("定时关闭完成")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID + 1, closedNotification)

            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun emitState() {
        _timerState.value = TimerState.Running(
            packageName = packageName,
            appName = appName,
            totalSeconds = totalSeconds,
            remainingSeconds = remainingSeconds,
            isPaused = isPaused
        )
    }

    private fun persistActiveTimer() {
        val endAtMillis = if (isPaused) 0L else System.currentTimeMillis() + remainingSeconds * 1000L
        serviceScope.launch(Dispatchers.IO) {
            prefs.saveActiveTimer(
                packageName = packageName,
                appName = appName,
                totalSeconds = totalSeconds,
                remainingSeconds = remainingSeconds,
                isPaused = isPaused,
                endAtMillis = endAtMillis
            )
        }
    }

    private fun clearPersistedTimer() {
        serviceScope.launch(Dispatchers.IO) {
            prefs.clearActiveTimer()
        }
    }

    private fun killSelf() {
        if (selfDestruct) {
            RootShellManager.forceStopApp("com.jussicodes.easytimer")
            try {
                Runtime.getRuntime().exec(arrayOf("am", "force-stop", "com.jussicodes.easytimer")).waitFor()
            } catch (_: Exception) {}
        }
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: SecurityException) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val remainingText = formatTime(remainingSeconds)
        val title = if (isPaused) {
            "已暂停 · $appName"
        } else {
            "将在 ${formatMinutes(totalSeconds)} 后关闭 $appName"
        }
        val content = "剩余 $remainingText"

        val openIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)

        if (isPaused) {
            builder.addAction(android.R.drawable.ic_media_play, "继续", actionPendingIntent(ACTION_RESUME))
        } else {
            builder.addAction(android.R.drawable.ic_media_pause, "暂停", actionPendingIntent(ACTION_PAUSE))
        }
        builder.addAction(android.R.drawable.ic_menu_add, "加5分钟", actionPendingIntent(ACTION_ADD_5_MIN))
        builder.addAction(android.R.drawable.ic_delete, "取消", actionPendingIntent(ACTION_CANCEL))
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "立即关闭", actionPendingIntent(ACTION_FORCE_STOP_NOW))

        return builder.build()
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, TimerForegroundService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "定时器",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun formatTime(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "%02d:%02d".format(m, s)
    }

    private fun formatMinutes(totalSeconds: Int): String {
        return "${totalSeconds / 60} 分钟"
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }
}
