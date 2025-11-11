package com.dar.checker.network

import android.content.Context
import android.util.Log
import com.dar.checker.data.SettingsRepository
import com.dar.checker.logging.LogBus
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttClient(private val context: Context) {
    
    private val settings = SettingsRepository(context)
    private var mqttClient: org.eclipse.paho.client.mqttv3.MqttClient? = null
    private var isConnected = false
    
    fun connect(): Boolean {
        return try {
            var broker = settings.getMqttBroker()
            val clientId = settings.getMqttClientId()
            
            // Corregir esquema si es necesario
            if (broker.startsWith("mqtt://")) {
                broker = broker.replace("mqtt://", "tcp://")
                LogBus.log("Corrigiendo esquema de mqtt:// a tcp://")
            }
            
            LogBus.log("Conectando a MQTT broker: $broker con client ID: $clientId")
            
            // Usar MemoryPersistence para evitar problemas de permisos en Android
            val persistence = MemoryPersistence()
            mqttClient = org.eclipse.paho.client.mqttv3.MqttClient(broker, clientId, persistence)
            
            val options = MqttConnectOptions().apply {
                isCleanSession = false // Mantener sesión para reconexión rápida
                isAutomaticReconnect = true
                connectionTimeout = 10 // Conexión más rápida
                keepAliveInterval = 20 // Keep-alive cada 20 segundos para máxima confiabilidad
                maxInflight = 10 // Permitir hasta 10 mensajes en vuelo
                mqttVersion = 4 // Usar MQTT 3.1.1 para mejor compatibilidad
            }
            
            mqttClient?.connect(options)
            
            // Verificar que realmente esté conectado
            if (mqttClient?.isConnected == true) {
                isConnected = true
                settings.setMqttConnected(true)
                Log.i(TAG, "MQTT conectado exitosamente")
                LogBus.log("MQTT conectado exitosamente")
                true
            } else {
                isConnected = false
                settings.setMqttConnected(false)
                Log.w(TAG, "MQTT no se conectó correctamente")
                LogBus.log("MQTT no se conectó correctamente")
                false
            }
        } catch (e: Exception) {
            isConnected = false
            settings.setMqttConnected(false)
            Log.e(TAG, "Error inicializando cliente MQTT", e)
            LogBus.log("Error inicializando cliente MQTT: ${e.message}")
            false
        }
    }
    
    fun disconnect() {
        try {
            mqttClient?.disconnect()
            isConnected = false
            settings.setMqttConnected(false)
            Log.i(TAG, "MQTT desconectado")
            LogBus.log("MQTT desconectado")
        } catch (e: Exception) {
            Log.e(TAG, "Error desconectando MQTT", e)
            LogBus.log("Error desconectando MQTT: ${e.message}")
        }
    }
    
    fun publishMessage(message: String): Boolean {
        return try {
            // Verificar conexión rápida
            if (!isConnected || mqttClient == null || !mqttClient!!.isConnected) {
                LogBus.log("MQTT desconectado, intentando reconectar...")
                if (!connect()) {
                    LogBus.log("Error reconectando MQTT")
                    return false
                }
            }
            
            val topic = settings.getMqttTopic()
            val payload = MqttMessage(message.toByteArray()).apply {
                qos = 0 // QoS 0 para mejor rendimiento
                isRetained = false
            }
            
            mqttClient?.publish(topic, payload)
            Log.i(TAG, "Mensaje MQTT enviado exitosamente")
            LogBus.log("Mensaje MQTT enviado a topic: $topic")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error publicando mensaje MQTT", e)
            LogBus.log("Error publicando mensaje MQTT: ${e.message}")
            
            // Marcar como desconectado si hay error
            isConnected = false
            settings.setMqttConnected(false)
            false
        }
    }
    
    fun isConnected(): Boolean = isConnected
    
    /**
     * Conecta, publica mensaje y desconecta inmediatamente (modo eficiente)
     */
    fun connectAndPublish(message: String): Boolean {
        return try {
            LogBus.log("Conectando MQTT para envío único...")
            
            // Conectar
            if (!connect()) {
                LogBus.log("Error conectando MQTT para envío único")
                return false
            }
            
            // Publicar mensaje
            val topic = settings.getMqttTopic()
            val payload = MqttMessage(message.toByteArray()).apply {
                qos = 0
                isRetained = false
            }
            
            mqttClient?.publish(topic, payload)
            LogBus.log("Mensaje MQTT enviado exitosamente")
            
            // Desconectar inmediatamente para ahorrar batería
            disconnect()
            LogBus.log("MQTT desconectado después del envío")
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error en connectAndPublish", e)
            LogBus.log("Error en envío único MQTT: ${e.message}")
            disconnect() // Asegurar desconexión en caso de error
            false
        }
    }
    
    companion object {
        private const val TAG = "MqttClient"
    }
}
