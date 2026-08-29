package com.nathanb.lock.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.nathanb.lock.data.model.AutoLatchSchedule
import com.nathanb.lock.data.model.LatchDevice
import com.nathanb.lock.data.model.Mode
import com.nathanb.lock.data.model.ModeLatchLink
import com.nathanb.lock.data.model.NfcTag
import com.nathanb.lock.data.model.Profile
import com.nathanb.lock.data.model.Schedule
import com.nathanb.lock.data.model.ScheduleProfileLink
import com.nathanb.lock.data.model.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles") fun getAll(): Flow<List<Profile>>
    @Query("SELECT * FROM profiles WHERE id = :id") suspend fun getById(id: Long): Profile?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(profile: Profile): Long
    @Update suspend fun update(profile: Profile)
    @Delete suspend fun delete(profile: Profile)
    @Query("SELECT * FROM profiles") suspend fun getAllOnce(): List<Profile>
    @Query("DELETE FROM profiles") suspend fun deleteAll()
}

@Dao
interface SessionDao {
    @Insert suspend fun insert(session: Session): Long
    @Query("UPDATE sessions SET endTime = :endTime, endReason = :endReason WHERE id = :id") suspend fun endSession(id: Long, endTime: Long, endReason: String)
    @Query("SELECT * FROM sessions ORDER BY startTime DESC") fun getAll(): Flow<List<Session>>
    @Query("SELECT * FROM sessions WHERE endTime IS NULL LIMIT 1") suspend fun getActiveSession(): Session?
    @Query("SELECT COUNT(*) FROM sessions WHERE endTime IS NOT NULL") fun getCompletedCount(): Flow<Int>
    @Query("SELECT COALESCE(SUM(endTime - startTime), 0) FROM sessions WHERE endTime IS NOT NULL") fun getTotalBlockedMs(): Flow<Long>
    @Query("SELECT MAX(endTime - startTime) FROM sessions WHERE endTime IS NOT NULL") fun getLongestSessionMs(): Flow<Long?>
    @Query("SELECT * FROM sessions ORDER BY startTime DESC") suspend fun getAllOnce(): List<Session>
    @Insert suspend fun insertAll(sessions: List<Session>)
    @Query("DELETE FROM sessions") suspend fun deleteAll()
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY createdAt ASC") fun getAll(): Flow<List<Schedule>>
    @Query("SELECT * FROM schedules WHERE id = :id") suspend fun getById(id: Long): Schedule?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(schedule: Schedule): Long
    @Update suspend fun update(schedule: Schedule)
    @Query("UPDATE schedules SET enabled = :enabled WHERE id = :id") suspend fun setEnabled(id: Long, enabled: Boolean)
    @Query("DELETE FROM schedules WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM schedules ORDER BY createdAt ASC") suspend fun getAllOnce(): List<Schedule>
    @Query("DELETE FROM schedules") suspend fun deleteAll()
}

@Dao
interface ScheduleProfileDao {
    @Query("SELECT * FROM schedule_profiles") fun getAll(): Flow<List<ScheduleProfileLink>>
    @Query("SELECT * FROM schedule_profiles WHERE scheduleId = :scheduleId") suspend fun getByScheduleOnce(scheduleId: Long): List<ScheduleProfileLink>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(links: List<ScheduleProfileLink>)
    @Query("DELETE FROM schedule_profiles WHERE scheduleId = :scheduleId") suspend fun deleteBySchedule(scheduleId: Long)
    @Query("DELETE FROM schedule_profiles WHERE profileId = :profileId") suspend fun deleteByProfile(profileId: Long)
    @Query("SELECT * FROM schedule_profiles") suspend fun getAllOnce(): List<ScheduleProfileLink>
    @Query("DELETE FROM schedule_profiles") suspend fun deleteAll()
}

