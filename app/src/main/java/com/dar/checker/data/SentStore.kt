package com.dar.checker.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class SentRecord(
    val text: String,
    val url: String,
    val code: Int,
    val time: Long
)

class SentStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun add(record: SentRecord) {
        val list = getAll().toMutableList()
        list.add(0, record)
        while (list.size > MAX_ITEMS) {
            list.removeLast()
        }
        save(list)
    }

    fun getAll(): List<SentRecord> {
        val json = prefs.getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(json)
        val out = ArrayList<SentRecord>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                SentRecord(
                    text = o.optString("text"),
                    url = o.optString("url"),
                    code = o.optInt("code"),
                    time = o.optLong("time")
                )
            )
        }
        return out
    }

    fun clear() {
        prefs.edit().putString(KEY, "[]").apply()
    }

    private fun save(list: List<SentRecord>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("text", r.text)
                    put("url", r.url)
                    put("code", r.code)
                    put("time", r.time)
                }
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val PREFS = "sent_store"
        private const val KEY = "sent"
        private const val MAX_ITEMS = 100
    }
}


