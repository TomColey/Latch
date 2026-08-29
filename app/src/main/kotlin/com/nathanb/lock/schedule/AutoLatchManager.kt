package com.nathanb.lock.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nathanb.lock.data.model.AutoLatchSchedule
import com.nathanb.lock.data.repository.LatchRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicBoolean

interface AutoLatchEffects {
    fun arm(triggerAtEpochMillis: Long)
    fun cancel()
}

class AndroidAutoLatchEffects(private val context: Context) : AutoLatchEffects {
    private fun pendingIntent(triggerAtEpochMillis: Long = 0L): PendingIntent {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java)
            .setAction(ScheduleAlarmReceiver.ACTION_AUTO_LATCH)
            .putExtra(ScheduleAlarmReceiver.EXTRA_AUTO_LATCH_TRIGGER_AT, triggerAtEpochMillis)
        return PendingIntent.getBroadcast(
            context,
            AUTO_LATCH_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun arm(triggerAtEpochMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(triggerAtEpochMillis)
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtEpochMillis,
                pendingIntent,
            )
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtEpochMillis, pendingIntent)
        }
    }

    override fun cancel() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent())
    }

    private companion object {
        const val AUTO_LATCH_REQUEST_CODE = 201
    }
}

/**
 * One-way scheduled activation for Latch Modes.
 * Schedules can start a Mode only while Latch is currently unlatched. They never release or
 * replace an active Mode.
 */
class AutoLatchManager(
    private val repository: LatchRepository,
    private val effects: AutoLatchEffects,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    private val runGuard = Mutex()
    private val rerunRequested = AtomicBoolean(false)

    suspend fun rearm() {
        if (!runGuard.tryLock()) {
            rerunRequested.set(true)
            return
        }
        try {
            do {
                rerunRequested.set(false)
                armNext()
            } while (rerunRequested.compareAndSet(true, false))
        } finally {
            runGuard.unlock()
        }
    }

    /** Called only for an Auto-latch alarm. The intended trigger time survives process death. */
    suspend fun handleAlarm(triggerAtEpochMillis: Long) {
        if (triggerAtEpochMillis <= 0L) {
            rearm()
            return
        }

        val current = now()
        val scheduledAt = Instant.ofEpochMilli(triggerAtEpochMillis).atZone(current.zone)
        if (current.toInstant().isBefore(scheduledAt.toInstant())) {
            rearm()
            return
        }

        val schedules = repository.autoLatchSchedules.first()
        val dueModeIds = schedules
            .asSequence()
            .filter { it.enabled && it.isDueAt(scheduledAt) }
            .map { it.modeId }
            .distinct()
            .toList()

        // Never replace an active Mode. Simultaneous Auto-latches for different Modes are
        // intentionally treated as ambiguous rather than resolved by database order.
        if (repository.activeModeState.value.activeModeId == null && dueModeIds.size == 1) {
            repository.latch(dueModeIds.single())
        }

        armNext(reference = current)
    }

    private suspend fun armNext(reference: ZonedDateTime = now()) {
        val schedules = repository.autoLatchSchedules.first()
        val next = nextOccurrence(schedules, reference)
        if (next == null) effects.cancel() else effects.arm(next.toInstant().toEpochMilli())
    }

    internal fun nextOccurrence(
        schedules: List<AutoLatchSchedule>,
        reference: ZonedDateTime,
    ): ZonedDateTime? {
        var best: ZonedDateTime? = null
        val today = reference.toLocalDate()
        for (offset in 0L..7L) {
            val day = today.plusDays(offset)
            val dayBit = 1 shl (day.dayOfWeek.value - 1)
            schedules.asSequence()
                .filter { it.enabled && (it.daysOfWeek and dayBit) != 0 }
                .forEach { schedule ->
                    val candidate = day.atStartOfDay(reference.zone)
                        .plusMinutes(schedule.startMinuteOfDay.toLong())
                    if (candidate.isAfter(reference) && (best == null || candidate.isBefore(best))) {
                        best = candidate
                    }
                }
        }
        return best
    }

    private fun AutoLatchSchedule.isDueAt(time: ZonedDateTime): Boolean {
        val dayBit = 1 shl (time.dayOfWeek.value - 1)
        return (daysOfWeek and dayBit) != 0 &&
            time.hour * 60 + time.minute == startMinuteOfDay
    }
}
