package com.dar.checker.logging

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogBus {
    private val _logs = MutableSharedFlow<String>(replay = 200, extraBufferCapacity = 100)
    val logs: SharedFlow<String> = _logs

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Volatile
    var enabled: Boolean = true

    fun log(message: String) {
        if (!enabled) return
        val ts = timeFormat.format(Date())
        _logs.tryEmit("[" + ts + "] " + message)
    }
}


