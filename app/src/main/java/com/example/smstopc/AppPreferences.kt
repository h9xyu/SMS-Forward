package com.example.smstopc

import android.content.Context
import android.content.SharedPreferences

object AppPreferences {
    private const val PREFS_NAME = "sms_forward_prefs"
    private const val DEFAULT_NTFY_TOPIC = "sms-forward-app"
    private const val KEY_NTFY_TOPIC = "ntfy_topic"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_FORWARD = "last_forward_time"
    private const val KEY_FORWARD_COUNT = "forward_count"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var ntfyTopic: String
        get() = prefs(App.context).getString(KEY_NTFY_TOPIC, DEFAULT_NTFY_TOPIC) ?: DEFAULT_NTFY_TOPIC
        set(value) = prefs(App.context).edit().putString(KEY_NTFY_TOPIC, value).apply()

    var isEnabled: Boolean
        get() = prefs(App.context).getBoolean(KEY_ENABLED, false)
        set(value) = prefs(App.context).edit().putBoolean(KEY_ENABLED, value).apply()

    var lastForwardTime: Long
        get() = prefs(App.context).getLong(KEY_LAST_FORWARD, 0)
        set(value) = prefs(App.context).edit().putLong(KEY_LAST_FORWARD, value).apply()

    var forwardCount: Int
        get() = prefs(App.context).getInt(KEY_FORWARD_COUNT, 0)
        set(value) = prefs(App.context).edit().putInt(KEY_FORWARD_COUNT, value).apply()

    fun generateNtfyTopic(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val random = (1..16).map { chars[kotlin.random.Random.nextInt(chars.length)] }.joinToString("")
        return "smsfw-$random"
    }
}
