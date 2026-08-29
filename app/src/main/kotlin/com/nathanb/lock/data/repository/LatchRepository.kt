package com.nathanb.lock.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
    }

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    val modes: Flow<List<Mode>> = modeDao.getAll()
    val latchDevices: Flow<List<LatchDevice>> = latchDeviceDao.getAll()
    val modeLatchLinks: Flow<List<ModeLatchLink>> = modeLatchDao.getAll()
    val autoLatchSchedules: Flow<List<AutoLatchSchedule>> = autoLatchScheduleDao.getAll()

    /** Installed by LockApplication so schedule edits immediately re-arm the platform alarm. */
    var onAutoLatchSchedulesChanged: suspend () -> Unit = {}

    val activeModeState: StateFlow<ActiveModeState> = dataStore.data
        .map { preferences ->
            ActiveModeState(
                activeModeId = preferences[Keys.ACTIVE_MODE_ID],
                latchedAt = preferences[Keys.LATCHED_AT],
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
                    val latchedAt = state.latchedAt ?: return@collectLatest
                    val duration = mode?.maxLatchDurationMs ?: return@collectLatest
                    val remaining = (latchedAt + duration - now()).coerceAtLeast(0L)
                    delay(remaining)
                    if (activeModeState.value == state) unlatch()
                }
        }
    }

    suspend fun getMode(id: Long): Mode? = modeDao.getById(id)

    suspend fun latch(modeId: Long): Boolean {
        if (modeDao.getById(modeId) == null) return false
        dataStore.edit { preferences ->
            preferences[Keys.ACTIVE_MODE_ID] = modeId
            preferences[Keys.LATCHED_AT] = now()
        }
        return true
    }

    suspend fun unlatch() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.ACTIVE_MODE_ID)
            preferences.remove(Keys.LATCHED_AT)
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

    suspend fun updateMode(mode: Mode) {
        modeDao.update(mode.copy(allowedPackages = mode.allowedPackages.distinct()))
    }

    suspend fun deleteMode(mode: Mode) {
        if (activeModeState.value.activeModeId == mode.id) unlatch()
        modeDao.delete(mode)
        onAutoLatchSchedulesChanged()
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

    suspend fun getLatchActionsForMode(modeId: Long): List<ModeLatchLink> = modeLatchDao.getByMode(modeId)
    suspend fun getActionsForLatch(uid: String): List<ModeLatchLink> = modeLatchDao.getByLatch(uid)

    suspend fun setLatchAction(modeId: Long, latchUid: String, action: LatchAction) {
        modeLatchDao.insert(ModeLatchLink(modeId = modeId, latchUid = latchUid, action = action.value))
    }

    suspend fun removeLatchAction(modeId: Long, latchUid: String, action: LatchAction) {
        modeLatchDao.delete(modeId, latchUid, action.value)
    }

    suspend fun replaceLatchActions(modeId: Long, links: List<Pair<String, LatchAction>>) {
        database.withTransaction {
            modeLatchDao.deleteByMode(modeId)
            links.distinct().forEach { (uid, action) ->
                modeLatchDao.insert(ModeLatchLink(modeId = modeId, latchUid = uid, action = action.value))
            }
        }
    }

    suspend fun getAutoLatchSchedules(modeId: Long): List<AutoLatchSchedule> =
        autoLatchScheduleDao.getByMode(modeId)

    /** v1 keeps one simple recurring Auto-latch configuration per Mode. */
    suspend fun setAutoLatchSchedule(
        modeId: Long,
        enabled: Boolean,
        daysOfWeek: Int,
        startMinuteOfDay: Int,
    ) {
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
        onAutoLatchSchedulesChanged()
    }

    suspend fun addAutoLatchSchedule(
        modeId: Long,
        daysOfWeek: Int,
        startMinuteOfDay: Int,
        enabled: Boolean = true,
    ): Long {
        val id = autoLatchScheduleDao.insert(
            AutoLatchSchedule(
                modeId = modeId,
                daysOfWeek = daysOfWeek,
                startMinuteOfDay = startMinuteOfDay,
                enabled = enabled,
            )
        )
        onAutoLatchSchedulesChanged()
        return id
    }

    suspend fun updateAutoLatchSchedule(schedule: AutoLatchSchedule) {
        autoLatchScheduleDao.update(schedule)
        onAutoLatchSchedulesChanged()
    }

    suspend fun setAutoLatchEnabled(id: Long, enabled: Boolean) {
        autoLatchScheduleDao.setEnabled(id, enabled)
        onAutoLatchSchedulesChanged()
    }

    suspend fun removeAutoLatchSchedule(id: Long) {
        autoLatchScheduleDao.delete(id)
        onAutoLatchSchedulesChanged()
    }
}
