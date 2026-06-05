package com.jussicodes.easytimer.model

import android.graphics.Bitmap

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Bitmap? = null
)

enum class Screen {
    HOME,
    TIMER_SETUP,
    ACTIVE_TIMER,
    SETTINGS
}

sealed class TimerState {
    data object Idle : TimerState()
    data class Running(
        val packageName: String,
        val appName: String,
        val totalSeconds: Int,
        val remainingSeconds: Int,
        val isPaused: Boolean = false
    ) : TimerState()
}
