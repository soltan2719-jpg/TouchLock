package com.example.touchlock

import android.content.Context
import androidx.core.content.edit

object TouchLockState {
    private const val PREFS = "touchlock_state"
    private const val KEY = "locked"

    fun setLocked(context: Context, locked: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY, locked)
        }
    }

    fun isLocked(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)
    }
}