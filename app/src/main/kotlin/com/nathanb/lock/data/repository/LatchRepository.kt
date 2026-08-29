package com.nathanb.lock.data.repository

import androidx.room.withTransaction
import com.nathanb.lock.data.database.AutoLatchScheduleDao
import com.nathanb.lock.data.database.LatchDeviceDao
import com.nathanb.lock.data.database.LockDatabase
import com.nathanb.lock.data.database.ModeDao
import com.nathanb.lock.data.database.ModeLatchDao
import com.nathanb.lock.data.model.AutoLatchSchedule
import com.nathanb.lock.data.model.LatchAction
import com.nathanb.lock.data.model.LatchDevice
import com.nathanb.lock.data.model.Mode
import com.nathanb.lock.data.model.ModeLatchLink
import kotlinx.coroutines.flow.Flow

/**
 * Repository for Latch's new domain model.
 *
 * Kept separate from the inherited LockRepository while the app is transitioned in small,
 * testable steps. This repository does not yet control blocking or NFC session behaviour.
 */
class LatchRepository(
    private val modeDao: ModeDao,
    private val latchDeviceDao: LatchDeviceDao,
    private val modeLatchDao: ModeLatchDao,
    private val autoLatchScheduleDao: AutoLatchScheduleDao,
    private val database: LockDatabase,
) {
    val modes: Flow<List<Mode>> = modeDao.getAll()
    val latchDevices: Flow<List<LatchDevice>> = latchDeviceDao.getAll()
    val modeLatchLinks: Flow<List<ModeLatchLink>> = modeLatchDao.getAll()
    val autoLatchSchedules: Flow<List<AutoLatchSchedule>> = autoLatchScheduleDao.getAll()

    suspend fun getMode(id: Long): Mode? = modeDao.getById(id)

    suspend fun createMode(
        name: String,
        allowedPackages: List<String>,
        maxLatchDurationMs: Long,
    ): Long = modeDao.insert(
        Mode(
            name = name,
            allowedPackages = allowedPackages.distinct(),
            maxLatchDurationMs = maxLatchDurationMs,
        )
    )

    suspend fun updateMode(mode: Mode) {
        modeDao.update(mode.copy(allowedPackages = mode.allowedPackages.distinct()))
    }

    suspend fun deleteMode(mode: Mode) {
        modeDao.delete(mode)
    }

    suspend fun getLatchDevice(uid: String): LatchDevice? = latchDeviceDao.getByUid(uid)

    suspend fun addLatchDevice(uid: String, name: String) {
        latchDeviceDao.insert(LatchDevice(uid = uid, name = name))
    }

    suspend fun renameLatchDevice(uid: String, name: String) {
        latchDeviceDao.rename(uid, name)
    }

    suspend fun removeLatchDevice(uid: String) {
        latchDeviceDao.delete(uid)
    }

    suspend fun getLatchActionsForMode(modeId: Long): List<ModeLatchLink> =
        modeLatchDao.getByMode(modeId)

    suspend fun getActionsForLatch(uid: String): List<ModeLatchLink> =
        modeLatchDao.getByLatch(uid)

    suspend fun setLatchAction(modeId: Long, latchUid: String, action: LatchAction) {
        modeLatchDao.insert(
            ModeLatchLink(
                modeId = modeId,
                latchUid = latchUid,
                action = action.value,
            )
        )
    }

    suspend fun removeLatchAction(modeId: Long, latchUid: String, action: LatchAction) {
        modeLatchDao.delete(modeId, latchUid, action.value)
    }

    /** Replace all physical Latch assignments for a Mode as one transaction. */
    suspend fun replaceLatchActions(modeId: Long, links: List<Pair<String, LatchAction>>) {
        database.withTransaction {
            modeLatchDao.deleteByMode(modeId)
            links.distinct().forEach { (uid, action) ->
                modeLatchDao.insert(
                    ModeLatchLink(
                        modeId = modeId,
                        latchUid = uid,
                        action = action.value,
                    )
                )
            }
        }
    }

    suspend fun getAutoLatchSchedules(modeId: Long): List<AutoLatchSchedule> =
        autoLatchScheduleDao.getByMode(modeId)

    suspend fun addAutoLatchSchedule(
        modeId: Long,
        daysOfWeek: Int,
        startMinuteOfDay: Int,
        enabled: Boolean = true,
    ): Long = autoLatchScheduleDao.insert(
        AutoLatchSchedule(
            modeId = modeId,
            daysOfWeek = daysOfWeek,
            startMinuteOfDay = startMinuteOfDay,
            enabled = enabled,
        )
    )

    suspend fun updateAutoLatchSchedule(schedule: AutoLatchSchedule) {
        autoLatchScheduleDao.update(schedule)
    }

    suspend fun setAutoLatchEnabled(id: Long, enabled: Boolean) {
        autoLatchScheduleDao.setEnabled(id, enabled)
    }

    suspend fun removeAutoLatchSchedule(id: Long) {
        autoLatchScheduleDao.delete(id)
    }
}
