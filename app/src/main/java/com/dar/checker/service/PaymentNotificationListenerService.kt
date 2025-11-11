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
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.dar.checker.MainActivity
import com.dar.checker.R
import com.dar.checker.data.SettingsRepository
import com.dar.checker.data.NotificationStore
import com.dar.checker.data.StoredNotification
import com.dar.checker.data.SentStore
import com.dar.checker.data.SentRecord
import com.dar.checker.logging.LogBus
import com.dar.checker.network.MqttClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class PaymentNotificationListenerService : NotificationListenerService() {

    private val ioScope = CoroutineScope(Dispatchers.IO)
    private val mqttClient by lazy { MqttClient(applicationContext) }
    private val settings by lazy { SettingsRepository(applicationContext) }
    private val store by lazy { NotificationStore(applicationContext) }
    private val sent by lazy { SentStore(applicationContext) }
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundService()
        acquireWakeLock()
        
        // Conectar MQTT al inicio y mantener conexión persistente
        ioScope.launch {
            connectMqttWithRetry()
            startMqttKeepAlive()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        mqttClient.disconnect()
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
            acquire(10 * 60 * 60 * 1000L) // 10 horas max - mantener activo por horas
        }
        LogBus.log("Wake lock adquirido por 10 horas para monitoreo continuo")
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
        
        // Detección múltiple para Yape - verificar todas las variantes posibles
        val isYapeNotification = packageName == "com.bcp.bo.wallet" || 
                                packageName.contains("yape") || 
                                packageName.contains("bcp") ||
                                title.contains("yape", ignoreCase = true) ||
                                text.contains("yape", ignoreCase = true) ||
                                bigText.contains("yape", ignoreCase = true)
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
            listOf("pago", "pagaste", "recibiste", "payment", "paid", "deposito", "depósito", "yape", "transferencia", "dinero")
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

                // Priorizar notificaciones de Yape o seguir lógica normal
                val shouldSend = if (isYapeNotification) {
                    LogBus.log("Notificación de Yape detectada - ENVIANDO INMEDIATAMENTE")
                    true
                } else if (settings.isSendAllNotifications()) {
                    true
                } else {
                    seemsPayment
                }
                
                if (!shouldSend) {
                    LogBus.log("Notificación no cumple criterios de envío")
                    return@launch
                }

                val payloadText = if (bigText.isNotBlank()) bigText else text
                val normalizedText = normalizeText(payloadText)
                
                // Verificar que hay texto válido para enviar (el texto es lo importante)
                if (normalizedText.isBlank()) {
                    LogBus.log("Notificación sin texto válido, no se envía")
                    return@launch
                }

                // De-duplicate: avoid sending the exact same payload repeatedly within a short window
                val dedupeKey = packageName + "|" + title + "|" + payloadText
                if (isDuplicate(dedupeKey, sbn.postTime)) {
                    LogBus.log("Notificación duplicada ignorada para paquete: " + packageName)
                    return@launch
                }

                val json = JSONObject().apply {
                    put("package", packageName)
                    put("title", normalizeText(title))
                    put("text", normalizedText)
                    put("postTime", sbn.postTime)
                }

                // Log del contenido que se va a enviar
                LogBus.log("Contenido a enviar - Título: '${normalizeText(title)}', Texto: '$normalizedText'")
                
                // Enviar usando conexión persistente (más rápido)
                if (isYapeNotification) {
                    LogBus.log("🚀 ENVIANDO NOTIFICACIÓN DE YAPE VIA MQTT - MÁXIMA PRIORIDAD")
                } else {
                    LogBus.log("Enviando notificación de pago via MQTT...")
                }
                
                val success = mqttClient.publishMessage(json.toString())
                if (success) {
                    Log.i(TAG, "Payment notification sent via MQTT")
                    if (isYapeNotification) {
                        LogBus.log("✅ NOTIFICACIÓN DE YAPE ENVIADA EXITOSAMENTE VIA MQTT")
                    } else {
                        LogBus.log("JSON enviado via MQTT")
                    }
                    sent.add(
                        SentRecord(
                            text = json.toString(),
                            url = "MQTT: ${settings.getMqttTopic()}",
                            code = 200,
                            time = System.currentTimeMillis()
                        )
                    )
                } else {
                    Log.w(TAG, "Failed to send MQTT message")
                    if (isYapeNotification) {
                        LogBus.log("❌ ERROR ENVIANDO NOTIFICACIÓN DE YAPE VIA MQTT")
                    } else {
                        LogBus.log("Error enviando mensaje MQTT")
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error sending MQTT notification", t)
                LogBus.log("Error al enviar MQTT: " + t.message)
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
    
    private suspend fun connectMqttWithRetry() {
        var attempts = 0
        val maxAttempts = 5
        
        while (attempts < maxAttempts) {
            try {
                val success = mqttClient.connect()
                if (success) {
                    LogBus.log("MQTT conectado exitosamente después de ${attempts + 1} intentos")
                    return
                }
            } catch (e: Exception) {
                LogBus.log("Error conectando MQTT (intento ${attempts + 1}): ${e.message}")
            }
            
            attempts++
            if (attempts < maxAttempts) {
                kotlinx.coroutines.delay(5000) // Esperar 5 segundos antes del siguiente intento
            }
        }
        
        LogBus.log("Error: No se pudo conectar MQTT después de $maxAttempts intentos")
    }
    
    private suspend fun startMqttKeepAlive() {
        while (true) {
            try {
                kotlinx.coroutines.delay(30000) // Verificar cada 30 segundos para máxima confiabilidad
                
                if (!mqttClient.isConnected()) {
                    LogBus.log("MQTT desconectado detectado, intentando reconectar...")
                    connectMqttWithRetry()
                } else {
                    LogBus.log("MQTT keep-alive: Conexión activa - Listo para Yape")
                }
            } catch (e: Exception) {
                LogBus.log("Error en keep-alive MQTT: ${e.message}")
            }
        }
    }
    
    /**
     * Normaliza texto removiendo acentos y caracteres especiales
     */
    private fun normalizeText(text: String): String {
        if (text.isBlank()) return text
        
        return text
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ñ", "n")
            .replace("Á", "A")
            .replace("É", "E")
            .replace("Í", "I")
            .replace("Ó", "O")
            .replace("Ú", "U")
            .replace("Ñ", "N")
            .replace("ü", "u")
            .replace("Ü", "U")
            .replace("ç", "c")
            .replace("Ç", "C")
            // Remover otros caracteres especiales comunes
            .replace("¿", "")
            .replace("¡", "")
            .replace("?", "")
            .replace("!", "")
            .replace("$", "")
            .replace("€", "")
            .replace("£", "")
            .replace("¥", "")
            .replace("°", "")
            .replace("º", "")
            .replace("ª", "")
            .replace("§", "")
            .replace("¶", "")
            .replace("†", "")
            .replace("‡", "")
            .replace("•", "")
            .replace("…", "...")
            .replace("–", "-")
            .replace("—", "-")
            .replace(""", "\"")
            .replace(""", "\"")
            .replace("'", "'")
            .replace("'", "'")
            .replace("«", "\"")
            .replace("»", "\"")
            .replace("‹", "<")
            .replace("›", ">")
            .replace("„", "\"")
            .replace("‚", "'")
            .replace("‰", "%")
            .replace("‱", "%")
            .replace("′", "'")
            .replace("″", "\"")
            .replace("‴", "'")
            .replace("‵", "`")
            .replace("‶", "\"")
            .replace("‷", "'")
            .replace("‸", "<")
            .replace("‹", "<")
            .replace("›", ">")
            .replace("※", "*")
            .replace("‼", "!!")
            .replace("⁇", "??")
            .replace("⁈", "?!")
            .replace("⁉", "!?")
            .replace("⁏", ";")
            .replace("⁐", ":")
            .replace("⁑", "**")
            .replace("⁒", "***")
            .replace("⁓", "~")
            .replace("⁔", "+")
            .replace("⁕", "*")
            .replace("⁖", "**")
            .replace("⁗", "***")
            .replace("⁘", "****")
            .replace("⁙", "*****")
            .replace("⁚", "**")
            .replace("⁛", "***")
            .replace("⁜", "****")
            .replace("⁝", "*****")
            .replace("⁞", "******")
            .trim()
    }
    
}


