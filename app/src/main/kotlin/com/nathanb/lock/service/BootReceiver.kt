package com.nathanb.lock.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.LockApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        if (BuildConfig.DEBUG) Log.d(TAG, "Boot completed, checking lock state")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as LockApplication
                val state = app.repository.getLockState()

                if (state.isLocked) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "Was locked before reboot, restarting foreground service")
                    LockForegroundService.start(context)
                }

                // AlarmManager alarms do not survive reboot. Rearm both the inherited Lock window
                // engine and Latch's one-way Auto-latch engine.
                kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                    app.scheduleManager.evaluateAndRearm()
                    app.autoLatchManager.rearm()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
