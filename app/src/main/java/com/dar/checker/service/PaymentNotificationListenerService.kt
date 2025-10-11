package com.dar.checker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dar.checker.MainActivity
import com.dar.checker.R
import com.dar.checker.data.SettingsRepository
import com.dar.checker.data.NotificationStore
import com.dar.checker.data.StoredNotification
import com.dar.checker.data.SentStore
import com.dar.checker.data.SentRecord
import com.dar.checker.logging.LogBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class PaymentNotificationListenerService : NotificationListenerService() {

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val httpClient by lazy { OkHttpClient() }
    private val settings by lazy { SettingsRepository(applicationContext) }
    private val store by lazy { NotificationStore(applicationContext) }
    private val sent by lazy { SentStore(applicationContext) }
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundService()
        acquireWakeLock()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Payment Listener",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors payment notifications"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Payment Listener Active")
            .setContentText("Monitoring notifications in background")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(FOREGROUND_ID, notification)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PaymentListener::WakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // 10 minutes max
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        LogBus.log("Notificación recibida de paquete:" + sbn.packageName)
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val packageName = sbn.packageName
        store.add(
            StoredNotification(
                packageName = packageName,
                title = title,
                text = text,
                bigText = bigText,
                postTime = sbn.postTime
            )
        )
        LogBus.log("Guardada notificación de " + packageName)

        // Basic heuristic: look for payment-related keywords. User can refine filter later.
        val contentJoined = listOf(title, text, bigText).joinToString(" ").lowercase()
        val seemsPayment =
            listOf("pago", "pagaste", "recibiste", "payment", "paid", "deposito", "depósito")
                .any { it in contentJoined }

        // Log all notifications
        LogBus.log("Titulo: " + title + " Texto: " + text)

        ioScope.launch {
            try {
                val allowed = settings.getAllowedPackages()
                val filterEnabled = settings.isPackageFilterEnabled()
                if (filterEnabled && allowed.isNotEmpty() && !allowed.contains(packageName)) {
                    LogBus.log("Paquete no permitido, no se envía: " + packageName)
                    return@launch
                }

                val shouldSend = if (settings.isSendAllNotifications()) true else seemsPayment
                if (!shouldSend) return@launch

                val payloadText = if (bigText.isNotBlank()) bigText else text

                // De-duplicate: avoid sending the exact same payload repeatedly within a short window
                val dedupeKey = packageName + "|" + title + "|" + payloadText
                if (isDuplicate(dedupeKey, sbn.postTime)) {
                    LogBus.log("Notificación duplicada ignorada para paquete: " + packageName)
                    return@launch
                }

                val json = JSONObject().apply {
                    put("package", packageName)
                    put("title", title)
                    put("text", payloadText)
                    put("postTime", sbn.postTime)
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = json.toString().toRequestBody(mediaType)
                val base = settings.getServerHost().trimEnd('/')
                val request = Request.Builder()
                    .url(base + "/payment-received")
                    .post(body)
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val code = response.code
                        val respBody = response.body?.string().orEmpty()
                        Log.w(TAG, "POST failed: " + code + " " + respBody)
                        LogBus.log("POST " + base + "/payment-received falló: " + code + " " + respBody)
                    } else {
                        Log.i(TAG, "Payment notification forwarded")
                        LogBus.log("JSON enviado a " + base + "/payment-received")
                        sent.add(
                            SentRecord(
                                text = json.toString(),
                                url = base + "/payment-received",
                                code = response.code,
                                time = System.currentTimeMillis()
                            )
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error posting payment notification", t)
                LogBus.log("Error al enviar POST: " + t.message)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Listener connected")
        LogBus.log("Listener conectado")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Listener disconnected")
        LogBus.log("Listener desconectado")
    }

    companion object {
        private const val TAG = "PaymentNLService"
        private const val CHANNEL_ID = "payment_listener_channel"
        private const val FOREGROUND_ID = 1001
        private const val DEDUPE_WINDOW_MS: Long = 60_000 // 60s window

        // Simple in-memory recent map with pruning
        private val recentMap: java.util.LinkedHashMap<String, Long> = object : java.util.LinkedHashMap<String, Long>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return this.size > 200
            }
        }

        @Synchronized
        private fun isDuplicate(key: String, postTime: Long): Boolean {
            val now = System.currentTimeMillis()
            // prune old
            val iter = recentMap.entries.iterator()
            while (iter.hasNext()) {
                val e = iter.next()
                if (now - e.value > DEDUPE_WINDOW_MS) iter.remove()
            }
            val last = recentMap[key]
            return if (last != null && (now - last) <= DEDUPE_WINDOW_MS) {
                true
            } else {
                recentMap[key] = now
                false
            }
        }
    }
}


