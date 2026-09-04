package com.nathanb.lock.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import com.nathanb.lock.data.database.AutoLatchScheduleDao
import com.nathanb.lock.data.database.LatchDeviceDao
import com.nathanb.lock.data.database.LockDatabase
import com.nathanb.lock.data.database.ModeDao
import com.nathanb.lock.data.database.ModeLatchDao
import com.nathanb.lock.data.model.AutoLatchSchedule
import com.nathanb.lock.data.model.ActiveModeState
import com.nathanb.lock.data.model.LatchAction
import com.nathanb.lock.data.model.LatchDevice
import com.nathanb.lock.data.model.Mode
import com.nathanb.lock.data.model.ModeLatchLink
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.latchDataStore by preferencesDataStore(name = "latch_prefs")

class LatchRepository(
    private val context: Context? = null,
    private val modeDao: ModeDao,
    private val latchDeviceDao: LatchDeviceDao,
    private val modeLatchDao: ModeLatchDao,
    private val autoLatchScheduleDao: AutoLatchScheduleDao,
    private val database: LockDatabase,
    private val dataStore: DataStore<Preferences> = context!!.latchDataStore,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private object Keys {
        val ACTIVE_MODE_ID = longPreferencesKey("active_mode_id")
        val LATCHED_AT = longPreferencesKey("latched_at")
        val SAFETY_RELEASE_AT = longPreferencesKey("safety_release_at")
        val SAFETY_PAUSED_MODE_IDS = stringSetPreferencesKey("safety_paused_mode_ids")
    }

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    val modes: Flow<List<Mode>> = modeDao.getAll()
    val latchDevices: Flow<List<LatchDevice>> = latchDeviceDao.getAll()
    val modeLatchLinks: Flow<List<ModeLatchLink>> = modeLatchDao.getAll()
    val autoLatchSchedules: Flow<List<AutoLatchSchedule>> = autoLatchScheduleDao.getAll()

    /** Modes whose Auto-latch was suspended because their safety release fired. */
    val safetyPausedModeIds: StateFlow<Set<Long>> = dataStore.data
        .map { preferences ->
            preferences[Keys.SAFETY_PAUSED_MODE_IDS].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** Installed by LockApplication so schedule edits immediately re-arm the platform alarm. */
    var onAutoLatchSchedulesChanged: suspend () -> Unit = {}

    val activeModeState: StateFlow<ActiveModeState> = dataStore.data
        .map { preferences ->
            ActiveModeState(
                activeModeId = preferences[Keys.ACTIVE_MODE_ID],
                latchedAt = preferences[Keys.LATCHED_AT],
                safetyReleaseAt = preferences[Keys.SAFETY_RELEASE_AT],
            )
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, ActiveModeState())

    val activeMode: StateFlow<Mode?> = combine(activeModeState, modes) { state, allModes ->
        allModes.firstOrNull { it.id == state.activeModeId }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            combine(activeModeState, activeMode) { state, mode -> state to mode }
                .collectLatest { (state, mode) ->
                    val modeId = state.activeModeId ?: return@collectLatest
                    val latchedAt = state.latchedAt ?: return@collectLatest
                    val deadline = state.safetyReleaseAt ?: mode?.let { latchedAt + it.maxLatchDurationMs }
                        ?: return@collectLatest

                    // Upgrade an already-active pre-dev.21 latch to the fixed-deadline model once.
                    if (state.safetyReleaseAt == null) {
                        dataStore.edit { it[Keys.SAFETY_RELEASE_AT] = deadline }
                        return@collectLatest
                    }

                    val remaining = (deadline - now()).coerceAtLeast(0L)
                    delay(remaining)
                    val current = activeModeState.value
                    if (current.activeModeId == modeId && current.safetyReleaseAt == deadline) {
                        safetyRelease(modeId)
                    }
                }
        }
    }

    suspend fun getMode(id: Long): Mode? = modeDao.getById(id)

    suspend fun latch(modeId: Long): Boolean {
        val mode = modeDao.getById(modeId) ?: return false
        val startedAt = now()
        dataStore.edit { preferences ->
            preferences[Keys.ACTIVE_MODE_ID] = modeId
            preferences[Keys.LATCHED_AT] = startedAt
            preferences[Keys.SAFETY_RELEASE_AT] = startedAt + mode.maxLatchDurationMs
        }
        return true
    }

    /** Normal authorised release. This does not affect the Mode's future Auto-latch schedule. */
    suspend fun unlatch() {
        clearActiveMode()
    }

    /**
     * Exceptional failsafe release. Auto-latch is disabled for this Mode until the user explicitly
     * turns it back on, guaranteeing a real recovery window if a Latch device is lost or broken.
     */
    private suspend fun safetyRelease(modeId: Long) {
        autoLatchScheduleDao.getByMode(modeId).forEach { schedule ->
            if (schedule.enabled) autoLatchScheduleDao.setEnabled(schedule.id, false)
        }
        dataStore.edit { preferences ->
            val paused = preferences[Keys.SAFETY_PAUSED_MODE_IDS].orEmpty().toMutableSet()
            paused += modeId.toString()
            preferences[Keys.SAFETY_PAUSED_MODE_IDS] = paused
            preferences.remove(Keys.ACTIVE_MODE_ID)
            preferences.remove(Keys.LATCHED_AT)
            preferences.remove(Keys.SAFETY_RELEASE_AT)
        }
        onAutoLatchSchedulesChanged()
    }

    private suspend fun clearActiveMode() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.ACTIVE_MODE_ID)
            preferences.remove(Keys.LATCHED_AT)
            preferences.remove(Keys.SAFETY_RELEASE_AT)
        }
    }

    suspend fun toggle(modeId: Long): Boolean {
        return if (activeModeState.value.activeModeId == modeId) {
            unlatch()
            false
        } else {
            latch(modeId)
        }
    }

    suspend fun createMode(name: String, allowedPackages: List<String>, maxLatchDurationMs: Long): Long =
        modeDao.insert(
            Mode(
                name = name,
                allowedPackages = allowedPackages.distinct(),
                maxLatchDurationMs = maxLatchDurationMs,
            )
        )

    /** Active Modes are immutable until they are properly released. */
    suspend fun updateMode(mode: Mode): Boolean {
        if (activeModeState.value.activeModeId == mode.id) return false
        modeDao.update(mode.copy(allowedPackages = mode.allowedPackages.distinct()))
        return true
    }

    /** An active Mode must be properly unlatched before it can be deleted. */
    suspend fun deleteMode(mode: Mode): Boolean {
        if (activeModeState.value.activeModeId == mode.id) return false
        modeDao.delete(mode)
        clearSafetyPause(mode.id)
        onAutoLatchSchedulesChanged()
        return true
    }

    suspend fun getLatchDevice(uid: String): LatchDevice? = latchDeviceDao.getByUid(uid)

    suspend fun addLatchDevice(uid: String, name: String) {
        latchDeviceDao.insert(LatchDevice(uid = uid, name = name))
    }

    suspend fun renameLatchDevice(uid: String, name: String) {
        latchDeviceDao.rename(uid, name)
    }

    suspend fun removeLatchDevice(uid: String): Boolean {
        val activeModeId = activeModeState.value.activeModeId
        if (activeModeId != null && modeLatchDao.getByLatch(uid).any { it.modeId == activeModeId }) {
            return false
        }
        latchDeviceDao.delete(uid)
        return true
    }

    suspend fun getLatchActionsForMode(modeId: Long): List<ModeLatchLink> = modeLatchDao.getByMode(modeId)
    suspend fun getActionsForLatch(uid: String): List<ModeLatchLink> = modeLatchDao.getByLatch(uid)

    suspend fun setLatchAction(modeId: Long, latchUid: String, action: LatchAction): Boolean {
        if (activeModeState.value.activeModeId == modeId) return false
        modeLatchDao.insert(ModeLatchLink(modeId = modeId, latchUid = latchUid, action = action.value))
        return true
    }

    suspend fun removeLatchAction(modeId: Long, latchUid: String, action: LatchAction): Boolean {
        if (activeModeState.value.activeModeId == modeId) return false
        modeLatchDao.delete(modeId, latchUid, action.value)
        return true
    }

    suspend fun replaceLatchActions(modeId: Long, links: List<Pair<String, LatchAction>>): Boolean {
        if (activeModeState.value.activeModeId == modeId) return false
        database.withTransaction {
            modeLatchDao.deleteByMode(modeId)
            links.distinct().forEach { (uid, action) ->
                modeLatchDao.insert(ModeLatchLink(modeId = modeId, latchUid = uid, action = action.value))
            }
        }
        return true
    }

    suspend fun getAutoLatchSchedules(modeId: Long): List<AutoLatchSchedule> =
        autoLatchScheduleDao.getByMode(modeId)

    /** v1 keeps one simple recurring Auto-latch configuration per Mode. */
    suspend fun setAutoLatchSchedule(
        modeId: Long,
        enabled: Boolean,
        daysOfWeek: Int,
        startMinuteOfDay: Int,
    ): Boolean {
        if (activeModeState.value.activeModeId == modeId) return false
        require(startMinuteOfDay in 0..1439)
        database.withTransaction {
            autoLatchScheduleDao.deleteByMode(modeId)
            autoLatchScheduleDao.insert(
                AutoLatchSchedule(
                    modeId = modeId,
                    daysOfWeek = daysOfWeek,
                    startMinuteOfDay = startMinuteOfDay,
                    enabled = enabled,
                )
            )
        }
        if (enabled) clearSafetyPause(modeId)
        onAutoLatchSchedulesChanged()
        return true
    }

    suspend fun addAutoLatchSchedule(
        modeId: Long,
        daysOfWeek: Int,
        startMinuteOfDay: Int,
        enabled: Boolean = true,
    ): Long {
        if (activeModeState.value.activeModeId == modeId) return -1L
        val id = autoLatchScheduleDao.insert(
            AutoLatchSchedule(
                modeId = modeId,
                daysOfWeek = daysOfWeek,
                startMinuteOfDay = startMinuteOfDay,
                enabled = enabled,
            )
        )
        if (enabled) clearSafetyPause(modeId)
        onAutoLatchSchedulesChanged()
        return id
    }

    suspend fun updateAutoLatchSchedule(schedule: AutoLatchSchedule): Boolean {
        if (activeModeState.value.activeModeId == schedule.modeId) return false
        autoLatchScheduleDao.update(schedule)
        if (schedule.enabled) clearSafetyPause(schedule.modeId)
        onAutoLatchSchedulesChanged()
        return true
    }

    suspend fun setAutoLatchEnabled(id: Long, enabled: Boolean) {
        val schedule = autoLatchSchedulesSnapshot(id)
        autoLatchScheduleDao.setEnabled(id, enabled)
        if (enabled && schedule != null) clearSafetyPause(schedule.modeId)
        onAutoLatchSchedulesChanged()
    }

    suspend fun removeAutoLatchSchedule(id: Long) {
        val schedule = autoLatchSchedulesSnapshot(id)
        autoLatchScheduleDao.delete(id)
        if (schedule != null) clearSafetyPause(schedule.modeId)
        onAutoLatchSchedulesChanged()
    }

    private suspend fun autoLatchSchedulesSnapshot(id: Long): AutoLatchSchedule? =
        autoLatchScheduleDao.getAllSnapshot().firstOrNull { it.id == id }

    private suspend fun clearSafetyPause(modeId: Long) {
        dataStore.edit { preferences ->
            val paused = preferences[Keys.SAFETY_PAUSED_MODE_IDS].orEmpty().toMutableSet()
            if (paused.remove(modeId.toString())) {
                preferences[Keys.SAFETY_PAUSED_MODE_IDS] = paused
            }
        }
    }
}
