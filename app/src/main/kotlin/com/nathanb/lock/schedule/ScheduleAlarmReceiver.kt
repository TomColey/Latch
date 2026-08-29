package com.nathanb.lock.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nathanb.lock.LockApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        when (action) {
            ACTION_WINDOW_BOUNDARY,
            ACTION_AUTO_LATCH,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> Unit
            else -> return
        }

        val pendingResult = goAsync()
        val app = context.applicationContext as LockApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(RECEIVER_TIMEOUT_MS) {
                    when (action) {
                        ACTION_WINDOW_BOUNDARY -> app.scheduleManager.evaluateAndRearm()
                        ACTION_AUTO_LATCH -> app.autoLatchManager.handleAlarm(
                            intent.getLongExtra(EXTRA_AUTO_LATCH_TRIGGER_AT, 0L)
                        )
                        else -> {
                            app.scheduleManager.evaluateAndRearm()
                            app.autoLatchManager.rearm()
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_WINDOW_BOUNDARY = "com.nathanb.lock.action.SCHEDULE_WINDOW_BOUNDARY"
        const val ACTION_AUTO_LATCH = "com.tomcoley.latch.action.AUTO_LATCH"
        const val EXTRA_AUTO_LATCH_TRIGGER_AT = "auto_latch_trigger_at"
        private const val RECEIVER_TIMEOUT_MS = 8_000L
    }
}
