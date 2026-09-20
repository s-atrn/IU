package com.example.iu

import android.content.Context

object IUSettings {

    private const val PREFS_NAME =
        "iu_settings"

    private const val KEY_NOTIFY_RECEIVED_FILES =
        "notify_received_files"

    fun notificationsEnabled(
        context: Context
    ): Boolean {
        return context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getBoolean(
                KEY_NOTIFY_RECEIVED_FILES,
                true
            )
    }

    fun setNotificationsEnabled(
        context: Context,
        enabled: Boolean
    ) {
        context.applicationContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                KEY_NOTIFY_RECEIVED_FILES,
                enabled
            )
            .apply()
    }
}