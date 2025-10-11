package com.dar.checker.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class StoredNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val bigText: String,
    val postTime: Long
)

class NotificationStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun add(notification: StoredNotification) {
        val list = getAll().toMutableList()
        list.add(0, notification)
        if (list.size > MAX_ITEMS) list.removeLast()
        save(list)
    }

    fun getAll(): List<StoredNotification> {
        val json = prefs.getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(json)
        val out = ArrayList<StoredNotification>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                StoredNotification(
                    packageName = o.optString("package"),
                    title = o.optString("title"),
                    text = o.optString("text"),
                    bigText = o.optString("bigText"),
                    postTime = o.optLong("postTime")
                )
            )
        }
        return out
    }

    fun clear() {
        prefs.edit().putString(KEY, "[]").apply()
    }

    private fun save(list: List<StoredNotification>) {
        val arr = JSONArray()
        list.forEach { n ->
            arr.put(
                JSONObject().apply {
                    put("package", n.packageName)
                    put("title", n.title)
                    put("text", n.text)
                    put("bigText", n.bigText)
                    put("postTime", n.postTime)
                }
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val PREFS = "notification_store"
        private const val KEY = "notifications"
        private const val MAX_ITEMS = 500
    }
}


