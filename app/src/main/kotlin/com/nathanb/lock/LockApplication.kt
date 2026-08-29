package com.nathanb.lock

import android.app.Application
import androidx.room.Room
import com.nathanb.lock.data.database.LockDatabase
import com.nathanb.lock.data.database.MIGRATION_1_2
import com.nathanb.lock.data.database.MIGRATION_2_3
import com.nathanb.lock.data.database.MIGRATION_3_4
import com.nathanb.lock.data.database.MIGRATION_4_5
import com.nathanb.lock.data.database.MIGRATION_5_6
import com.nathanb.lock.data.repository.LatchRepository
import com.nathanb.lock.data.repository.LatchRuntime
import com.nathanb.lock.data.repository.LockRepository
import com.nathanb.lock.schedule.AndroidAutoLatchEffects
import com.nathanb.lock.schedule.AndroidScheduleEffects
import com.nathanb.lock.schedule.AutoLatchManager
import com.nathanb.lock.schedule.ScheduleManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LockApplication : Application() {
    lateinit var database: LockDatabase
        private set
    lateinit var repository: LockRepository
        private set
    lateinit var latchRepository: LatchRepository
        private set
    lateinit var scheduleManager: ScheduleManager
        private set
    lateinit var autoLatchManager: AutoLatchManager
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        database = Room.databaseBuilder(this, LockDatabase::class.java, "lock.db")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
            )
            .build()

        repository = LockRepository(
            context = this,
            profileDao = database.profileDao(),
            sessionDao = database.sessionDao(),
            nfcTagDao = database.nfcTagDao(),
            scheduleDao = database.scheduleDao(),
            scheduleProfileDao = database.scheduleProfileDao(),
            database = database,
        )

        latchRepository = LatchRepository(
            context = this,
            modeDao = database.modeDao(),
            latchDeviceDao = database.latchDeviceDao(),
            modeLatchDao = database.modeLatchDao(),
            autoLatchScheduleDao = database.autoLatchScheduleDao(),
            database = database,
        )
        LatchRuntime.install(latchRepository)

        scheduleManager = ScheduleManager(repository, AndroidScheduleEffects(this))
        autoLatchManager = AutoLatchManager(latchRepository, AndroidAutoLatchEffects(this))

        repository.onSessionEnded = { scheduleManager.evaluateAndRearm() }
        latchRepository.onAutoLatchSchedulesChanged = { autoLatchManager.rearm() }

        appScope.launch {
            scheduleManager.evaluateAndRearm()
            autoLatchManager.rearm()
        }
    }
}
