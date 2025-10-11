package com.dar.checker

import android.app.Application
import android.util.Log
import com.dar.checker.network.HealthServer

class CheckerApp : Application() {
    private var server: HealthServer? = null

    override fun onCreate() {
        super.onCreate()
        try {
            server = HealthServer(8080).also { it.start() }
            Log.i(TAG, "Health server started on :8080")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start health server", t)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        try {
            server?.stop()
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val TAG = "CheckerApp"
    }
}


