package com.dar.checker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dar.checker.logging.LogBus

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            // App components (Application) will be created on demand when app is started.
            LogBus.log("Dispositivo reiniciado: BootReceiver activo")
        }
    }
}