@Dao
interface NfcTagDao {
    @Query("SELECT * FROM nfc_tags ORDER BY createdAt ASC") fun getAll(): Flow<List<NfcTag>>
    @Query("SELECT * FROM nfc_tags WHERE uid = :uid") suspend fun getByUid(uid: String): NfcTag?
    @Query("SELECT COUNT(*) FROM nfc_tags") suspend fun count(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(tag: NfcTag)
    @Query("DELETE FROM nfc_tags WHERE uid = :uid") suspend fun delete(uid: String)
    @Query("UPDATE nfc_tags SET name = :name WHERE uid = :uid") suspend fun rename(uid: String, name: String)
    @Query("UPDATE nfc_tags SET profileId = :profileId WHERE uid = :uid") suspend fun setProfile(uid: String, profileId: Long?)
    @Query("UPDATE nfc_tags SET profileId = :newProfileId WHERE profileId = :oldProfileId") suspend fun reassignProfile(oldProfileId: Long, newProfileId: Long)
    @Query("SELECT * FROM nfc_tags WHERE profileId = :profileId") suspend fun getByProfile(profileId: Long): List<NfcTag>
    @Query("SELECT * FROM nfc_tags ORDER BY createdAt ASC") suspend fun getAllOnce(): List<NfcTag>
    @Query("DELETE FROM nfc_tags") suspend fun deleteAll()
}

@Dao
interface ModeDao {
    @Query("SELECT * FROM modes ORDER BY createdAt ASC") fun getAll(): Flow<List<Mode>>
    @Query("SELECT * FROM modes WHERE id = :id") suspend fun getById(id: Long): Mode?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(mode: Mode): Long
    @Update suspend fun update(mode: Mode)
    @Delete suspend fun delete(mode: Mode)
    @Query("DELETE FROM modes") suspend fun deleteAll()
}

@Dao
interface LatchDeviceDao {
    @Query("SELECT * FROM latch_devices ORDER BY createdAt ASC") fun getAll(): Flow<List<LatchDevice>>
    @Query("SELECT * FROM latch_devices WHERE uid = :uid") suspend fun getByUid(uid: String): LatchDevice?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(device: LatchDevice)
    @Query("UPDATE latch_devices SET name = :name WHERE uid = :uid") suspend fun rename(uid: String, name: String)
    @Query("DELETE FROM latch_devices WHERE uid = :uid") suspend fun delete(uid: String)
    @Query("DELETE FROM latch_devices") suspend fun deleteAll()
}

@Dao
interface ModeLatchDao {
    @Query("SELECT * FROM mode_latches") fun getAll(): Flow<List<ModeLatchLink>>
    @Query("SELECT * FROM mode_latches WHERE modeId = :modeId") suspend fun getByMode(modeId: Long): List<ModeLatchLink>
    @Query("SELECT * FROM mode_latches WHERE latchUid = :latchUid") suspend fun getByLatch(latchUid: String): List<ModeLatchLink>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(link: ModeLatchLink)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(links: List<ModeLatchLink>)
    @Query("DELETE FROM mode_latches WHERE modeId = :modeId") suspend fun deleteByMode(modeId: Long)
    @Query("DELETE FROM mode_latches WHERE modeId = :modeId AND latchUid = :latchUid AND action = :action") suspend fun delete(modeId: Long, latchUid: String, action: String)
}

@Dao
interface AutoLatchScheduleDao {
    @Query("SELECT * FROM auto_latch_schedules ORDER BY createdAt ASC") fun getAll(): Flow<List<AutoLatchSchedule>>
    @Query("SELECT * FROM auto_latch_schedules WHERE id = :id") suspend fun getById(id: Long): AutoLatchSchedule?
    @Query("SELECT * FROM auto_latch_schedules WHERE modeId = :modeId ORDER BY createdAt ASC") suspend fun getByMode(modeId: Long): List<AutoLatchSchedule>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(schedule: AutoLatchSchedule): Long
    @Update suspend fun update(schedule: AutoLatchSchedule)
    @Query("UPDATE auto_latch_schedules SET enabled = :enabled WHERE id = :id") suspend fun setEnabled(id: Long, enabled: Boolean)
    @Query("DELETE FROM auto_latch_schedules WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM auto_latch_schedules WHERE modeId = :modeId") suspend fun deleteByMode(modeId: Long)
    @Query("DELETE FROM auto_latch_schedules") suspend fun deleteAll()
}
