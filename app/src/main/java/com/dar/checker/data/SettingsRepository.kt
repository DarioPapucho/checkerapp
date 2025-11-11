package com.dar.checker.data

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getServerHost(): String {
        return prefs.getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST
    }

    fun setServerHost(host: String) {
        prefs.edit().putString(KEY_HOST, host).apply()
    }

    fun isPackageFilterEnabled(): Boolean {
        return prefs.getBoolean(KEY_FILTER_ENABLED, false)
    }

    fun setPackageFilterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FILTER_ENABLED, enabled).apply()
    }

    fun isSendAllNotifications(): Boolean {
        return prefs.getBoolean(KEY_SEND_ALL, true)
    }

    fun setSendAllNotifications(sendAll: Boolean) {
        prefs.edit().putBoolean(KEY_SEND_ALL, sendAll).apply()
    }

    fun isLoggingEnabled(): Boolean {
        return prefs.getBoolean(KEY_LOGGING_ENABLED, true)
    }

    fun setLoggingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOGGING_ENABLED, enabled).apply()
    }

    fun getAllowedPackages(): Set<String> {
        val raw = prefs.getString(KEY_ALLOWED, "") ?: ""
        if (raw.isBlank()) return emptySet()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun addAllowedPackage(pkg: String) {
        if (pkg.isBlank()) return
        val set = getAllowedPackages().toMutableSet()
        set.add(pkg.trim())
        prefs.edit().putString(KEY_ALLOWED, set.joinToString(",")).apply()
    }

    fun removeAllowedPackage(pkg: String) {
        val set = getAllowedPackages().toMutableSet()
        set.remove(pkg.trim())
        prefs.edit().putString(KEY_ALLOWED, set.joinToString(",")).apply()
    }

    fun getMqttBroker(): String {
        return prefs.getString(KEY_MQTT_BROKER, DEFAULT_MQTT_BROKER) ?: DEFAULT_MQTT_BROKER
    }

    fun setMqttBroker(broker: String) {
        prefs.edit().putString(KEY_MQTT_BROKER, broker).apply()
    }

    fun getMqttTopic(): String {
        return prefs.getString(KEY_MQTT_TOPIC, DEFAULT_MQTT_TOPIC) ?: DEFAULT_MQTT_TOPIC
    }

    fun setMqttTopic(topic: String) {
        prefs.edit().putString(KEY_MQTT_TOPIC, topic).apply()
    }

    fun getMqttClientId(): String {
        return prefs.getString(KEY_MQTT_CLIENT_ID, DEFAULT_MQTT_CLIENT_ID) ?: DEFAULT_MQTT_CLIENT_ID
    }

    fun setMqttClientId(clientId: String) {
        prefs.edit().putString(KEY_MQTT_CLIENT_ID, clientId).apply()
    }

    fun isMqttConnected(): Boolean {
        return prefs.getBoolean(KEY_MQTT_CONNECTED, false)
    }

    fun setMqttConnected(connected: Boolean) {
        prefs.edit().putBoolean(KEY_MQTT_CONNECTED, connected).apply()
    }

    companion object {
        private const val PREFS_NAME = "checker_settings"
        private const val KEY_HOST = "server_host"
        private const val KEY_ALLOWED = "allowed_packages"
        private const val KEY_FILTER_ENABLED = "filter_enabled"
        private const val KEY_SEND_ALL = "send_all"
        private const val KEY_LOGGING_ENABLED = "logging_enabled"
        private const val KEY_MQTT_BROKER = "mqtt_broker"
        private const val KEY_MQTT_TOPIC = "mqtt_topic"
        private const val KEY_MQTT_CLIENT_ID = "mqtt_client_id"
        private const val KEY_MQTT_CONNECTED = "mqtt_connected"
        private const val DEFAULT_HOST = "http://localhost:4444"
        private const val DEFAULT_MQTT_BROKER = "tcp://broker.hivemq.com:1883"
        private const val DEFAULT_MQTT_TOPIC = "payment-notifications"
        private const val DEFAULT_MQTT_CLIENT_ID = "checker-app"
    }
}


