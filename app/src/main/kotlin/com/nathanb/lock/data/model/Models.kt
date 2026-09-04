package com.nathanb.lock.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ProfileType(val value: String) {
    STANDARD("standard"),
    NO_ESCAPE("no_escape");

    companion object {
        fun fromValue(value: String?): ProfileType =
            entries.firstOrNull { it.value == value } ?: STANDARD
    }
}

@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val blockedPackages: List<String>,
    @ColumnInfo(defaultValue = "standard") val type: String = ProfileType.STANDARD.value,
    @ColumnInfo(defaultValue = "0") val isDefault: Boolean = false,
    val durationMs: Long? = null,
)

@Entity(tableName = "sessions", indices = [Index("startTime")])
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val startTime: Long,
    val endTime: Long? = null,
    val endReason: String? = null,
)

@Entity(tableName = "nfc_tags")
data class NfcTag(
    @PrimaryKey val uid: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val profileId: Long? = null,
)

/**
 * A recurring auto-lock window. Days are a bitmask (bit 0 = Monday … bit 6 = Sunday,
 * i.e. `1 shl (DayOfWeek.value - 1)`). An occurrence belongs to the day its START falls
 * on; endMinuteOfDay <= startMinuteOfDay means the window ends the next day (overnight).
 * Blocked apps come from the attached profiles (schedule_profiles join table); a schedule
 * with no attached profile is inert.
 *
 * This is the inherited Lock scheduling model. Latch's one-way auto-latch model is represented
 * separately by [AutoLatchSchedule] during the transition.
 */
@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val daysOfWeek: Int,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "schedule_profiles",
    primaryKeys = ["scheduleId", "profileId"],
    indices = [Index("profileId")],
)
data class ScheduleProfileLink(
    val scheduleId: Long,
    val profileId: Long,
)

// -----------------------------------------------------------------------------
// Latch domain model
// -----------------------------------------------------------------------------
// These entities are introduced alongside the inherited Lock model so the behaviour can be
// migrated in controlled steps. Phase 1 deliberately does not reinterpret old Profile data:
// a Lock blocklist cannot safely be converted into a Latch allow-list.

/** A user-defined restricted state. While active, only [allowedPackages] get through. */
@Entity(tableName = "modes")
data class Mode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val allowedPackages: List<String>,
    val maxLatchDurationMs: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Persisted latch state. [safetyReleaseAt] is snapshotted when the Mode starts so later Mode
 * edits cannot move the failsafe deadline for an already-active latch.
 */
data class ActiveModeState(
    val activeModeId: Long? = null,
    val latchedAt: Long? = null,
    val safetyReleaseAt: Long? = null,
) {
    val isLatched: Boolean get() = activeModeId != null
}

/** A named physical NFC tag used by Latch. */
@Entity(tableName = "latch_devices")
data class LatchDevice(
    @PrimaryKey val uid: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class LatchAction(val value: String) {
    LATCH("latch"),
    UNLATCH("unlatch"),
    TOGGLE("toggle");

    companion object {
        fun fromValue(value: String?): LatchAction? = entries.firstOrNull { it.value == value }
    }
}

/**
 * Connects a physical [LatchDevice] to a [Mode] and defines what that device does for the mode.
 * Keeping this relationship separate means one physical Latch can play different roles in
 * different Modes.
 */
@Entity(
    tableName = "mode_latches",
    primaryKeys = ["modeId", "latchUid", "action"],
    foreignKeys = [
        ForeignKey(
            entity = Mode::class,
            parentColumns = ["id"],
            childColumns = ["modeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LatchDevice::class,
            parentColumns = ["uid"],
            childColumns = ["latchUid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("modeId"), Index("latchUid")],
)
data class ModeLatchLink(
    val modeId: Long,
    val latchUid: String,
    val action: String,
)

/**
 * A one-way recurring activation for a Latch Mode. Days use bit 0 = Monday through bit 6 = Sunday.
 * Auto-latch can start a Mode but never ends one.
 */
@Entity(
    tableName = "auto_latch_schedules",
    foreignKeys = [
        ForeignKey(
            entity = Mode::class,
            parentColumns = ["id"],
            childColumns = ["modeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("modeId")],
)
data class AutoLatchSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modeId: Long,
    val daysOfWeek: Int,
    val startMinuteOfDay: Int,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
