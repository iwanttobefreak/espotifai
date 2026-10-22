package com.espotifai.app.ui

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "--:--"
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

fun formatDuration(seconds: Int): String = formatDuration(seconds.toLong())

fun formatDurationMs(ms: Long): String = formatDuration(ms / 1000)
