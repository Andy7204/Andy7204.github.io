package com.andy.todo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Alarms are cleared on reboot and app update, and exact-alarm permission or clock changes
 * affect when they should fire, so reschedule everything.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        Reminders.syncAll(c)
        TodoWidget.refresh(c)
    }
}
